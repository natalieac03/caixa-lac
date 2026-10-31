package br.com.lac.caixa.pix;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Mercado Pago falso para os testes, com as orders em memória. */
public class FakeMercadoPago extends Dispatcher {

    public static final String TOKEN = "APP_USR-token-do-fake-0000";

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

    private final ObjectMapper json = new JsonMapper();
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Map<String, String> porIdempotencia = new ConcurrentHashMap<>();
    private final AtomicInteger sequencia = new AtomicInteger();
    public final AtomicInteger criacoes = new AtomicInteger();
    public final AtomicInteger consultas = new AtomicInteger();
    public final AtomicInteger cancelamentos = new AtomicInteger();

    public volatile JsonNode ultimaCriacao;

    public volatile String ultimoCorpoCru;

    public static MockWebServer iniciar(FakeMercadoPago fake) {
        try {
            var servidor = new MockWebServer();
            servidor.setDispatcher(fake);
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public Order order(String id) {
        return orders.get(id);
    }

    public List<Order> ordersDoToken(String token) {
        return orders.values().stream()
                .filter(o -> o.referencia.startsWith(token))
                .toList();
    }

    public void pagar(String id, String valor) {
        Order o = orders.get(id);
        o.status = "processed";
        o.detalhe = "accredited";
        o.pago = new BigDecimal(valor);
    }

    public void processando(String id) {
        Order o = orders.get(id);
        o.status = "processing";
        o.detalhe = "in_process";
    }

    public void estornar(String id, String status) {
        Order o = orders.get(id);
        o.status = status;
        o.detalhe = status;
    }

    public volatile Runnable aoConsultar;

    public volatile Runnable aoCriar;

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
        String cru = req.getBody().readUtf8();
        ultimoCorpoCru = cru;
        var corpo = json.readTree(cru);
        ultimaCriacao = corpo;
        String existente = chave == null ? null : porIdempotencia.get(chave);
        if (existente != null) return resposta(201, corpo(orders.get(existente)));
        Runnable gancho = aoCriar;
        if (gancho != null) gancho.run();
        criacoes.incrementAndGet();
        String id = "ORD" + String.format("%04d", sequencia.incrementAndGet());
        Order o = new Order(
                id,
                new BigDecimal(corpo.path("total_amount").asString()),
                corpo.path("external_reference").asString());
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
