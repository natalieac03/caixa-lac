package br.com.lac.caixa.pix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Cliente do Mercado Pago contra um servidor HTTP falso (nunca o Mercado Pago de verdade). */
@ExtendWith(OutputCaptureExtension.class)
class MercadoPagoPixTest {
    static final String TOKEN = "APP_USR-token-secreto-de-teste-0000";

    MockWebServer servidor;
    MercadoPagoPix mp;

    static MercadoPagoConfig config(String url, String token) {
        var c = new MercadoPagoConfig();
        c.setUrl(url);
        c.setAccessToken(token);
        c.setPayerEmail("caixa@lac.test");
        c.setTimeout(Duration.ofMillis(500));
        c.setEsperaMaximaRetry(Duration.ofMillis(200));
        return c;
    }

    static MockResponse json(int status, String corpo) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(corpo);
    }

    static String order(String status, String detalhe, String pago) {
        return """
                {"id":"ORD01TESTE","status":"%s","status_detail":"%s","external_reference":"ref-1",
                 "total_amount":"12.50","total_paid_amount":"%s",
                 "transactions":{"payments":[{"id":"PAY01","amount":"12.50","paid_amount":"%s",
                   "status":"%s","status_detail":"%s",
                   "payment_method":{"id":"pix","type":"bank_transfer","qr_code":"000201COPIAECOLA",
                     "qr_code_base64":"iVBORw0KGgo="}}]}}
                """.formatted(status, detalhe, pago, pago, status, detalhe);
    }

    @BeforeEach
    void subir() throws Exception {
        servidor = new MockWebServer();
        servidor.start();
        mp = new MercadoPagoPix(config(servidor.url("/").toString(), TOKEN));
    }

    @AfterEach
    void parar() throws Exception {
        servidor.shutdown();
    }

    @Test
    @DisplayName("Criar Pix: POST /v1/orders no formato da documentação, com idempotência e expiração")
    void criarPix() throws Exception {
        // Arrange
        servidor.enqueue(json(201, order("action_required", "waiting_transfer", "0.00")));

        // Act
        var cobranca = mp.criarPix(new BigDecimal("12.5"), "venda-abc-p0-1250");

        // Assert: resposta
        assertEquals("ORD01TESTE", cobranca.orderId());
        assertEquals("action_required", cobranca.status());
        assertEquals("waiting_transfer", cobranca.statusDetail());
        assertEquals("000201COPIAECOLA", cobranca.qrCode());
        assertEquals("iVBORw0KGgo=", cobranca.qrCodeBase64());
        // Assert: requisição
        var req = servidor.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/v1/orders", req.getPath());
        assertEquals("Bearer " + TOKEN, req.getHeader("Authorization"));
        assertEquals("venda-abc-p0-1250", req.getHeader("X-Idempotency-Key"));
        var corpo = new ObjectMapper().readTree(req.getBody().readUtf8());
        assertEquals("online", corpo.path("type").asText());
        assertEquals("automatic", corpo.path("processing_mode").asText());
        assertEquals("12.50", corpo.path("total_amount").asText());
        assertEquals("venda-abc-p0-1250", corpo.path("external_reference").asText());
        assertEquals("caixa@lac.test", corpo.path("payer").path("email").asText());
        assertTrue(
                corpo.path("payer").path("first_name").isMissingNode(), "sem MP_PAYER_FIRST_NAME, não vai first_name");
        var pagamento = corpo.path("transactions").path("payments").path(0);
        assertEquals("12.50", pagamento.path("amount").asText());
        assertEquals("PT30M", pagamento.path("expiration_time").asText());
        assertEquals("pix", pagamento.path("payment_method").path("id").asText());
        assertEquals(
                "bank_transfer", pagamento.path("payment_method").path("type").asText());
    }

    @Test
    @DisplayName("Consultar: paga (processed/accredited) com valor pago; pendente; expirada")
    void consultar() throws Exception {
        // Arrange
        servidor.enqueue(json(200, order("processed", "accredited", "12.50")));
        servidor.enqueue(json(200, order("action_required", "waiting_transfer", "0.00")));
        servidor.enqueue(json(200, order("canceled", "expired", "0.00")));

        // Act
        var paga = mp.consultar("ORD01TESTE");
        var pendente = mp.consultar("ORD01TESTE");
        var expirada = mp.consultar("ORD01TESTE");

        // Assert
        assertEquals("processed", paga.status());
        assertEquals("accredited", paga.statusDetail());
        assertEquals(0, new BigDecimal("12.50").compareTo(paga.valorPago()));
        assertEquals("ref-1", paga.externalReference());
        assertEquals("action_required", pendente.status());
        assertEquals("canceled", expirada.status());
        assertEquals("expired", expirada.statusDetail());
        var req = servidor.takeRequest();
        assertEquals("GET", req.getMethod());
        assertEquals("/v1/orders/ORD01TESTE", req.getPath());
    }

    @Test
    @DisplayName("Sem total_paid_amount, o valor pago é a soma dos paid_amount; sem nenhum, fica null")
    void valorPagoPelosPagamentos() {
        servidor.enqueue(json(200, """
                {"id":"O1","status":"processed","status_detail":"accredited",
                 "transactions":{"payments":[{"paid_amount":"5.00"},{"paid_amount":"7.50"}]}}"""));
        servidor.enqueue(json(200, "{\"id\":\"O2\",\"status\":\"processed\"}"));

        assertEquals(0, new BigDecimal("12.50").compareTo(mp.consultar("O1").valorPago()));
        assertNull(mp.consultar("O2").valorPago());
    }

    @Test
    @DisplayName("Cancelar: POST /v1/orders/{id}/cancel com X-Idempotency-Key; 409 traz o código do erro")
    void cancelar() throws Exception {
        servidor.enqueue(json(200, order("canceled", "canceled_transaction", "0.00")));
        servidor.enqueue(json(409, "{\"errors\":[{\"code\":\"cannot_cancel_order\",\"message\":\"x\"}]}"));

        var cancelada = mp.cancelar("ORD01TESTE");
        var erro = assertThrows(MercadoPagoException.class, () -> mp.cancelar("ORD01TESTE"));

        assertEquals("canceled", cancelada.status());
        var req = servidor.takeRequest();
        assertEquals("/v1/orders/ORD01TESTE/cancel", req.getPath());
        assertEquals("cancelar-ORD01TESTE", req.getHeader("X-Idempotency-Key"));
        assertEquals(409, erro.getStatus());
        assertEquals("cannot_cancel_order", erro.getCodigo());
    }

    @Test
    @DisplayName("429: espera o Retry-After e tenta uma vez mais")
    void tooManyRequestsUmaVez() {
        servidor.enqueue(json(429, "{}").setHeader("Retry-After", "0"));
        servidor.enqueue(json(200, order("processed", "accredited", "12.50")));

        var situacao = mp.consultar("ORD01TESTE");

        assertEquals("processed", situacao.status());
        assertEquals(2, servidor.getRequestCount());
    }

    @Test
    @DisplayName("429 duas vezes: desiste com MercadoPagoException (status 429), sem terceira tentativa")
    void tooManyRequestsDuasVezes() {
        servidor.enqueue(
                json(429, "{\"errors\":[{\"code\":\"too_many_requests\"}]}").setHeader("Retry-After", "60"));
        servidor.enqueue(
                json(429, "{\"errors\":[{\"code\":\"too_many_requests\"}]}").setHeader("Retry-After", "60"));

        long inicio = System.nanoTime();
        var erro = assertThrows(MercadoPagoException.class, () -> mp.consultar("ORD01TESTE"));
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        assertEquals(429, erro.getStatus());
        assertEquals(2, servidor.getRequestCount());
        assertTrue(ms < 2000, "a espera do Retry-After é limitada (levou " + ms + " ms)");
    }

    @Test
    @DisplayName("Timeout: sem resposta no prazo vira MercadoPagoException (status 0)")
    void timeout() {
        servidor.enqueue(json(200, order("processed", "accredited", "12.50")).setHeadersDelay(3, TimeUnit.SECONDS));

        var erro = assertThrows(MercadoPagoException.class, () -> mp.consultar("ORD01TESTE"));

        assertEquals(0, erro.getStatus());
        assertTrue(erro.getMessage().contains("sem resposta"));
    }

    @Test
    @DisplayName("Erros HTTP viram MercadoPagoException e o token nunca aparece no log nem na mensagem")
    void tokenNuncaNoLog(CapturedOutput log) {
        servidor.enqueue(json(401, "{\"message\":\"invalid token\"}"));
        servidor.enqueue(json(500, "{\"errors\":[{\"code\":\"internal_error\"}]}"));
        servidor.enqueue(json(200, "{}").setHeadersDelay(3, TimeUnit.SECONDS));

        var e401 = assertThrows(MercadoPagoException.class, () -> mp.criarPix(BigDecimal.TEN, "ref"));
        var e500 = assertThrows(MercadoPagoException.class, () -> mp.consultar("X"));
        var eTimeout = assertThrows(MercadoPagoException.class, () -> mp.cancelar("X"));

        assertEquals(401, e401.getStatus());
        assertEquals(500, e500.getStatus());
        assertEquals("internal_error", e500.getCodigo());
        for (var e : new MercadoPagoException[] {e401, e500, eTimeout}) {
            assertFalse(e.getMessage().contains(TOKEN));
        }
        assertTrue(log.getAll().contains("Mercado Pago: criar Pix falhou com HTTP 401"));
        assertFalse(log.getAll().contains(TOKEN), "o token apareceu no log");
        assertFalse(config("http://x", TOKEN).toString().contains(TOKEN));
    }

    @Test
    @DisplayName("Sem MP_ACCESS_TOKEN (ou sem e-mail do pagador) o recurso fica desligado e não chama nada")
    void desligadoSemToken() {
        var semToken = new MercadoPagoPix(config(servidor.url("/").toString(), ""));
        var semEmail = config(servidor.url("/").toString(), TOKEN);
        semEmail.setPayerEmail(" ");

        assertFalse(semToken.ligado());
        assertFalse(new MercadoPagoPix(semEmail).ligado());
        assertThrows(MercadoPagoException.class, () -> semToken.criarPix(BigDecimal.ONE, "r"));
        assertEquals(0, servidor.getRequestCount());
    }

    @Test
    @DisplayName("Com MP_PAYER_FIRST_NAME (ex.: APRO nos testes), vai payer.first_name")
    void primeiroNomeDoPagadorDeTeste() throws Exception {
        var c = config(servidor.url("/").toString(), TOKEN);
        c.setPayerFirstName("APRO");
        servidor.enqueue(json(201, order("action_required", "waiting_transfer", "0.00")));

        new MercadoPagoPix(c).criarPix(BigDecimal.TEN, "ref-apro");

        var corpo = new ObjectMapper().readTree(servidor.takeRequest().getBody().readUtf8());
        assertEquals("APRO", corpo.path("payer").path("first_name").asText());
    }

    @Test
    @DisplayName("Resposta que não é JSON (proxy, página de erro): vira MercadoPagoException, não RestClientException")
    void respostaQueNaoEJson() {
        servidor.enqueue(json(200, "<html>Bad gateway</html>"));
        var erro = assertThrows(MercadoPagoException.class, () -> mp.consultar("ORD01TESTE"));
        org.junit.jupiter.api.Assertions.assertTrue(erro.getMessage().contains("consultar order"), erro.getMessage());
    }
}
