package br.com.lac.caixa.servico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.repositorio.VendaRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Os avisos do sininho aparecem e somem nas condições certas. */
@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:alertas;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class AlertasTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    AlertasService alertas;

    @Autowired
    EstoqueService estoque;

    @Autowired
    CaixaService caixa;

    @Autowired
    EventoService eventos;

    @Autowired
    VendaRepository vendas;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void limpar() {
        for (String tabela : new String[] {
            "movimento_estoque",
            "compra_estoque",
            "pagamento_venda",
            "venda_desconto",
            "item_venda",
            "venda",
            "despesa",
            "evento",
            "produto"
        }) {
            jdbc.update("delete from " + tabela);
        }
    }

    static BigDecimal r(String v) {
        return new BigDecimal(v);
    }

    String inicio() throws Exception {
        return mvc.perform(get("/").sessionAttr("autenticado", true))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    Venda vender(Produto p, int qtd, String totalManual) {
        return caixa.registrarVenda(
                        Map.of(p.getId(), qtd),
                        List.of(PagamentoInformado.total(FormaPagamento.DINHEIRO)),
                        totalManual == null ? null : r(totalManual),
                        totalManual == null ? null : "Desconto de amigo",
                        UUID.randomUUID().toString())
                .venda();
    }

    @Test
    @DisplayName("Sem nada para avisar, o cartão não aparece")
    void semNada() throws Exception {
        estoque.criarNovo("Bóton Tranquilo", r("6"), 10, null, r("2"));
        assertFalse(alertas.calcular().isTemAlgo());
        assertTrue(inicio().contains("class=\"selo-avisos oculto\""), "sino sem selo");
    }

    @Test
    @DisplayName("Produto ativo sem custo: aparece e some ao informar o custo (ou arquivar)")
    void semCusto() throws Exception {
        var p = estoque.criarNovo("Caneca Sem Custo", r("30"), 5, null, null);
        String html = inicio();
        assertTrue(html.contains("id=\"alerta-sem-custo\"") && html.contains("Caneca Sem Custo"), html);

        estoque.editar(p.getId(), p.getNome(), r("30"), null, r("12"));
        assertTrue(inicio().contains("class=\"selo-avisos oculto\""), "sino sem selo");
    }

    @Test
    @DisplayName("Estoque para menos de 1 dia pela média do último evento; com limite, vale o limite")
    void estoqueAcabando() throws Exception {
        LocalDate ontem = LocalDate.now().minusDays(1);
        var feira = eventos.criar("Feira de ontem", ontem.atTime(9, 0), ontem.atTime(18, 0), null);
        var p = estoque.criarNovo("Bóton Concorrido", r("6"), 15, null, r("2"));
        Venda v = vender(p, 10, null);
        v.setDataHora(ontem.atTime(10, 0));
        vendas.saveAndFlush(v);

        var a = alertas.calcular();
        assertEquals(1, a.estoqueAcabando().size());
        assertTrue(
                a.estoqueAcabando().get(0).motivo().contains("10,00 un./dia em Feira de ontem"),
                a.estoqueAcabando().toString());
        assertTrue(inicio().contains("id=\"alerta-estoque\""));

        estoque.adicionarUnidades(p.getId(), 5);
        assertFalse(alertas.calcular().isTemAlgo());

        mvc.perform(post("/estoque/" + p.getId() + "/editar")
                        .sessionAttr("autenticado", true)
                        .param("nome", p.getNome())
                        .param("preco", "6,00")
                        .param("novaCategoria", "Bóton")
                        .param("estoqueMinimo", "12"))
                .andExpect(redirectedUrl("/estoque"));
        var comLimite = alertas.calcular();
        assertEquals("limite de 12 un.", comLimite.estoqueAcabando().get(0).motivo());
        assertEquals(10, comLimite.estoqueAcabando().get(0).estoque());

        mvc.perform(post("/estoque/" + p.getId() + "/editar")
                .sessionAttr("autenticado", true)
                .param("nome", p.getNome())
                .param("preco", "6,00")
                .param("novaCategoria", "Bóton"));
        assertEquals(12, estoque.buscar(p.getId()).getEstoqueMinimo());
        mvc.perform(post("/estoque/" + p.getId() + "/editar")
                .sessionAttr("autenticado", true)
                .param("nome", p.getNome())
                .param("preco", "6,00")
                .param("novaCategoria", "Bóton")
                .param("estoqueMinimo", ""));
        assertFalse(alertas.calcular().isTemAlgo());

        estoque.definirLimite(p.getId(), 3);
        assertFalse(alertas.calcular().isTemAlgo(), "com limite, vale o limite (10 > 3)");
        assertTrue(feira.getId() != null);
    }

    @Test
    @DisplayName("Venda de hoje com ajuste manual: aparece; cancelada ou de outro dia, não")
    void ajusteHoje() throws Exception {
        var p = estoque.criarNovo("Pôster Ajustado", r("15"), 20, null, r("5"));
        Venda v = vender(p, 1, "12");
        String html = inicio();
        assertTrue(html.contains("id=\"alerta-ajustes\"") && html.contains("Desconto de amigo"), html);
        assertTrue(html.contains("R$ 15,00 → R$ 12,00"), html);

        caixa.cancelarVenda(v.getId(), "Ensaio");
        assertFalse(alertas.calcular().isTemAlgo());

        Venda antiga = vender(p, 1, "10");
        antiga.setDataHora(LocalDateTime.now().minusDays(2));
        vendas.saveAndFlush(antiga);
        assertFalse(alertas.calcular().isTemAlgo(), "ajuste de outro dia não aparece");
    }

    @Test
    @DisplayName("Fechamento de hoje que não bate (soma das formas diferente do total): aparece e some ao corrigir")
    void fechamentoNaoBate() throws Exception {
        var p = estoque.criarNovo("Adesivo Conferido", r("8"), 20, null, r("3"));
        Venda v = vender(p, 1, null);
        assertFalse(alertas.calcular().isTemAlgo());

        jdbc.update("update pagamento_venda set valor = 7 where venda_id = ?", v.getId());
        String html = inicio();
        assertTrue(html.contains("id=\"alerta-fechamento\""), html);
        assertTrue(html.contains("R$ 7,00") && html.contains("R$ 8,00"), html);

        jdbc.update("update pagamento_venda set valor = 8 where venda_id = ?", v.getId());
        assertFalse(alertas.calcular().isTemAlgo());
    }
}
