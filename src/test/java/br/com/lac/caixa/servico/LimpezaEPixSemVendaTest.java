package br.com.lac.caixa.servico;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import br.com.lac.caixa.RelogioDeTeste;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.OrigemPagamento;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.pix.FakeMercadoPago;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Limpeza das cobranças abandonadas e aviso de Pix recebido sem venda, com relógio controlado. */
@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:limpeza;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class LimpezaEPixSemVendaTest {
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
    LimpezaDePix limpeza;

    @Autowired
    CaixaService caixa;

    @Autowired
    ProdutoRepository produtos;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    void avancarMinutos(long minutos) {
        ((RelogioDeTeste) relogio).avancar(minutos * 60);
    }

    String situacao(String orderId) {
        return jdbc.queryForObject("select status from cobranca_pix where order_id = ?", String.class, orderId);
    }

    @Test
    @DisplayName("Limpeza: pendente há mais de 20 min é cancelada no Mercado Pago; paga ou recente fica")
    void limpezaDasAbandonadas() {
        // Arrange
        String naoPaga = cobrancas
                .gerar(UUID.randomUUID().toString(), 0, new BigDecimal("10"))
                .getOrderId();
        String pagaSemAviso = cobrancas
                .gerar(UUID.randomUUID().toString(), 0, new BigDecimal("12"))
                .getOrderId();
        FAKE.pagar(pagaSemAviso, "12.00");
        avancarMinutos(21);
        String recente = cobrancas
                .gerar(UUID.randomUUID().toString(), 0, new BigDecimal("14"))
                .getOrderId();

        // Act
        limpeza.limpar();

        // Assert
        assertEquals("CANCELADA", situacao(naoPaga));
        assertEquals("canceled", FAKE.order(naoPaga).status);
        assertEquals("PAGA", situacao(pagaSemAviso), "antes de cancelar, confere: estava paga");
        assertEquals("processed", FAKE.order(pagaSemAviso).status);
        assertEquals("PENDENTE", situacao(recente));
        assertEquals("action_required", FAKE.order(recente).status);
    }

    @Test
    @DisplayName(
            "Pix pago sem venda aparece no histórico depois de 10 min e some ao ser resolvido; o usado numa venda não aparece")
    void pixRecebidoSemVenda() throws Exception {
        // Arrange: um Pix pago sem venda e outro pago e usado numa venda
        String tokenSemVenda = UUID.randomUUID().toString();
        String semVenda =
                cobrancas.gerar(tokenSemVenda, 0, new BigDecimal("18")).getOrderId();
        FAKE.pagar(semVenda, "18.00");
        cobrancas.consultar(tokenSemVenda, 0);
        var p = produtos.save(new Produto("Bóton Limpeza", new BigDecimal("6"), 10));
        String tokenComVenda = UUID.randomUUID().toString();
        String comVenda =
                cobrancas.gerar(tokenComVenda, 0, new BigDecimal("18")).getOrderId();
        FAKE.pagar(comVenda, "18.00");
        caixa.registrarVenda(
                Map.of(p.getId(), 3),
                List.of(new PagamentoInformado(FormaPagamento.PIX, null, null, null, OrigemPagamento.MERCADO_PAGO, 0)),
                null,
                null,
                tokenComVenda);

        // Assert: dentro da carência, ninguém aparece (a tela pode estar confirmando)
        assertTrue(cobrancas.pixSemVenda().isEmpty());

        // Act
        avancarMinutos(11);

        // Assert: só o Pix sem venda, com valor, hora e order
        var avisos = cobrancas.pixSemVenda();
        assertEquals(List.of(semVenda), avisos.stream().map(c -> c.getOrderId()).toList());
        mvc.perform(get("/historico").sessionAttr("autenticado", true))
                .andExpect(content().string(containsString("Pix recebido sem venda")))
                .andExpect(content().string(containsString(semVenda)))
                .andExpect(content().string(containsString("R$ 18,00")))
                .andExpect(content().string(not(containsString(comVenda))));

        // Act: alguém resolve à mão
        mvc.perform(post("/historico/pix/" + avisos.get(0).getId() + "/resolver")
                        .sessionAttr("autenticado", true)
                        .param("como", "Venda lançada à mão"))
                .andExpect(redirectedUrl("/historico"));

        // Assert
        assertTrue(cobrancas.pixSemVenda().isEmpty());
        mvc.perform(get("/historico").sessionAttr("autenticado", true))
                .andExpect(content().string(not(containsString("Pix recebido sem venda"))));
    }
}
