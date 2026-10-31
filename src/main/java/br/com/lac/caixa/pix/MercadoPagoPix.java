package br.com.lac.caixa.pix;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Cliente do Pix pela Orders API do Mercado Pago: criar, consultar e cancelar. */
@Component
public class MercadoPagoPix {

    private static final Logger LOG = LoggerFactory.getLogger(MercadoPagoPix.class);

    public record Cobranca(String orderId, String status, String statusDetail, String qrCode, String qrCodeBase64) {}

    public record Situacao(
            String orderId, String status, String statusDetail, BigDecimal valorPago, String externalReference) {}

    private final MercadoPagoConfig config;
    private final RestClient http;
    private final ObjectMapper json = new JsonMapper();

    public MercadoPagoPix(MercadoPagoConfig config) {
        this.config = config;
        var cliente =
                HttpClient.newBuilder().connectTimeout(config.getTimeout()).build();
        var fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(config.getTimeout());
        this.http = RestClient.builder()
                .baseUrl(config.getUrl())
                .requestFactory(fabrica)
                .build();
    }

    public boolean ligado() {
        return config.isLigado();
    }

    public Cobranca criarPix(BigDecimal valor, String referencia) {
        exigirLigado();
        String quantia = valor.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        ObjectNode corpo = json.createObjectNode();
        corpo.put("type", "online");
        corpo.put("processing_mode", "automatic");
        corpo.put("total_amount", quantia);
        corpo.put("external_reference", referencia);
        ObjectNode pagador = corpo.putObject("payer").put("email", config.getPayerEmail());
        if (!config.getPayerFirstName().isBlank()) pagador.put("first_name", config.getPayerFirstName());
        ObjectNode pagamento =
                corpo.putObject("transactions").putArray("payments").addObject();
        pagamento.put("amount", quantia);
        pagamento.put("expiration_time", config.getExpiracao());
        pagamento.putObject("payment_method").put("id", "pix").put("type", "bank_transfer");

        JsonNode order = executar(
                "criar Pix",
                () -> http.post()
                        .uri("/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getAccessToken())
                        .header("X-Idempotency-Key", referencia)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(corpo)
                        .retrieve()
                        .body(JsonNode.class));
        JsonNode metodo = order.path("transactions").path("payments").path(0).path("payment_method");
        return new Cobranca(
                texto(order, "id"),
                texto(order, "status"),
                texto(order, "status_detail"),
                texto(metodo, "qr_code"),
                texto(metodo, "qr_code_base64"));
    }

    public Situacao consultar(String orderId) {
        exigirLigado();
        JsonNode order = executar(
                "consultar order",
                () -> http.get()
                        .uri("/v1/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getAccessToken())
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(JsonNode.class));
        return situacao(order);
    }

    public Situacao cancelar(String orderId) {
        exigirLigado();
        JsonNode order = executar(
                "cancelar order",
                () -> http.post()
                        .uri("/v1/orders/{id}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getAccessToken())
                        .header("X-Idempotency-Key", "cancelar-" + orderId)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(JsonNode.class));
        return situacao(order);
    }

    private Situacao situacao(JsonNode order) {
        return new Situacao(
                texto(order, "id"),
                texto(order, "status"),
                texto(order, "status_detail"),
                valorPago(order),
                texto(order, "external_reference"));
    }

    private static BigDecimal valorPago(JsonNode order) {
        BigDecimal total = decimal(order.path("total_paid_amount"));
        if (total != null) return total;
        BigDecimal soma = null;
        for (JsonNode p : order.path("transactions").path("payments")) {
            BigDecimal pago = decimal(p.path("paid_amount"));
            if (pago != null) soma = soma == null ? pago : soma.add(pago);
        }
        return soma;
    }

    private static BigDecimal decimal(JsonNode no) {
        if (no.isMissingNode() || no.isNull() || no.asString().isBlank()) return null;
        try {
            return new BigDecimal(no.asString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode v = no.path(campo);
        return v.isMissingNode() || v.isNull() ? null : v.asString();
    }

    private void exigirLigado() {
        if (!config.isLigado()) {
            throw new MercadoPagoException(
                    "Pix automático desligado (sem MP_ACCESS_TOKEN ou MP_PAYER_EMAIL).", 0, null, null);
        }
    }

    private JsonNode executar(String operacao, Supplier<JsonNode> chamada) {
        for (int tentativa = 1; ; tentativa++) {
            try {
                JsonNode resposta = chamada.get();
                return resposta == null ? json.createObjectNode() : resposta;
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value() && tentativa == 1) {
                    Duration espera = esperaDoRetryAfter(e.getResponseHeaders());
                    LOG.warn("Mercado Pago: {} recebeu 429; nova tentativa em {} ms", operacao, espera.toMillis());
                    dormir(espera);
                    continue;
                }
                String codigo = codigoDoErro(e.getResponseBodyAsString());
                String mensagem = "Mercado Pago: " + operacao + " falhou com HTTP "
                        + e.getStatusCode().value() + (codigo == null ? "" : " (" + codigo + ")");
                LOG.warn(mensagem);
                throw new MercadoPagoException(mensagem, e.getStatusCode().value(), codigo, null);
            } catch (ResourceAccessException e) {
                String mensagem = "Mercado Pago: " + operacao + " sem resposta (timeout ou rede)";
                LOG.warn(mensagem);
                throw new MercadoPagoException(mensagem, 0, null, null);
            } catch (RestClientException e) {
                String mensagem = "Mercado Pago: " + operacao + " devolveu uma resposta inválida";
                LOG.warn(mensagem);
                throw new MercadoPagoException(mensagem, 0, null, null);
            }
        }
    }

    private Duration esperaDoRetryAfter(HttpHeaders cabecalhos) {
        Duration espera = Duration.ofSeconds(1);
        String valor = cabecalhos == null ? null : cabecalhos.getFirst(HttpHeaders.RETRY_AFTER);
        if (valor != null) {
            try {
                espera = Duration.ofSeconds(Long.parseLong(valor.trim()));
            } catch (NumberFormatException retryAfterEmDataHttpUsaAEsperaPadrao) {
            }
        }
        return espera.compareTo(config.getEsperaMaximaRetry()) > 0 ? config.getEsperaMaximaRetry() : espera;
    }

    private static void dormir(Duration espera) {
        try {
            Thread.sleep(espera.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String codigoDoErro(String corpo) {
        try {
            JsonNode no = json.readTree(corpo);
            String codigo = texto(no.path("errors").path(0), "code");
            return codigo != null ? codigo : texto(no, "code");
        } catch (Exception e) {
            return null;
        }
    }
}
