package br.com.lac.caixa.financas;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lac.caixa.controle.csv.ExportadorFinancas;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Tela de Finanças e o JSON dos gráficos: login, filtros, período vazio e CSVs. */
@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:financas_http;DB_CLOSE_DELAY=-1"
        })
@AutoConfigureMockMvc
class FinancasHttpTest {
    static final String MARCO = "?atalho=personalizado&inicio=2025-03-01&fim=2025-03-15";

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationContext contexto;

    MassaDeTeste massa;

    @BeforeEach
    void montar() {
        massa = new MassaDeTeste(contexto).montar();
    }

    static MockHttpServletRequestBuilder logado(String url) {
        return get(url).sessionAttr("autenticado", true);
    }

    @Test
    @DisplayName("Sem login: a tela vai para o login e o JSON responde 401")
    void exigeLogin() throws Exception {
        mvc.perform(get("/financas")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/financas/dados").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        mvc.perform(get("/financas/exportar/demonstrativo.csv")).andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Período personalizado: cartões, demonstrativo, rankings e botões de CSV com o mesmo filtro")
    void periodoPersonalizado() throws Exception {
        mvc.perform(logado("/financas" + MARCO))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("01/03/2025 a 15/03/2025")))
                .andExpect(content().string(containsString("R$ 229,00")))
                .andExpect(content().string(containsString("R$ 26,42")))
                .andExpect(content().string(containsString("63,54%")))
                .andExpect(content().string(containsString("id=\"aviso-parcial\"")))
                .andExpect(content().string(containsString("/estoque?semCusto=true")))
                .andExpect(content().string(containsString("Promoções apagadas")))
                .andExpect(content().string(containsString("Arquivado")))
                .andExpect(
                        content()
                                .string(
                                        containsString(
                                                "/financas/exportar/produtos.csv?atalho=personalizado&amp;inicio=2025-03-01&amp;fim=2025-03-15")))
                .andExpect(content().string(containsString("id=\"tabela-serie-titulo\"")))
                .andExpect(content().string(not(containsString("Nenhuma venda neste período"))));
    }

    @Test
    @DisplayName("Evento, categoria, forma e métrica nos filtros da URL")
    void eventoCategoriaFormaNaUrlEAtalhoInvalidoDa400() throws Exception {
        long feira1 = massa.e.get("Feira 1").getId();
        mvc.perform(logado("/financas?evento=" + feira1))
                .andExpect(content().string(containsString("Feira 1 (01/03/2025 09:00 a 01/03/2025 18:00)")))
                .andExpect(content().string(containsString("R$ 135,00")))
                .andExpect(content().string(containsString("Sem período anterior para comparar")));
        mvc.perform(logado("/financas" + MARCO + "&categoria="
                        + massa.c.get("Caneca").getId()))
                .andExpect(content().string(containsString("R$ 81,67")))
                .andExpect(content().string(containsString("Não se aplica com filtro de categoria ou de forma.")))
                .andExpect(content().string(containsString("Descontos e ajustes rateados")));
        mvc.perform(logado("/financas" + MARCO + "&forma=PIX"))
                .andExpect(content().string(containsString("R$ 67,00")));
        for (String atalho : new String[] {"hoje", "evento", "7dias", "mes"}) {
            mvc.perform(logado("/financas?atalho=" + atalho)).andExpect(status().isOk());
        }
        mvc.perform(logado("/financas?atalho=xyz").header("Referer", "http://localhost/financas?atalho=xyz"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Atalho de período desconhecido.")));
    }

    @Test
    @DisplayName("Período sem venda: mensagem em vez de gráficos; JSON marca vazio")
    void periodoVazio() throws Exception {
        String vazio = "?atalho=personalizado&inicio=2020-01-01&fim=2020-01-07";
        mvc.perform(logado("/financas" + vazio))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nenhuma venda neste período.")))
                .andExpect(content().string(not(containsString("grafico-serie"))))
                .andExpect(content().string(containsString("Produtos parados")));
        mvc.perform(logado("/financas/dados" + vazio).accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.vazio").value(true))
                .andExpect(jsonPath("$.receita.valores", hasSize(7)));
    }

    @Test
    @DisplayName("JSON dos gráficos com o mesmo filtro e a métrica do ranking")
    void jsonDosGraficosSegueOFiltroEAMetrica() throws Exception {
        mvc.perform(logado("/financas/dados" + MARCO).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vazio").value(false))
                .andExpect(jsonPath("$.porHora").value(false))
                .andExpect(jsonPath("$.receita.rotulos", hasSize(15)))
                .andExpect(jsonPath("$.receita.rotulos[0]").value("01/03"))
                .andExpect(jsonPath("$.receita.valores[0]").value(160.00))
                .andExpect(jsonPath("$.receita.textos[0]").value("R$ 160,00"))
                .andExpect(jsonPath("$.produtos.rotulos[0]").value("Camiseta 1"))
                .andExpect(jsonPath("$.categorias.rotulos[0]").value("Camiseta"))
                .andExpect(jsonPath("$.formasValor.textos[0]").value("R$ 67,00"));
        mvc.perform(logado("/financas/dados" + MARCO + "&metrica=quantidade").accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.metrica").value("QUANTIDADE"))
                .andExpect(jsonPath("$.produtos.rotulos[0]").value("Bóton 1"))
                .andExpect(jsonPath("$.produtos.textos[0]").value("3 un."));
        mvc.perform(logado("/financas/dados?evento=" + massa.e.get("Feira 1").getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.porHora").value(true))
                .andExpect(jsonPath("$.receita.rotulos", hasSize(9)))
                .andExpect(jsonPath("$.receita.rotulos[0]").value("09h"));
    }

    @Test
    @DisplayName("CSV de cada tabela: BOM, ponto e vírgula e vírgula decimal")
    void csvDeCadaTabelaTemBomPontoEVirgulaEVirgulaDecimal() throws Exception {
        byte[] corpo = mvc.perform(logado("/financas/exportar/demonstrativo.csv" + MARCO))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("financas-demonstrativo.csv")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertTrue(corpo[0] == (byte) 0xEF && corpo[1] == (byte) 0xBB && corpo[2] == (byte) 0xBF, "BOM");
        String texto = new String(corpo, StandardCharsets.UTF_8);
        assertTrue(texto.contains("\"01/03/2025 a 15/03/2025\";\"'= Receita líquida\";229,00;100,00"), texto);
        assertTrue(texto.contains("\"(-) Despesas\";115,50;50,44"), texto);
        for (String tabela : ExportadorFinancas.TABELAS) {
            mvc.perform(logado("/financas/exportar/" + tabela + ".csv" + MARCO)).andExpect(status().isOk());
        }
        String produtos = mvc.perform(logado("/financas/exportar/produtos.csv" + MARCO))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertTrue(
                produtos.contains(";1;\"Camiseta 1\";\"Camiseta\";\"não\";1;50,00;25,00;25,00;50,00;21,83;A;0"),
                produtos);
        assertTrue(produtos.contains("\"Pôster 2\";\"Pôster\";\"sim\""), produtos);
        mvc.perform(logado("/financas/exportar/senhas.csv").header("Referer", "http://localhost/financas"))
                .andExpect(redirectedUrl("/financas"));
    }

    @Test
    @DisplayName("Link do aviso de lucro parcial: o estoque mostra só os produtos sem custo")
    void estoqueSemCusto() throws Exception {
        mvc.perform(logado("/estoque?semCusto=true"))
                .andExpect(content().string(containsString("id=\"filtro-sem-custo\"")))
                .andExpect(content().string(containsString("Bóton 3")))
                .andExpect(content().string(not(containsString("Bóton 1"))));
    }
}
