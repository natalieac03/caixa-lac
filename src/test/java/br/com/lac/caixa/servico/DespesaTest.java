package br.com.lac.caixa.servico;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.CategoriaDespesa;
import br.com.lac.caixa.modelo.Despesa;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Despesas: categorias, valor, filtro por período e por evento, e telas. */
@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:despesas;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class DespesaTest {
    @Autowired
    DespesaService despesas;

    @Autowired
    EventoService eventos;

    @Autowired
    MockMvc mvc;

    static BigDecimal r(String v) {
        return new BigDecimal(v);
    }

    Long categoria(String nome) {
        return despesas.categorias().stream()
                .filter(c -> c.getNome().equals(nome))
                .map(CategoriaDespesa::getId)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("As seis categorias iniciais existem; criar, renomear e apagar (só sem despesas)")
    void categorias() {
        assertEquals(
                java.util.Set.of("Material", "Impressão", "Transporte", "Alimentação", "Taxa de espaço", "Outros"),
                new java.util.HashSet<>(despesas.categorias().stream()
                        .map(CategoriaDespesa::getNome)
                        .filter(n -> !n.startsWith("Teste"))
                        .toList()));

        var nova = despesas.criarCategoria("Teste Divulgação");
        assertThrows(RegraDeNegocioException.class, () -> despesas.criarCategoria("  teste   DIVULGAÇÃO "));
        despesas.renomearCategoria(nova.getId(), "Teste Divulgação online");
        assertThrows(RegraDeNegocioException.class, () -> despesas.renomearCategoria(nova.getId(), "material"));
        despesas.criar(LocalDate.of(2025, 1, 2), "Post", nova.getId(), r("10"), null, null);
        assertThrows(RegraDeNegocioException.class, () -> despesas.apagarCategoria(nova.getId()));
        var vazia = despesas.criarCategoria("Teste Vazia");
        despesas.apagarCategoria(vazia.getId());
    }

    @Test
    @DisplayName("Valor precisa ser maior que zero; data, descrição e categoria obrigatórias")
    void validacoes() {
        Long material = categoria("Material");
        LocalDate dia = LocalDate.of(2025, 1, 3);
        assertThrows(RegraDeNegocioException.class, () -> despesas.criar(dia, "Zero", material, r("0"), null, null));
        assertThrows(
                RegraDeNegocioException.class, () -> despesas.criar(dia, "Negativa", material, r("-1"), null, null));
        assertThrows(
                RegraDeNegocioException.class, () -> despesas.criar(dia, "Casas", material, r("1.234"), null, null));
        assertThrows(
                RegraDeNegocioException.class, () -> despesas.criar(null, "Sem data", material, r("1"), null, null));
        assertThrows(RegraDeNegocioException.class, () -> despesas.criar(dia, " ", material, r("1"), null, null));
        assertThrows(
                RegraDeNegocioException.class, () -> despesas.criar(dia, "Sem categoria", null, r("1"), null, null));
    }

    @Test
    @DisplayName("Despesa por evento: o filtro do evento soma as ligadas a ele, mesmo com data fora do horário")
    void despesaPorEvento() {
        var feira = eventos.criar(
                "Feira de maio", LocalDateTime.of(2025, 5, 10, 9, 0), LocalDateTime.of(2025, 5, 10, 18, 0), null);
        Long transporte = categoria("Transporte");
        Long espaco = categoria("Taxa de espaço");
        despesas.criar(
                LocalDate.of(2025, 5, 9), "Lona (véspera)", categoria("Material"), r("45.50"), feira.getId(), null);
        despesas.criar(LocalDate.of(2025, 5, 10), "Mesa", espaco, r("80"), feira.getId(), "estande 12");
        despesas.criar(LocalDate.of(2025, 5, 10), "Uber de outro assunto", transporte, r("23.90"), null, null);

        var doEvento = despesas.filtrar(new DespesaService.Filtro(feira.getId(), null, null));
        var doDia =
                despesas.filtrar(new DespesaService.Filtro(null, LocalDate.of(2025, 5, 10), LocalDate.of(2025, 5, 10)));

        assertEquals(
                java.util.List.of("Mesa", "Lona (véspera)"),
                doEvento.stream().map(Despesa::getDescricao).toList());
        assertEquals(0, r("125.50").compareTo(despesas.total(new DespesaService.Filtro(feira.getId(), null, null))));
        assertEquals(2, doDia.size());
        assertEquals(
                0,
                r("103.90")
                        .compareTo(despesas.total(new DespesaService.Filtro(
                                null, LocalDate.of(2025, 5, 10), LocalDate.of(2025, 5, 10)))));
        var f = new DespesaService.Filtro(feira.getId(), LocalDate.of(2030, 1, 1), null);
        assertEquals(2, despesas.filtrar(f).size());
    }

    @Test
    @DisplayName("Tela: aviso sobre compras, criar, editar, apagar e filtrar")
    void tela() throws Exception {
        Long alimentacao = categoria("Alimentação");
        mvc.perform(get("/despesas").sessionAttr("autenticado", true))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Compras de mercadoria para revender")));
        mvc.perform(post("/despesas")
                        .sessionAttr("autenticado", true)
                        .param("data", "2025-02-14")
                        .param("descricao", "Lanche da equipe")
                        .param("categoria", alimentacao.toString())
                        .param("valor", "37,80"))
                .andExpect(redirectedUrl("/despesas"))
                .andExpect(flash().attribute("sucesso", containsString("R$ 37,80")));
        mvc.perform(post("/despesas")
                        .sessionAttr("autenticado", true)
                        .header("Referer", "/despesas")
                        .param("data", "2025-02-14")
                        .param("descricao", "Zero")
                        .param("categoria", alimentacao.toString())
                        .param("valor", "0"))
                .andExpect(flash().attribute("erro", containsString("maior que zero")));
        var d = despesas.filtrar(new DespesaService.Filtro(null, LocalDate.of(2025, 2, 14), LocalDate.of(2025, 2, 14)))
                .get(0);
        mvc.perform(get("/despesas").param("editar", d.getId().toString()).sessionAttr("autenticado", true))
                .andExpect(content().string(containsString("Salvar despesa")));
        mvc.perform(post("/despesas/" + d.getId() + "/editar")
                        .sessionAttr("autenticado", true)
                        .param("data", "2025-02-14")
                        .param("descricao", "Lanche da equipe (pizza)")
                        .param("categoria", alimentacao.toString())
                        .param("valor", "41"))
                .andExpect(redirectedUrl("/despesas"));
        mvc.perform(get("/despesas")
                        .param("inicio", "2025-02-14")
                        .param("fim", "2025-02-14")
                        .sessionAttr("autenticado", true))
                .andExpect(content().string(containsString("Lanche da equipe (pizza)")))
                .andExpect(content().string(containsString("R$ 41,00")));
        mvc.perform(post("/despesas/" + d.getId() + "/apagar").sessionAttr("autenticado", true))
                .andExpect(redirectedUrl("/despesas"));
        mvc.perform(get("/despesas")
                        .param("inicio", "2025-02-14")
                        .param("fim", "2025-02-14")
                        .sessionAttr("autenticado", true))
                .andExpect(content().string(not(containsString("Lanche da equipe"))));
    }
}
