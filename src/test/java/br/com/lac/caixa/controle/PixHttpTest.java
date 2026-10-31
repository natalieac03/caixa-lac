package br.com.lac.caixa.controle;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.pix.FakeMercadoPago;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import br.com.lac.caixa.repositorio.VendaRepository;
import java.math.BigDecimal;
import java.util.UUID;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Pix automático na venda, com o Mercado Pago falso. */
@SpringBootTest(properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:pix;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class PixHttpTest {
    static final FakeMercadoPago FAKE = new FakeMercadoPago();
    static final MockWebServer SERVIDOR = FakeMercadoPago.iniciar(FAKE);

    @DynamicPropertySource
    static void mercadoPago(DynamicPropertyRegistry registro) {
        registro.add("mercadopago.url", () -> SERVIDOR.url("/").toString());
        registro.add("mercadopago.access-token", () -> FakeMercadoPago.TOKEN);
        registro.add("mercadopago.payer-email", () -> "caixa@lac.test");
        registro.add("mercadopago.webhook-secret", () -> "segredo-do-webhook");
    }

    @AfterAll
    static void parar() throws Exception {
        SERVIDOR.shutdown();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ProdutoRepository produtos;

    @Autowired
    VendaRepository vendas;

    @Autowired
    JdbcTemplate jdbc;

    final ObjectMapper json = new JsonMapper();

    Produto p;

    String token;

    @BeforeEach
    void preparar() {
        p = produtos.save(new Produto("Bóton Pix", new BigDecimal("6"), 100));
        token = UUID.randomUUID().toString();
    }

    MockHttpServletRequestBuilder logado(MockHttpServletRequestBuilder req) {
        return req.sessionAttr("autenticado", true).accept(MediaType.APPLICATION_JSON);
    }

    String gerar(int parte, String valor) throws Exception {
        String corpo = mvc.perform(logado(post("/venda/pix"))
                        .param("token", token)
                        .param("parte", String.valueOf(parte))
                        .param("valor", valor))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(corpo).path("orderId").asString();
    }

    MockHttpServletRequestBuilder venda(String... campos) {
        var req = post("/venda")
                .sessionAttr("autenticado", true)
                .param("token", token)
                .param("qtd_" + p.getId(), "3");
        for (int i = 0; i < campos.length; i += 2) req.param(campos[i], campos[i + 1]);
        return req;
    }

    @Test
    @DisplayName("Gerar Pix devolve QR e código; mesmo token + parte + valor reaproveita a cobrança")
    void gerarEReaproveitar() throws Exception {
        mvc.perform(logado(post("/venda/pix"))
                        .param("token", token)
                        .param("parte", "0")
                        .param("valor", "18,00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.valor").value(18.0))
                .andExpect(jsonPath("$.qrCode", containsString("00020126PIX-")))
                .andExpect(jsonPath("$.qrCodeBase64", containsString("iVBORw0KGgo")));
        int criacoes = FAKE.criacoes.get();
        String primeira = gerar(0, "18,00");

        assertEquals(criacoes, FAKE.criacoes.get());
        assertEquals(
                "PENDENTE",
                jdbc.queryForObject("select status from cobranca_pix where order_id = ?", String.class, primeira));
    }

    @Test
    @DisplayName("Valor mudou: a cobrança pendente é cancelada no Mercado Pago e outra é criada")
    void valorMudouCancelaEGeraOutra() throws Exception {
        String antiga = gerar(0, "18,00");

        String nova = gerar(0, "15,00");

        assertNotEquals(antiga, nova);
        assertEquals("canceled", FAKE.order(antiga).status);
        assertEquals(0, new BigDecimal("15.00").compareTo(FAKE.order(nova).valor));
        assertEquals(1, jdbc.queryForObject("select count(*) from cobranca_pix where token = ?", Integer.class, token));
    }

    @Test
    @DisplayName("Parte já paga não troca de valor: recusa com mensagem")
    void partePagaNaoTrocaValor() throws Exception {
        String order = gerar(0, "18,00");
        FAKE.pagar(order, "18.00");

        mvc.perform(logado(post("/venda/pix"))
                        .param("token", token)
                        .param("parte", "0")
                        .param("valor", "15,00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro", containsString("já foi paga")));
    }

    void passaramCincoSegundos() {
        jdbc.update("update cobranca_pix set consultada_em = null");
    }

    @Test
    @DisplayName("Consulta: o servidor pergunta ao Mercado Pago e só marca PAGA com o valor certo")
    void consultaMarcaPagaSoComValorCerto() throws Exception {
        String order = gerar(0, "18,00");
        String url = "/venda/pix/" + token + "/0";
        mvc.perform(logado(get(url))).andExpect(jsonPath("$.status").value("PENDENTE"));

        FAKE.pagar(order, "10.00");
        passaramCincoSegundos();
        mvc.perform(logado(get(url)))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.valorPago").value(10.0));

        FAKE.pagar(order, "18.00");
        passaramCincoSegundos();
        mvc.perform(logado(get(url))).andExpect(jsonPath("$.status").value("PAGA"));
        int consultas = FAKE.consultas.get();
        mvc.perform(logado(get(url))).andExpect(jsonPath("$.status").value("PAGA"));
        assertEquals(consultas, FAKE.consultas.get(), "depois de PAGA não precisa perguntar de novo");
    }

    @Test
    @DisplayName("Pix expirado no Mercado Pago aparece como EXPIRADA")
    void pixExpiradoNoMercadoPagoApareceComoExpirada() throws Exception {
        String order = gerar(0, "18,00");
        FAKE.expirar(order);

        mvc.perform(logado(get("/venda/pix/" + token + "/0")))
                .andExpect(jsonPath("$.status").value("EXPIRADA"));
    }

    @Test
    @DisplayName("Cancelar pela tela cancela no Mercado Pago")
    void cancelarPelaTela() throws Exception {
        String order = gerar(0, "18,00");

        mvc.perform(logado(post("/venda/pix/cancelar")).param("token", token).param("parte", "0"))
                .andExpect(jsonPath("$.status").value("CANCELADA"));
        assertEquals("canceled", FAKE.order(order).status);
    }

    @Test
    @DisplayName("Valor zero ou negativo é recusado; sem sessão, 401 em JSON")
    void valorZeroOuNegativoERecusadoESemSessaoDa401() throws Exception {
        mvc.perform(logado(post("/venda/pix"))
                        .param("token", token)
                        .param("parte", "0")
                        .param("valor", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("O valor do Pix deve ser maior que zero."));
        mvc.perform(logado(post("/venda/pix"))
                        .param("token", token)
                        .param("parte", "0")
                        .param("valor", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/venda/pix")
                        .accept(MediaType.APPLICATION_JSON)
                        .param("token", token)
                        .param("parte", "0")
                        .param("valor", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("Sessão expirada"));
    }

    @Test
    @DisplayName("Venda com Pix do Mercado Pago ainda não pago é recusada, sem baixar estoque")
    void vendaComPixNaoPagoRecusada() throws Exception {
        gerar(0, "18,00");

        mvc.perform(venda("pag_forma_0", "PIX", "pag_origem_0", "MERCADO_PAGO"))
                .andExpect(status().isConflict())
                .andExpect(model().attribute("erro", "O Pix de R$ 18,00 ainda não foi pago."));
        assertEquals(100, produtos.findById(p.getId()).orElseThrow().getQuantidade());
    }

    @Test
    @DisplayName("Venda com Pix do Mercado Pago pago é aceita e grava a origem e a order")
    void vendaComPixPagoAceita() throws Exception {
        String order = gerar(0, "18,00");
        FAKE.pagar(order, "18.00");

        mvc.perform(venda("pag_forma_0", "PIX", "pag_origem_0", "MERCADO_PAGO")).andExpect(redirectedUrl("/historico"));

        long id = vendas.findByToken(token).orElseThrow().getId();
        assertEquals(
                "MERCADO_PAGO",
                jdbc.queryForObject("select origem from pagamento_venda where venda_id = ?", String.class, id));
        assertEquals(
                order,
                jdbc.queryForObject("select mp_order_id from pagamento_venda where venda_id = ?", String.class, id));
    }

    @Test
    @DisplayName("Valor pago diferente do valor da parte é recusado")
    void valorPagoDiferenteRecusado() throws Exception {
        String order = gerar(0, "18,00");
        FAKE.pagar(order, "17.99");

        mvc.perform(venda("pag_forma_0", "PIX", "pag_origem_0", "MERCADO_PAGO"))
                .andExpect(model().attribute("erro", containsString("diferente do valor da cobrança")));
        mvc.perform(venda(
                        "pag_forma_0", "PIX", "pag_origem_0", "MERCADO_PAGO", "valorTotal", "15", "motivoAjuste", "x"))
                .andExpect(model().attribute("erro", containsString("diferente do valor da cobrança")));
    }

    @Test
    @DisplayName("Pix pago de R$ 18 não serve para uma venda de valor diferente")
    void cobrancaPagaComOutroTotal() throws Exception {
        String order = gerar(0, "18,00");
        FAKE.pagar(order, "18.00");

        mvc.perform(venda(
                        "pag_forma_0",
                        "PIX",
                        "pag_origem_0",
                        "MERCADO_PAGO",
                        "valorTotal",
                        "15",
                        "motivoAjuste",
                        "Desconto"))
                .andExpect(model().attribute("erro", containsString("não bate com o valor da parte")));
    }

    @Test
    @DisplayName("Pagamento dividido: parte Pix do Mercado Pago (parte estável 3) + dinheiro")
    void divididaComPixMercadoPago() throws Exception {
        String order = gerar(3, "10,00");
        FAKE.pagar(order, "10.00");

        mvc.perform(venda(
                        "pag_forma_0",
                        "DINHEIRO",
                        "pag_valor_0",
                        "8,00",
                        "pag_parte_0",
                        "1",
                        "pag_forma_1",
                        "PIX",
                        "pag_valor_1",
                        "10,00",
                        "pag_parte_1",
                        "3",
                        "pag_origem_1",
                        "MERCADO_PAGO"))
                .andExpect(redirectedUrl("/historico"));

        long id = vendas.findByToken(token).orElseThrow().getId();
        assertEquals(
                order,
                jdbc.queryForObject(
                        "select mp_order_id from pagamento_venda where venda_id = ? and forma = 'PIX'",
                        String.class,
                        id));
    }

    @Test
    @DisplayName("Pix manual continua como hoje e fica registrado como MANUAL")
    void pixManual() throws Exception {
        mvc.perform(venda("pag_forma_0", "PIX", "pag_origem_0", "MANUAL")).andExpect(redirectedUrl("/historico"));

        long id = vendas.findByToken(token).orElseThrow().getId();
        assertEquals(
                "MANUAL",
                jdbc.queryForObject("select origem from pagamento_venda where venda_id = ?", String.class, id));
    }

    @Test
    @DisplayName("Tela de venda sabe que o Pix automático está ligado")
    void telaComPixAutomatico() throws Exception {
        mvc.perform(get("/venda/nova").sessionAttr("autenticado", true))
                .andExpect(content().string(containsString("data-pix-automatico=\"true\"")));
    }
}
