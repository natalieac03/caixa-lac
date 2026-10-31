package br.com.lac.caixa.servico;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import br.com.lac.caixa.RelogioDeTeste;
import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.OrigemPagamento;
import br.com.lac.caixa.modelo.PagamentoVenda;
import br.com.lac.caixa.modelo.TaxaPagamento;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.pix.FakeMercadoPago;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

/** Taxas por forma com vigência: gravadas na venda, arredondadas ao centavo, sem mexer no passado. */
@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:taxas;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class TaxaTest {
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
    TaxaService taxas;

    @Autowired
    CaixaService caixa;

    @Autowired
    EstoqueService estoque;

    @Autowired
    CobrancaPixService cobrancas;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    static BigDecimal r(String v) {
        return new BigDecimal(v);
    }

    LocalDate hoje() {
        return LocalDate.now(relogio);
    }

    void avancarDias(long dias) {
        ((RelogioDeTeste) relogio).avancar(dias * 24 * 3600);
    }

    Venda vender(BigDecimal preco, int qtd, List<PagamentoInformado> pagamentos) {
        var p = estoque.criarNovo("Produto Taxa " + System.nanoTime(), preco, 100, null);
        return caixa.registrarVenda(
                        Map.of(p.getId(), qtd),
                        pagamentos,
                        null,
                        null,
                        UUID.randomUUID().toString())
                .venda();
    }

    BigDecimal taxaGravada(Venda v, FormaPagamento forma) {
        return jdbc.queryForObject(
                "select taxa_estimada from pagamento_venda where venda_id = ? and forma = ?",
                BigDecimal.class,
                v.getId(),
                forma.name());
    }

    static void assertValor(String esperado, BigDecimal valor) {
        assertEquals(0, r(esperado).compareTo(valor), "esperado " + esperado + ", veio " + valor);
    }

    @Test
    @DisplayName("Arredondamento: percentual + fixo ao centavo, meio centavo para o par (HALF_EVEN)")
    void arredondamento() {
        var agora = LocalDateTime.of(2025, 1, 1, 0, 0);
        var credito = new TaxaPagamento(FormaPagamento.CREDITO, r("3.5"), r("0"), LocalDate.of(2025, 1, 1), agora);
        assertValor("0.21", credito.estimar(r("6.00"))); // 0,21 exato
        assertValor("0.04", credito.estimar(r("1.00"))); // 0,035 -> 0,04
        assertValor("0.03", credito.estimar(r("0.99"))); // 0,03465 -> 0,03
        var meio = new TaxaPagamento(FormaPagamento.CREDITO, r("2.5"), r("0"), LocalDate.of(2025, 1, 1), agora);
        assertValor("0.02", meio.estimar(r("1.00"))); // 0,025 -> 0,02 (o par), como o resto das finanças
        assertValor("0.04", meio.estimar(r("1.40"))); // 0,035 -> 0,04 (o par)
        var debito = new TaxaPagamento(FormaPagamento.DEBITO, r("1.99"), r("0.30"), LocalDate.of(2025, 1, 1), agora);
        assertValor("0.66", debito.estimar(r("18.00"))); // 0,3582 + 0,30 = 0,6582 -> 0,66
        assertValor("0.32", debito.estimar(r("1.00"))); // 0,0199 + 0,30 -> 0,32
        var pix = new TaxaPagamento(FormaPagamento.PIX, r("0.99"), r("0"), LocalDate.of(2025, 1, 1), agora);
        assertValor("0.05", pix.estimar(r("5.00"))); // 0,0495 -> 0,05
        assertValor("0.00", pix.estimar(r("0.50"))); // 0,00495 -> 0,00
        // Percentual fora de 0..100, negativo, com casas demais e fixo negativo são recusados.
        assertThrows(
                RegraDeNegocioException.class,
                () -> new TaxaPagamento(FormaPagamento.PIX, r("101"), r("0"), LocalDate.of(2025, 1, 1), agora));
        assertThrows(
                RegraDeNegocioException.class,
                () -> new TaxaPagamento(FormaPagamento.PIX, r("-1"), r("0"), LocalDate.of(2025, 1, 1), agora));
        assertThrows(
                RegraDeNegocioException.class,
                () -> new TaxaPagamento(FormaPagamento.PIX, r("1.23456"), r("0"), LocalDate.of(2025, 1, 1), agora));
        assertThrows(
                RegraDeNegocioException.class,
                () -> new TaxaPagamento(FormaPagamento.PIX, r("1"), r("-0.01"), LocalDate.of(2025, 1, 1), agora));
    }

    @Test
    @DisplayName("Vigência: venda antes da mudança guarda a taxa antiga, depois guarda a nova; passado é recusado")
    void vigencia() {
        // Arrange: crédito 3,5% hoje; muda para 4,99% + R$ 0,50 daqui a 2 dias
        taxas.definir(FormaPagamento.CREDITO, r("3.5"), null, hoje());
        taxas.definir(FormaPagamento.CREDITO, r("4.99"), r("0.50"), hoje().plusDays(2));
        assertThrows(
                RegraDeNegocioException.class,
                () -> taxas.definir(FormaPagamento.CREDITO, r("1"), null, hoje().minusDays(1)));

        // Act: uma venda hoje, outra amanhã (ainda a antiga) e outra depois da mudança
        Venda antes = vender(r("20"), 1, List.of(PagamentoInformado.total(FormaPagamento.CREDITO)));
        avancarDias(1);
        Venda vespera = vender(r("20"), 1, List.of(PagamentoInformado.total(FormaPagamento.CREDITO)));
        avancarDias(1);
        Venda depois = vender(r("20"), 1, List.of(PagamentoInformado.total(FormaPagamento.CREDITO)));

        // Assert
        assertValor("0.70", taxaGravada(antes, FormaPagamento.CREDITO));
        assertValor("0.70", taxaGravada(vespera, FormaPagamento.CREDITO));
        assertValor("1.50", taxaGravada(depois, FormaPagamento.CREDITO)); // 0,998 + 0,50 = 1,498 -> 1,50

        // Act: muda de novo (hoje); as vendas passadas não mudam
        taxas.definir(FormaPagamento.CREDITO, r("10"), null, hoje());
        assertValor("0.70", taxaGravada(antes, FormaPagamento.CREDITO));
        assertValor("1.50", taxaGravada(depois, FormaPagamento.CREDITO));
        // Mesma forma e data: substitui (não duplica).
        assertEquals(
                1,
                jdbc.queryForObject(
                        "select count(*) from taxa_pagamento where forma = 'CREDITO' and vigente_desde = ?",
                        Integer.class,
                        hoje()));
        assertValor(
                "10",
                taxas.vigente(FormaPagamento.CREDITO, hoje()).orElseThrow().getPercentual());
    }

    @Test
    @DisplayName(
            "Venda dividida: taxa por parte; forma sem taxa grava zero registrada; Pix do Mercado Pago usa a do Pix")
    void dividaESemTaxaEPixMercadoPago() {
        // Arrange
        taxas.definir(FormaPagamento.DEBITO, r("1.99"), r("0.30"), hoje());
        taxas.definir(FormaPagamento.PIX, r("0.99"), null, hoje());

        // Act: R$ 18 = débito 10 + dinheiro 8 (dinheiro sem taxa)
        Venda dividida = vender(
                r("6"),
                3,
                List.of(
                        new PagamentoInformado(FormaPagamento.DEBITO, r("10"), null, null),
                        new PagamentoInformado(FormaPagamento.DINHEIRO, r("8"), null, null)));
        // Act: Pix do Mercado Pago de R$ 12
        String token = UUID.randomUUID().toString();
        String order = cobrancas.gerar(token, 0, r("12")).getOrderId();
        FAKE.pagar(order, "12.00");
        var p = estoque.criarNovo("Produto Pix MP " + System.nanoTime(), r("6"), 10, null);
        Venda pixMp = caixa.registrarVenda(
                        Map.of(p.getId(), 2),
                        List.of(new PagamentoInformado(
                                FormaPagamento.PIX, null, null, null, OrigemPagamento.MERCADO_PAGO, 0)),
                        null,
                        null,
                        token)
                .venda();

        // Assert
        assertValor("0.50", taxaGravada(dividida, FormaPagamento.DEBITO)); // 0,199 + 0,30 = 0,499 -> 0,50
        assertValor("0.00", taxaGravada(dividida, FormaPagamento.DINHEIRO));
        assertTrue(jdbc.queryForObject(
                "select taxa_registrada from pagamento_venda where venda_id = ? and forma = 'DINHEIRO'",
                Boolean.class,
                dividida.getId()));
        assertNull(jdbc.queryForObject(
                "select taxa_id from pagamento_venda where venda_id = ? and forma = 'DINHEIRO'",
                Long.class,
                dividida.getId()));
        assertValor("0.12", taxaGravada(pixMp, FormaPagamento.PIX)); // 0,1188 -> 0,12
        assertEquals(
                "MERCADO_PAGO",
                jdbc.queryForObject(
                        "select origem from pagamento_venda where venda_id = ?", String.class, pixMp.getId()));
        PagamentoVenda parte = pixMp.getPagamentos().get(0);
        assertEquals(taxas.vigente(FormaPagamento.PIX, hoje()).orElseThrow().getId(), parte.getTaxaId());
    }

    @Test
    @DisplayName("Vendas antigas (antes das taxas): taxa zero e marcadas como sem taxa registrada")
    void vendasAntigas() throws Exception {
        // Arrange: uma parte como a V15 deixa as antigas
        Venda v = vender(r("6"), 1, List.of(PagamentoInformado.total(FormaPagamento.DINHEIRO)));
        jdbc.update(
                "update pagamento_venda set taxa_registrada = false, taxa_estimada = 0, taxa_id = null where venda_id = ?",
                v.getId());

        // Assert
        assertTrue(taxas.vendasSemTaxaRegistrada() >= 1);
        assertFalse(jdbc.queryForObject(
                "select taxa_registrada from pagamento_venda where venda_id = ?", Boolean.class, v.getId()));
        mvc.perform(get("/taxas").sessionAttr("autenticado", true))
                .andExpect(content().string(containsString("sem taxa registrada")));
    }

    @Test
    @DisplayName("Tela Taxas: salvar com vírgula, mensagem, vigentes e recusa de vigência no passado")
    void tela() throws Exception {
        mvc.perform(post("/taxas")
                        .sessionAttr("autenticado", true)
                        .param("forma", "OUTRO")
                        .param("percentual", "2,5")
                        .param("valorFixo", "0,10"))
                .andExpect(redirectedUrl("/taxas"))
                .andExpect(flash().attribute("sucesso", containsString("2,5% + R$ 0,10")));
        mvc.perform(post("/taxas")
                        .sessionAttr("autenticado", true)
                        .header("Referer", "/taxas")
                        .param("forma", "OUTRO")
                        .param("percentual", "1")
                        .param("vigenteDesde", hoje().minusDays(3).toString()))
                .andExpect(flash().attribute("erro", containsString("não pode ser no passado")));
        mvc.perform(get("/taxas").sessionAttr("autenticado", true))
                .andExpect(content().string(containsString("2,50%")));
    }
}
