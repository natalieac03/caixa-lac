package br.com.lac.caixa.servico;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lac.caixa.modelo.FormaPagamento;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Sininho: o JSON /avisos, ids estáveis e o sino em todas as telas. */
@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:avisos;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class AvisosHttpTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    AlertasService alertas;

    @Autowired
    EstoqueService estoque;

    @Autowired
    CaixaService caixa;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void limpar() {
        for (String t : new String[] {
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
            jdbc.update("delete from " + t);
        }
    }

    @Test
    @DisplayName("/avisos: 401 sem login; com login, total e seções com id, texto e link")
    void json() throws Exception {
        mvc.perform(get("/avisos").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        mvc.perform(get("/avisos").sessionAttr("autenticado", true).accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.secoes", hasSize(0)));

        var p = estoque.criarNovo("Caneca Sem Custo", new BigDecimal("30"), 5, null);
        caixa.registrarVenda(
                Map.of(p.getId(), 1),
                List.of(PagamentoInformado.total(FormaPagamento.PIX)),
                new BigDecimal("25"),
                "Amigo do grupo",
                UUID.randomUUID().toString());

        mvc.perform(get("/avisos").sessionAttr("autenticado", true).accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.secoes[0].id").value("alerta-sem-custo"))
                .andExpect(jsonPath("$.secoes[0].avisos[0].id").value("sem-custo-" + p.getId()))
                .andExpect(jsonPath("$.secoes[0].avisos[0].link").value("/estoque?semCusto=true"))
                .andExpect(jsonPath("$.secoes[1].id").value("alerta-ajustes"))
                .andExpect(jsonPath("$.secoes[1].avisos[0].texto")
                        .value(containsString("R$ 30,00 → R$ 25,00 (Amigo do grupo)")));
    }

    @Test
    @DisplayName("O id do aviso é estável e muda quando o conteúdo muda (produto + quantidade)")
    void idEstavel() {
        var p = estoque.criarNovo("Bóton Limite", new BigDecimal("6"), 5, null, new BigDecimal("2"));
        estoque.definirLimite(p.getId(), 5);
        String antes = alertas.calcular().secoes().get(0).avisos().get(0).id();
        assertEquals(antes, alertas.calcular().secoes().get(0).avisos().get(0).id(), "mesmo conteúdo, mesmo id");

        estoque.removerUnidades(p.getId(), 1);
        String depois = alertas.calcular().secoes().get(0).avisos().get(0).id();
        assertNotEquals(antes, depois);
        assertEquals("estoque-" + p.getId() + "-4", depois);
    }

    @Test
    @DisplayName("O sino está no topo de todas as telas logadas; a tela inicial não tem mais o cartão Atenção")
    void sinoEmTodasAsTelas() throws Exception {
        estoque.criarNovo("Pôster Sem Custo", new BigDecimal("12"), 5, null);
        for (String url : new String[] {
            "/",
            "/estoque",
            "/descontos",
            "/historico",
            "/fechamento",
            "/financas",
            "/eventos",
            "/despesas",
            "/taxas",
            "/caixa",
            "/venda/nova",
            "/eventos/comparativo"
        }) {
            mvc.perform(get(url).sessionAttr("autenticado", true))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("id=\"botao-sino\"")))
                    .andExpect(content().string(containsString("aria-controls=\"painel-avisos\"")))
                    .andExpect(content().string(containsString("1 aviso novo")))
                    .andExpect(content().string(containsString("Pôster Sem Custo")));
        }
        mvc.perform(get("/").sessionAttr("autenticado", true))
                .andExpect(content().string(not(containsString("cartao-atencao"))));
    }
}
