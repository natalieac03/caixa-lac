package br.com.lac.caixa.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lac.caixa.pix.FakeMercadoPago;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Webhook do Mercado Pago: assinatura, atualização da cobrança e idempotência. */
@SpringBootTest(
        properties = {
            "spring.profiles.active=test",
            "spring.datasource.url=jdbc:h2:mem:webhook;DB_CLOSE_DELAY=-1",
            "mercadopago.webhook-sincrono=true"
        })
@AutoConfigureMockMvc
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class WebhookHttpTest {
    static final String SEGREDO = "segredo-do-webhook-de-teste";
    static final FakeMercadoPago FAKE = new FakeMercadoPago();
    static final MockWebServer SERVIDOR = FakeMercadoPago.iniciar(FAKE);

    @DynamicPropertySource
    static void mercadoPago(DynamicPropertyRegistry registro) {
        registro.add("mercadopago.url", () -> SERVIDOR.url("/").toString());
        registro.add("mercadopago.access-token", () -> FakeMercadoPago.TOKEN);
        registro.add("mercadopago.payer-email", () -> "caixa@lac.test");
        registro.add("mercadopago.webhook-secret", () -> SEGREDO);
    }

    @AfterAll
    static void parar() throws Exception {
        SERVIDOR.shutdown();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    static String assinar(String segredo, String manifesto) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8)));
    }

    static MockHttpServletRequestBuilder aviso(String orderId, String requestId, long ts, String v1) {
        String corpo = "{\"action\":\"order.processed\",\"type\":\"order\",\"data\":{\"id\":\"" + orderId + "\"}}";
        return post("/webhooks/mercadopago")
                .param("data.id", orderId)
                .param("type", "order")
                .header("x-request-id", requestId)
                .header("x-signature", "ts=" + ts + ",v1=" + v1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo);
    }

    static MockHttpServletRequestBuilder avisoAssinado(String orderId, String requestId, long ts) throws Exception {
        String manifesto = "id:" + orderId.toLowerCase() + ";request-id:" + requestId + ";ts:" + ts + ";";
        return aviso(orderId, requestId, ts, assinar(SEGREDO, manifesto));
    }

    String gerarPix() throws Exception {
        String corpo = mvc.perform(post("/venda/pix")
                        .sessionAttr("autenticado", true)
                        .accept(MediaType.APPLICATION_JSON)
                        .param("token", UUID.randomUUID().toString())
                        .param("parte", "0")
                        .param("valor", "18,00"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new ObjectMapper().readTree(corpo).path("orderId").asText();
    }

    String situacao(String orderId) {
        return jdbc.queryForObject("select status from cobranca_pix where order_id = ?", String.class, orderId);
    }

    @Test
    @DisplayName("Assinatura válida: 200 sem login, consulta a order no Mercado Pago e marca PAGA")
    void assinaturaValida() throws Exception {
        String order = gerarPix();
        FAKE.pagar(order, "18.00");
        long agora = Instant.now().getEpochSecond();

        mvc.perform(avisoAssinado(order, UUID.randomUUID().toString(), agora)).andExpect(status().isOk());

        assertEquals("PAGA", situacao(order));
    }

    @Test
    @DisplayName("ts em milissegundos (como diz a documentação) também é aceito")
    void tsEmMilissegundos() throws Exception {
        String order = gerarPix();
        FAKE.pagar(order, "18.00");

        mvc.perform(avisoAssinado(order, "req-ms", Instant.now().toEpochMilli()))
                .andExpect(status().isOk());

        assertEquals("PAGA", situacao(order));
    }

    @Test
    @DisplayName("Assinatura inválida, ausente ou fora da tolerância: 401 sem corpo e nada muda")
    void assinaturaInvalida() throws Exception {
        String order = gerarPix();
        FAKE.pagar(order, "18.00");
        long agora = Instant.now().getEpochSecond();
        int consultas = FAKE.consultas.get();

        mvc.perform(aviso(order, "req-1", agora, "00" + "ab".repeat(31)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
        mvc.perform(post("/webhooks/mercadopago").param("data.id", order)).andExpect(status().isUnauthorized());
        String manifesto = "id:" + order.toLowerCase() + ";request-id:req-2;ts:" + agora + ";";
        mvc.perform(aviso(order, "req-2", agora, assinar("outro-segredo", manifesto)))
                .andExpect(status().isUnauthorized());
        mvc.perform(aviso("ORD9999", "req-3", agora, assinar(SEGREDO, manifesto)))
                .andExpect(status().isUnauthorized());
        mvc.perform(avisoAssinado(order, "req-4", agora - 3600)).andExpect(status().isUnauthorized());

        assertEquals("PENDENTE", situacao(order));
        assertEquals(consultas, FAKE.consultas.get(), "aviso recusado não consulta o Mercado Pago");
    }

    @Test
    @DisplayName("Sem data.id na URL: 400, mesmo assinado; o id do corpo (que não entra na assinatura) não vale")
    void exigeDataIdNaUrl() throws Exception {
        String order = gerarPix();
        FAKE.pagar(order, "18.00");
        long agora = Instant.now().getEpochSecond();
        String requestId = UUID.randomUUID().toString();
        String v1 = assinar(SEGREDO, "request-id:" + requestId + ";ts:" + agora + ";");
        mvc.perform(post("/webhooks/mercadopago")
                        .param("type", "order")
                        .header("x-request-id", requestId)
                        .header("x-signature", "ts=" + agora + ",v1=" + v1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"order\",\"data\":{\"id\":\"" + order + "\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        assertEquals("PENDENTE", situacao(order));
    }

    @Test
    @DisplayName("O mesmo aviso duas vezes não muda nada na segunda (idempotente)")
    void avisoRepetido() throws Exception {
        String order = gerarPix();
        FAKE.pagar(order, "18.00");
        long agora = Instant.now().getEpochSecond();
        mvc.perform(avisoAssinado(order, "req-repetido", agora)).andExpect(status().isOk());
        String pagaEm = jdbc.queryForObject(
                "select cast(paga_em as varchar) from cobranca_pix where order_id = ?", String.class, order);
        int consultas = FAKE.consultas.get();

        mvc.perform(avisoAssinado(order, "req-repetido", agora)).andExpect(status().isOk());

        assertEquals("PAGA", situacao(order));
        assertEquals(
                pagaEm,
                jdbc.queryForObject(
                        "select cast(paga_em as varchar) from cobranca_pix where order_id = ?", String.class, order));
        assertEquals(consultas, FAKE.consultas.get(), "já PAGA: não pergunta de novo ao Mercado Pago");
    }

    @Test
    @DisplayName("Aviso válido de order que não é nossa: 200 e nada acontece")
    void orderDesconhecida() throws Exception {
        mvc.perform(avisoAssinado("ORDNAOENOSSA", "req-x", Instant.now().getEpochSecond()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Aviso de pagamento com valor diferente: não vira PAGA, registra o valor pago")
    void valorDiferente() throws Exception {
        String order = gerarPix();
        FAKE.pagar(order, "1.00");

        mvc.perform(avisoAssinado(order, "req-div", Instant.now().getEpochSecond()))
                .andExpect(status().isOk());

        assertEquals("PENDENTE", situacao(order));
        assertEquals(
                0,
                new java.math.BigDecimal("1.00")
                        .compareTo(jdbc.queryForObject(
                                "select valor_pago from cobranca_pix where order_id = ?",
                                java.math.BigDecimal.class,
                                order)));
    }

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping rotas;

    @Test
    @DisplayName(
            "\"Simular notificação\" do painel: assinatura válida, order que não existe -> 200 e só um aviso no log")
    void simulacaoDoPainel(org.springframework.boot.test.system.CapturedOutput saida) throws Exception {
        long agora = Instant.now().getEpochSecond();
        int consultas = FAKE.consultas.get();
        String manifesto = "id:123456;request-id:req-simulado;ts:" + agora + ";";
        mvc.perform(post("/webhooks/mercadopago")
                        .param("data.id", "123456")
                        .param("type", "order")
                        .header("x-request-id", "req-simulado")
                        .header("x-signature", "ts=" + agora + ",v1=" + assinar(SEGREDO, manifesto))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"order.processed\",\"api_version\":\"v1\",\"type\":\"order\","
                                + "\"live_mode\":false,\"data\":{\"id\":\"123456\"}}"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
        String log = saida.getOut();
        org.junit.jupiter.api.Assertions.assertTrue(
                log.contains("Webhook: a order 123456 não é de nenhuma cobrança deste caixa; aviso ignorado."), log);
        org.junit.jupiter.api.Assertions.assertFalse(log.contains(" ERROR "), "sem erro no log");
        assertEquals(consultas, FAKE.consultas.get(), "order que não é nossa não é consultada no Mercado Pago");
    }

    @Test
    @DisplayName("Id com quebra de linha não forja linhas no log")
    void idNaoForjaLinhas(org.springframework.boot.test.system.CapturedOutput saida) throws Exception {
        mvc.perform(avisoAssinado("123\nINFO falso", "req-quebra", Instant.now().getEpochSecond()))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertTrue(
                saida.getOut().contains("a order 123_INFO falso não é"), saida.getOut());
        org.junit.jupiter.api.Assertions.assertFalse(saida.getOut().contains("\nINFO falso"));
    }

    @Test
    @DisplayName("O endpoint é exatamente POST /webhooks/mercadopago e fica fora do login; o resto pede login")
    void caminhoEForaDoLogin() throws Exception {
        var webhooks = rotas.getHandlerMethods().keySet().stream()
                .filter(r -> r.getPatternValues().stream().anyMatch(x -> x.contains("webhook")))
                .toList();
        assertEquals(1, webhooks.size(), webhooks.toString());
        assertEquals(java.util.Set.of("/webhooks/mercadopago"), webhooks.get(0).getPatternValues());
        assertEquals(
                java.util.Set.of(org.springframework.web.bind.annotation.RequestMethod.POST),
                webhooks.get(0).getMethodsCondition().getMethods());
        mvc.perform(post("/webhooks/mercadopago").param("data.id", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
        for (String outro : new String[] {"/webhooks/mercadopago/x", "/webhooks/outro", "/webhooks"}) {
            mvc.perform(post(outro))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", "/login"));
        }
    }
}
