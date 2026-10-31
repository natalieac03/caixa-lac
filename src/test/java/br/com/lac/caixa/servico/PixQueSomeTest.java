package br.com.lac.caixa.servico;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

import br.com.lac.caixa.RelogioDeTeste;
import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.CobrancaPix;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.OrigemPagamento;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.StatusCobranca;
import br.com.lac.caixa.pix.FakeMercadoPago;
import br.com.lac.caixa.repositorio.CobrancaPixRepository;
import br.com.lac.caixa.repositorio.VendaRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** O Pix pago não some: order em processamento, venda cancelada, token reusado e travas. */
@SpringBootTest(
        properties = {
            "spring.profiles.active=test",
            "spring.datasource.url=jdbc:h2:mem:pix_que_some;DB_CLOSE_DELAY=-1",
            "mercadopago.webhook-sincrono=true"
        })
@AutoConfigureMockMvc
class PixQueSomeTest {
    static final FakeMercadoPago FAKE = new FakeMercadoPago();
    static final MockWebServer SERVIDOR = FakeMercadoPago.iniciar(FAKE);

    @TestConfiguration
    static class Relogio {
        @Bean
        @Primary
        Clock relogioDeTeste() {
            return new RelogioDeTeste();
        }
    }

    @DynamicPropertySource
    static void mercadoPago(DynamicPropertyRegistry registro) {
        registro.add("mercadopago.url", () -> SERVIDOR.url("/").toString());
        registro.add("mercadopago.access-token", () -> FakeMercadoPago.TOKEN);
        registro.add("mercadopago.payer-email", () -> "caixa@lac.test");
    }

    @AfterAll
    static void parar() throws Exception {
        SERVIDOR.shutdown();
    }

    @Autowired
    Clock relogio;

    @Autowired
    CobrancaPixService cobrancas;

    @Autowired
    CaixaService caixa;

    @Autowired
    EstoqueService estoque;

    @Autowired
    DataSource dataSource;

    @Autowired
    MockMvc mvc;

    Produto boton;

    @BeforeEach
    void produto() {
        boton = estoque.criarNovo("Bóton Pix " + System.nanoTime(), new BigDecimal("6"), 100, null, null);
    }

    @AfterEach
    void semGancho() {
        FAKE.aoConsultar = null;
        FAKE.aoCriar = null;
    }

    @Autowired
    CobrancaPixRepository repositorio;

    /** Segura no Mercado Pago falso a primeira criação de order até o teste liberar. */
    CountDownLatch[] segurarPrimeiraCriacao() {
        var chegou = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var primeira = new AtomicBoolean(true);
        FAKE.aoCriar = () -> {
            if (!primeira.compareAndSet(true, false)) return;
            chegou.countDown();
            try {
                liberar.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        return new CountDownLatch[] {chegou, liberar};
    }

    void corrida(String token) throws Exception {
        var travas = segurarPrimeiraCriacao();
        var executor = Executors.newSingleThreadExecutor();
        try {
            Future<CobrancaPix> primeira = executor.submit(() -> cobrancas.gerar(token, 0, new BigDecimal("10")));
            assertTrue(travas[0].await(10, TimeUnit.SECONDS), "a primeira criação não chegou ao Mercado Pago");
            CobrancaPix segunda = cobrancas.gerar(token, 0, new BigDecimal("12"));
            travas[1].countDown();
            CobrancaPix daPrimeira = primeira.get(10, TimeUnit.SECONDS);

            CobrancaPix linha = repositorio.findByTokenAndParte(token, 0).orElseThrow();
            assertEquals(segunda.getOrderId(), linha.getOrderId(), "a linha tem a order que a tela mostrou");
            assertEquals(linha.getOrderId(), daPrimeira.getOrderId(), "as duas telas mostram a mesma order");
            assertEquals(StatusCobranca.PENDENTE, linha.getStatus());
            assertEquals(0, new BigDecimal("12").compareTo(linha.getValor()));
            var perdedora = FAKE.ordersDoToken(token).stream()
                    .filter(o -> o.valor.compareTo(new BigDecimal("10")) == 0)
                    .findFirst()
                    .orElseThrow();
            assertEquals("canceled", perdedora.status, "a order que perdeu foi cancelada no Mercado Pago");
        } finally {
            travas[1].countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName(
            "Dois gerar da mesma parte ao mesmo tempo, sem linha: a segunda linha bate na chave única e cancela a sua order")
    void corridaComLinhaNova() throws Exception {
        corrida(UUID.randomUUID().toString());
    }

    @Test
    @DisplayName("Dois gerar da mesma parte ao mesmo tempo, com o Pix anterior expirado: não sobrescreve a order nova")
    void corridaComLinhaExistente() throws Exception {
        String token = UUID.randomUUID().toString();
        FAKE.expirar(cobrancas.gerar(token, 0, new BigDecimal("8")).getOrderId());
        avancarSegundos(10);
        corrida(token);
    }

    void avancarSegundos(long s) {
        ((RelogioDeTeste) relogio).avancar(s);
    }

    @Test
    @DisplayName(
            "409 com a order em processamento: não marca cancelada, recusa trocar o valor e o webhook confirma o pago")
    void processandoNaoSome() {
        String token = UUID.randomUUID().toString();
        String order = cobrancas.gerar(token, 0, new BigDecimal("10")).getOrderId();
        FAKE.processando(order);

        assertEquals(StatusCobranca.PENDENTE, cobrancas.cancelar(token, 0).getStatus());
        avancarSegundos(6);
        var erro = assertThrows(RegraDeNegocioException.class, () -> cobrancas.gerar(token, 0, new BigDecimal("12")));
        assertEquals("O Pix anterior está em processamento; espere alguns segundos.", erro.getMessage());
        assertEquals(order, cobrancas.consultar(token, 0).getOrderId(), "a order continua a mesma");

        FAKE.pagar(order, "10.00");
        cobrancas.atualizarPorOrder(order);
        assertEquals(StatusCobranca.PAGA, cobrancas.consultar(token, 0).getStatus());
        avancarSegundos(11 * 60);
        assertTrue(cobrancas.pixSemVenda().stream().anyMatch(c -> c.getOrderId().equals(order)));
    }

    @Test
    @DisplayName("Gerar Pix com um token que já virou venda é recusado")
    void tokenQueJaVirouVenda() {
        String token = UUID.randomUUID().toString();
        caixa.registrarVenda(
                Map.of(boton.getId(), 1),
                List.of(new PagamentoInformado(FormaPagamento.PIX, null, null, null, OrigemPagamento.MANUAL, 0)),
                null,
                null,
                token);
        int criacoes = FAKE.criacoes.get();
        var erro = assertThrows(RegraDeNegocioException.class, () -> cobrancas.gerar(token, 0, new BigDecimal("6")));
        assertTrue(erro.getMessage().contains("já foi registrada"), erro.getMessage());
        assertEquals(criacoes, FAKE.criacoes.get(), "não cria order no Mercado Pago");
    }

    @Test
    @DisplayName(
            "Venda cancelada com Pix do Mercado Pago: aviso de estorno ao cancelar e o Pix volta para Pix sem venda")
    void vendaCanceladaParaEstorno() throws Exception {
        String token = UUID.randomUUID().toString();
        String order = cobrancas.gerar(token, 0, new BigDecimal("6")).getOrderId();
        FAKE.pagar(order, "6.00");
        var venda = caixa.registrarVenda(
                        Map.of(boton.getId(), 1),
                        List.of(new PagamentoInformado(
                                FormaPagamento.PIX, null, null, null, OrigemPagamento.MERCADO_PAGO, 0)),
                        null,
                        null,
                        token)
                .venda();
        avancarSegundos(11 * 60);
        assertFalse(
                cobrancas.pixSemVenda().stream().anyMatch(c -> c.getOrderId().equals(order)), "venda válida: não");

        mvc.perform(post("/historico/" + venda.getId() + "/cancelar")
                        .sessionAttr("autenticado", true)
                        .param("motivo", "Cliente desistiu"))
                .andExpect(flash().attribute("sucesso", containsString("estorne")));

        assertTrue(cobrancas.pixSemVenda().stream().anyMatch(c -> c.getOrderId().equals(order)));
        mvc.perform(get("/historico").sessionAttr("autenticado", true))
                .andExpect(content().string(containsString(order)))
                .andExpect(content().string(containsString("Venda cancelada: estorne o Pix")));
    }

    @Test
    @DisplayName("Consultar de novo em menos de 5 s devolve o salvo, sem perguntar ao Mercado Pago")
    void cacheDeCincoSegundos() {
        String token = UUID.randomUUID().toString();
        cobrancas.gerar(token, 0, new BigDecimal("6"));
        avancarSegundos(10);
        int antes = FAKE.consultas.get();
        cobrancas.consultar(token, 0);
        cobrancas.consultar(token, 0);
        avancarSegundos(4);
        cobrancas.consultar(token, 0);
        assertEquals(antes + 1, FAKE.consultas.get());
        avancarSegundos(2);
        cobrancas.consultar(token, 0);
        assertEquals(antes + 2, FAKE.consultas.get());
    }

    @Test
    @DisplayName("Enquanto o Mercado Pago responde, a linha da cobrança não fica travada no banco")
    void semTravaDuranteAConsulta() {
        String token = UUID.randomUUID().toString();
        long id = cobrancas.gerar(token, 0, new BigDecimal("6")).getId();
        avancarSegundos(10);
        AtomicReference<String> travada = new AtomicReference<>();
        FAKE.aoConsultar = () -> {
            try (var c = dataSource.getConnection();
                    var s = c.createStatement()) {
                c.setAutoCommit(false);
                s.execute("SET LOCK_TIMEOUT 300");
                s.executeQuery("SELECT id FROM cobranca_pix WHERE id = " + id + " FOR UPDATE")
                        .close();
                c.rollback();
            } catch (Exception e) {
                travada.set(e.getMessage());
            }
        };
        cobrancas.consultar(token, 0);
        assertEquals(null, travada.get(), "a consulta ao Mercado Pago segurava a trava da linha");
    }

    @Test
    @DisplayName("Pix estornado (refunded) no Mercado Pago: a cobrança vira ESTORNADA")
    void pixEstornadoNoMercadoPagoViraEstornada() {
        String token = UUID.randomUUID().toString();
        String order = cobrancas.gerar(token, 0, new BigDecimal("6")).getOrderId();
        FAKE.estornar(order, "refunded");
        avancarSegundos(10);
        assertEquals("ESTORNADA", cobrancas.consultar(token, 0).getStatus().name());
    }

    @Autowired
    AlertasService alertas;

    @Autowired
    VendaRepository vendas;

    @Test
    @DisplayName("Pix pago sem venda e depois estornado: sai sozinho de Pix recebido sem venda, resolvido")
    void pagoSemVendaEstornadoSaiDaLista() {
        String token = UUID.randomUUID().toString();
        String order = cobrancas.gerar(token, 0, new BigDecimal("7")).getOrderId();
        FAKE.pagar(order, "7.00");
        cobrancas.atualizarPorOrder(order);
        avancarSegundos(11 * 60);
        assertTrue(cobrancas.pixSemVenda().stream().anyMatch(c -> c.getOrderId().equals(order)));

        FAKE.estornar(order, "refunded");
        cobrancas.atualizarPorOrder(order);

        assertFalse(
                cobrancas.pixSemVenda().stream().anyMatch(c -> c.getOrderId().equals(order)));
        var linha = repositorio.findByTokenAndParte(token, 0).orElseThrow();
        assertEquals(StatusCobranca.ESTORNADA, linha.getStatus());
        assertEquals("estornado no Mercado Pago", linha.getResolucao());
    }

    @Test
    @DisplayName("Pix estornado de uma venda ativa: a venda continua e o sino avisa, com link para ela")
    void estornoDeVendaAtivaGeraAviso() {
        String token = UUID.randomUUID().toString();
        String order = cobrancas.gerar(token, 0, new BigDecimal("6")).getOrderId();
        FAKE.pagar(order, "6.00");
        var venda = caixa.registrarVenda(
                        Map.of(boton.getId(), 1),
                        List.of(new PagamentoInformado(
                                FormaPagamento.PIX, null, null, null, OrigemPagamento.MERCADO_PAGO, 0)),
                        null,
                        null,
                        token)
                .venda();

        FAKE.estornar(order, "charged_back");
        cobrancas.atualizarPorOrder(order);

        assertEquals(
                StatusCobranca.ESTORNADA,
                repositorio.findByTokenAndParte(token, 0).orElseThrow().getStatus());
        assertFalse(vendas.findById(venda.getId()).orElseThrow().isCancelada(), "a venda não é mexida");
        var aviso = alertas.calcular().secoes().stream()
                .filter(x -> x.id().equals("alerta-pix-estornado"))
                .flatMap(x -> x.avisos().stream())
                .filter(a -> a.id().equals("pix-estornado-" + order))
                .findFirst()
                .orElseThrow();
        assertTrue(aviso.link().endsWith("#venda-" + venda.getId()), aviso.link());
        assertTrue(aviso.texto().contains("#" + venda.getId()), aviso.texto());
    }
}
