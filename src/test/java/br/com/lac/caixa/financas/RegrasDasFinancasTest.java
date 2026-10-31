package br.com.lac.caixa.financas;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lac.caixa.RelogioDeTeste;
import br.com.lac.caixa.controle.FiltrosDaTelaFinancas;
import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.repositorio.FiltroFinancas;
import br.com.lac.caixa.repositorio.VendaRepository;
import br.com.lac.caixa.servico.CaixaService;
import br.com.lac.caixa.servico.CategoriaService;
import br.com.lac.caixa.servico.EstoqueService;
import br.com.lac.caixa.servico.EventoService;
import br.com.lac.caixa.servico.PagamentoInformado;
import br.com.lac.caixa.servico.TaxaService;
import br.com.lac.caixa.servico.financas.Financas.Granularidade;
import br.com.lac.caixa.servico.financas.Financas.LinhaProduto;
import br.com.lac.caixa.servico.financas.Financas.Metrica;
import br.com.lac.caixa.servico.financas.Financas.Ponto;
import br.com.lac.caixa.servico.financas.FinancasService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Regras das finanças: período máximo, série, recortes, CMV, taxas, fechamento e correção de data. */
@SpringBootTest(
        properties = {
            "spring.profiles.active=test",
            "spring.datasource.url=jdbc:h2:mem:regras_financas;DB_CLOSE_DELAY=-1"
        })
@AutoConfigureMockMvc
class RegrasDasFinancasTest {

    @TestConfiguration
    static class Relogio {
        @Bean
        @Primary
        Clock relogioDeTeste() {
            return new RelogioDeTeste();
        }
    }

    @Autowired
    Clock relogio;

    @Autowired
    FinancasService financas;

    @Autowired
    FiltrosDaTelaFinancas filtros;

    @Autowired
    CaixaService caixa;

    @Autowired
    EstoqueService estoque;

    @Autowired
    CategoriaService categorias;

    @Autowired
    EventoService eventos;

    @Autowired
    TaxaService taxas;

    @Autowired
    VendaRepository vendas;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    static BigDecimal r(String v) {
        return new BigDecimal(v);
    }

    static void assertValor(String esperado, BigDecimal valor) {
        assertEquals(r(esperado), valor, "esperado " + esperado + ", veio " + valor);
    }

    void agora(String quando) {
        ((RelogioDeTeste) relogio).definir(LocalDateTime.parse(quando));
    }

    Categoria categoria() {
        return categorias.obterOuCriar("Regra " + System.nanoTime());
    }

    Venda vender(String quando, Categoria categoria, String preco, String custo, List<PagamentoInformado> partes) {
        var p = estoque.criarNovo(
                "Produto " + System.nanoTime(), r(preco), 10, categoria, custo == null ? null : r(custo));
        var v = caixa.registrarVenda(
                        Map.of(p.getId(), 1),
                        partes,
                        null,
                        null,
                        UUID.randomUUID().toString())
                .venda();
        v.setDataHora(LocalDateTime.parse(quando));
        return vendas.saveAndFlush(v);
    }

    Venda vender(String quando, Categoria categoria, String preco) {
        return vender(quando, categoria, preco, null, List.of(PagamentoInformado.total(FormaPagamento.DINHEIRO)));
    }

    @Test
    @DisplayName("Período de 0001-01-01 a 9999-12-31: 400 rápido, com a mensagem dos 3 anos")
    void periodoEnorme() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            mvc.perform(get("/financas")
                            .sessionAttr("autenticado", true)
                            .param("atalho", "personalizado")
                            .param("inicio", "0001-01-01")
                            .param("fim", "9999-12-31"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("O período pode ter no máximo 3 anos")));
            mvc.perform(get("/financas/dados")
                            .sessionAttr("autenticado", true)
                            .accept(MediaType.APPLICATION_JSON)
                            .param("atalho", "personalizado")
                            .param("inicio", "0001-01-01")
                            .param("fim", "9999-12-31"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("3 anos")));
        });
    }

    @Test
    @DisplayName("Três anos cabem; três anos e um dia, não")
    void limiteDeTresAnos() throws Exception {
        mvc.perform(get("/financas")
                        .sessionAttr("autenticado", true)
                        .param("atalho", "personalizado")
                        .param("inicio", "2020-01-01")
                        .param("fim", "2022-12-31"))
                .andExpect(status().isOk());
        var erro = assertThrows(
                RegraDeNegocioException.class,
                () -> filtros.resolver("personalizado", "2020-01-01", "2023-01-01", null, null, null));
        assertTrue(erro.getMessage().contains("3 anos"), erro.getMessage());
    }

    @Test
    @DisplayName("Série por dia até 90 dias, por semana acima de 90 dias e por mês acima de 1 ano")
    void agrupamentoDaSerie() {
        var cat = categoria();
        vender("2024-02-07T10:00", cat, "6.00");
        vender("2024-02-09T10:00", cat, "4.00");
        vender("2024-05-20T10:00", cat, "5.00");

        var noventa = financas.serie(FiltroFinancas.periodo(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 30))
                .comCategoria(cat.getId()));
        assertEquals(Granularidade.DIA, noventa.granularidade());
        assertEquals(90, noventa.pontos().size());

        var semanas = financas.serie(FiltroFinancas.periodo(LocalDate.of(2024, 1, 3), LocalDate.of(2024, 6, 30))
                .comCategoria(cat.getId()));
        assertEquals(Granularidade.SEMANA, semanas.granularidade());
        assertFalse(semanas.porHora());
        assertEquals(
                LocalDate.of(2024, 1, 3).atStartOfDay(), semanas.pontos().get(0).inicio());
        assertEquals(
                LocalDate.of(2024, 1, 8).atStartOfDay(), semanas.pontos().get(1).inicio());
        Ponto semanaDe5 = semanas.pontos().stream()
                .filter(p -> p.inicio().toLocalDate().equals(LocalDate.of(2024, 2, 5)))
                .findFirst()
                .orElseThrow();
        assertValor("10.00", semanaDe5.receitaLiquida());
        assertEquals(2, semanaDe5.vendas());
        assertValor("15.00", soma(semanas.pontos()));

        var meses = financas.serie(FiltroFinancas.periodo(LocalDate.of(2023, 6, 15), LocalDate.of(2024, 12, 31))
                .comCategoria(cat.getId()));
        assertEquals(Granularidade.MES, meses.granularidade());
        assertEquals(
                LocalDate.of(2023, 6, 15).atStartOfDay(), meses.pontos().get(0).inicio());
        assertEquals(
                LocalDate.of(2023, 7, 1).atStartOfDay(), meses.pontos().get(1).inicio());
        assertEquals(19, meses.pontos().size());
        assertValor("10.00", meses.pontos().get(8).receitaLiquida());
        assertValor("15.00", soma(meses.pontos()));

        assertEquals(
                Granularidade.SEMANA,
                financas.serie(FiltroFinancas.periodo(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)))
                        .granularidade());
    }

    static BigDecimal soma(List<Ponto> pontos) {
        return pontos.stream().map(Ponto::receitaLiquida).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    @Test
    @DisplayName("Dias do período contam só até agora: janeiro inteiro visto no dia 10 tem 10 dias")
    void diasAteAgora() {
        var janeiro = new FiltroFinancas(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                null,
                null,
                null,
                false,
                LocalDateTime.of(2026, 1, 10, 12, 0));
        assertEquals(10, janeiro.dias());
        var passado = new FiltroFinancas(
                LocalDate.of(2025, 12, 1),
                LocalDate.of(2025, 12, 31),
                null,
                null,
                null,
                false,
                LocalDateTime.of(2026, 1, 10, 12, 0));
        assertEquals(31, passado.dias());
    }

    @Test
    @DisplayName("Hoje às 10h contra ontem: só as vendas de ontem até as 10h")
    void hojeContraOntemNoMesmoRecorte() {
        agora("2026-02-02T10:00");
        var cat = categoria();
        vender("2026-02-01T09:00", cat, "6.00");
        vender("2026-02-01T15:00", cat, "7.00");
        vender("2026-02-02T09:30", cat, "8.00");

        var hoje = filtros.resolver("hoje", null, null, null, cat.getId(), null);
        var comparacao = financas.comparar(hoje.filtro());
        assertValor("8.00", comparacao.atual().receitaLiquida());
        assertValor("6.00", comparacao.anterior().receitaLiquida());
        assertEquals("01/02/2026 até 10:00", comparacao.periodoAnterior());
    }

    @Test
    @DisplayName("CMV arredondado por item no banco: o resumo soma os mesmos centavos do ranking")
    void cmvPorItem() {
        var cat = categoria();
        var dinheiro = List.of(PagamentoInformado.total(FormaPagamento.DINHEIRO));
        vender("2024-03-03T10:00", cat, "1.00", "0.0050", dinheiro);
        vender("2024-03-03T11:00", cat, "1.00", "0.0050", dinheiro);
        var f = FiltroFinancas.periodo(LocalDate.of(2024, 3, 3), LocalDate.of(2024, 3, 3))
                .comCategoria(cat.getId());
        BigDecimal ranking = financas.rankingProdutos(f, Metrica.RECEITA).stream()
                .map(LinhaProduto::cmv)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        assertValor("0.02", ranking);
        assertEquals(ranking, financas.resumo(f).cmv());
        assertEquals(
                ranking, financas.rankingCategorias(f, Metrica.RECEITA).get(0).cmv());
    }

    @Test
    @DisplayName("Filtro de forma: a taxa do resumo é só a das partes nessa forma")
    void taxasDaForma() {
        agora("2026-04-01T09:00");
        taxas.definir(FormaPagamento.PIX, r("1"), null, LocalDate.of(2026, 4, 1));
        taxas.definir(FormaPagamento.CREDITO, r("10"), null, LocalDate.of(2026, 4, 1));
        vender(
                "2026-04-01T09:00",
                null,
                "10.00",
                null,
                List.of(
                        new PagamentoInformado(FormaPagamento.PIX, r("4"), null, null),
                        new PagamentoInformado(FormaPagamento.CREDITO, r("6"), null, null)));
        var dia = FiltroFinancas.periodo(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 1));
        assertValor("0.64", financas.resumo(dia).taxas());
        assertValor("0.04", financas.resumo(dia.comForma(FormaPagamento.PIX)).taxas());
        assertValor(
                "0.60", financas.resumo(dia.comForma(FormaPagamento.CREDITO)).taxas());
    }

    @Test
    @DisplayName("CSV do fechamento de evento que acaba à meia-noite: a data final é o dia do evento")
    void fechamentoAteMeiaNoite() throws Exception {
        var ev = eventos.criar(
                "Noite " + System.nanoTime(),
                LocalDateTime.of(2024, 6, 10, 18, 0),
                LocalDateTime.of(2024, 6, 11, 0, 0),
                null);
        String csv = mvc.perform(get("/fechamento/exportar.csv")
                        .sessionAttr("autenticado", true)
                        .param("evento", ev.getId().toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(csv.contains("10/06/2024"), csv);
        assertFalse(csv.contains("11/06/2024"), csv);
    }

    BigDecimal taxaGravada(Venda v) {
        return jdbc.queryForObject(
                "select taxa_estimada from pagamento_venda where venda_id = ?", BigDecimal.class, v.getId());
    }

    @Test
    @DisplayName("Corrigir a data: recusa o futuro e a venda cancelada, e recalcula a taxa pela nova data")
    void corrigirData() {
        agora("2026-03-01T09:00");
        taxas.definir(FormaPagamento.DEBITO, r("1"), null, LocalDate.of(2026, 3, 1));
        agora("2026-03-20T09:00");
        taxas.definir(FormaPagamento.DEBITO, r("2"), null, LocalDate.of(2026, 3, 20));
        var debito = List.of(PagamentoInformado.total(FormaPagamento.DEBITO));
        var v = vender("2026-03-20T09:00", null, "6.00", null, debito);
        assertValor("0.12", taxaGravada(v));

        caixa.alterarDataHora(v.getId(), LocalDateTime.of(2026, 3, 5, 10, 0));
        assertValor("0.06", taxaGravada(v));

        var futuro = assertThrows(
                RegraDeNegocioException.class,
                () -> caixa.alterarDataHora(v.getId(), LocalDateTime.of(2026, 3, 20, 9, 1)));
        assertTrue(futuro.getMessage().contains("futuro"), futuro.getMessage());

        var cancelada = vender("2026-03-20T08:00", null, "6.00", null, debito);
        caixa.cancelarVenda(cancelada.getId(), "Teste");
        var erro = assertThrows(
                RegraDeNegocioException.class,
                () -> caixa.alterarDataHora(cancelada.getId(), LocalDateTime.of(2026, 3, 19, 10, 0)));
        assertTrue(erro.getMessage().contains("cancelada"), erro.getMessage());
    }
}
