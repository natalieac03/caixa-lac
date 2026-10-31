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
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.OrigemPagamento;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.StatusCobranca;
import br.com.lac.caixa.pix.FakeMercadoPago;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
}
