package br.com.lac.caixa.pix;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Mercado Pago de mentira para os testes (MockWebServer): guarda as orders em
 * memória e responde POST/GET /v1/orders e o cancelamento no formato da Orders
 * API. Os testes mudam a situação com pagar/expirar.
 */
public class FakeMercadoPago extends Dispatcher {

    public static final String TOKEN = "APP_USR-token-do-fake-0000";

    /** Uma order: status, detalhe, valor e valor pago (null = nada pago ainda). */
    public static final class Order {
        public final String id;
        public final BigDecimal valor;
        public final String referencia;
        public volatile String status = "action_required";
        public volatile String detalhe = "waiting_transfer";
        public volatile BigDecimal pago;

        Order(String id, BigDecimal valor, String referencia) {
            this.id = id;
            this.valor = valor;
            this.referencia = referencia;
        }
    }

    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Map<String, String> porIdempotencia = new ConcurrentHashMap<>();
    private final AtomicInteger sequencia = new AtomicInteger();
    public final AtomicInteger criacoes = new AtomicInteger();
    public final AtomicInteger consultas = new AtomicInteger();
    public final AtomicInteger cancelamentos = new AtomicInteger();

    /** O corpo JSON da última criação de order (o MockWebServer só deixa ler o corpo uma vez). */
    public volatile com.fasterxml.jackson.databind.JsonNode ultimaCriacao;

    public static MockWebServer iniciar(FakeMercadoPago fake) {
        try {
            var servidor = new MockWebServer();
            servidor.setDispatcher(fake);
            servidor.start();
            return servidor;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public Order order(String id) {
        return orders.get(id);
    }

    public void pagar(String id, String valor) {
        Order o = orders.get(id);
        o.status = "processed";
        o.detalhe = "accredited";
        o.pago = new BigDecimal(valor);
    }

    /** Pago no banco, mas o Mercado Pago ainda está processando (cancelar responde 409). */
    public void processando(String id) {
        Order o = orders.get(id);
        o.status = "processing";
        o.detalhe = "in_process";
    }

    /** Pago e devolvido: refunded (estorno) ou charged_back (contestação). */
    public void estornar(String id, String status) {
        Order o = orders.get(id);
        o.status = status;
        o.detalhe = status;
    }

    /** Roda dentro do atendimento de cada consulta (GET /v1/orders/{id}), antes de responder. */
    public volatile Runnable aoConsultar;

    public void expirar(String id) {
        Order o = orders.get(id);
        o.status = "canceled";
        o.detalhe = "expired";
    }

    @Override
    public MockResponse dispatch(RecordedRequest req) {
        if (!("Bearer " + TOKEN).equals(req.getHeader("Authorization")))
            return resposta(401, "{\"message\":\"unauthorized\"}");
        String caminho = req.getPath();
        try {
            if ("POST".equals(req.getMethod()) && "/v1/orders".equals(caminho)) return criar(req);
            if ("GET".equals(req.getMethod()) && caminho.startsWith("/v1/orders/")) {
                consultas.incrementAndGet();
                Runnable gancho = aoConsultar;
                if (gancho != null) gancho.run();
                Order o = orders.get(caminho.substring("/v1/orders/".length()));
                return o == null
                        ? resposta(404, "{\"errors\":[{\"code\":\"order_not_found\"}]}")
                        : resposta(200, corpo(o));
            }
            if ("POST".equals(req.getMethod()) && caminho.endsWith("/cancel")) {
                String id = caminho.substring("/v1/orders/".length(), caminho.length() - "/cancel".length());
                Order o = orders.get(id);
                if (o == null) return resposta(404, "{\"errors\":[{\"code\":\"order_not_found\"}]}");
                if (!"action_required".equals(o.status) && !"created".equals(o.status)) {
                    return resposta(409, "{\"errors\":[{\"code\":\"cannot_cancel_order\"}]}");
                }
                cancelamentos.incrementAndGet();
                o.status = "canceled";
                o.detalhe = "canceled_transaction";
                return resposta(200, corpo(o));
            }
        } catch (Exception e) {
            return resposta(500, "{\"errors\":[{\"code\":\"internal_error\"}]}");
        }
        return resposta(404, "{}");
    }

    private MockResponse criar(RecordedRequest req) throws Exception {
        String chave = req.getHeader("X-Idempotency-Key");
        var corpo = json.readTree(req.getBody().readUtf8());
        ultimaCriacao = corpo;
        String existente = chave == null ? null : porIdempotencia.get(chave);
        if (existente != null) return resposta(201, corpo(orders.get(existente)));
        criacoes.incrementAndGet();
        String id = "ORD" + String.format("%04d", sequencia.incrementAndGet());
        Order o = new Order(
                id,
                new BigDecimal(corpo.path("total_amount").asText()),
                corpo.path("external_reference").asText());
        orders.put(id, o);
        if (chave != null) porIdempotencia.put(chave, id);
        return resposta(201, corpo(o));
    }

    private String corpo(Order o) throws Exception {
        var no = json.createObjectNode();
        no.put("id", o.id);
        no.put("status", o.status);
        no.put("status_detail", o.detalhe);
        no.put("external_reference", o.referencia);
        no.put("total_amount", o.valor.toPlainString());
        if (o.pago != null) no.put("total_paid_amount", o.pago.toPlainString());
        var pagamento = no.putObject("transactions").putArray("payments").addObject();
        pagamento.put("id", "PAY-" + o.id);
        pagamento.put("amount", o.valor.toPlainString());
        pagamento.put("status", o.status);
        pagamento.put("status_detail", o.detalhe);
        pagamento
                .putObject("payment_method")
                .put("id", "pix")
                .put("type", "bank_transfer")
                .put("qr_code", "00020126PIX-" + o.id)
                .put("qr_code_base64", "iVBORw0KGgo" + o.id);
        return json.writeValueAsString(no);
    }

    private static MockResponse resposta(int status, String corpo) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(corpo);
    }
}
