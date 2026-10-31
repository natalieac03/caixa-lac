package br.com.lac.caixa.controle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.servico.CaixaService;
import br.com.lac.caixa.servico.EstoqueService;
import br.com.lac.caixa.servico.PagamentoInformado;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Os CSVs não deixam texto do usuário virar fórmula na planilha. */
@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:csv_injecao;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class CsvInjecaoHttpTest {
    static final String FORMULA = "=HYPERLINK(\"http://malicioso.example\",\"Clique\")";
    static final String NO_CSV = "\"'=HYPERLINK(\"\"http://malicioso.example\"\",\"\"Clique\"\")\"";

    @Autowired
    MockMvc mvc;

    @Autowired
    EstoqueService estoque;

    @Autowired
    CaixaService caixa;

    @BeforeEach
    void produtoComFormula() {
        var p = estoque.criarNovo(FORMULA + " " + System.nanoTime(), new BigDecimal("6"), 10, null, null);
        caixa.registrarVenda(
                Map.of(p.getId(), 1),
                List.of(new PagamentoInformado(FormaPagamento.OUTRO, null, "@SUM(1+1)", null, null, 0)),
                null,
                null,
                UUID.randomUUID().toString());
    }

    String csv(String url) throws Exception {
        byte[] corpo = mvc.perform(get(url).sessionAttr("autenticado", true))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        return new String(corpo, StandardCharsets.UTF_8);
    }

    static void semFormula(String texto) {
        assertFalse(texto.contains(";\"=") || texto.contains(";\"@") || texto.startsWith("\"="), texto);
    }

    @Test
    @DisplayName("Estoque, histórico e finanças: =HYPERLINK e @SUM saem com apóstrofo")
    void apostrofoNaFrente() throws Exception {
        String estoqueCsv = csv("/estoque/exportar.csv");
        assertTrue(estoqueCsv.contains(NO_CSV.substring(0, NO_CSV.length() - 1)), estoqueCsv);
        semFormula(estoqueCsv);

        String historico = csv("/historico/exportar.csv");
        assertTrue(historico.contains("'=HYPERLINK"), historico);
        assertTrue(historico.contains("'@SUM(1+1)"), historico);
        semFormula(historico);

        String produtos = csv("/financas/exportar/produtos.csv");
        assertTrue(produtos.contains("'=HYPERLINK"), produtos);
        semFormula(produtos);
    }
}
