package br.com.lac.caixa.financas;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lac.caixa.servico.EventoService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

/** Relatório do evento, comparativo e metas com a massa fixa. */
@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:relatorio;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class RelatorioHttpTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationContext contexto;

    @Autowired
    EventoService eventos;

    MassaDeTeste massa;

    @BeforeEach
    void montar() {
        massa = new MassaDeTeste(contexto).montar();
    }

    String pagina(String url) throws Exception {
        return mvc.perform(get(url).sessionAttr("autenticado", true))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    static String trecho(String html, String inicio, String fim) {
        int i = html.indexOf(inicio);
        assertTrue(i >= 0, "sem " + inicio);
        return html.substring(i, html.indexOf(fim, i) + fim.length());
    }

    @Test
    @DisplayName("Relatório da Feira 1: os mesmos números da tela de Finanças, despesas, cancelamentos e ajustes")
    void relatorioDaFeira1() throws Exception {
        long id = massa.e.get("Feira 1").getId();
        String relatorio = pagina("/eventos/" + id + "/relatorio");
        String tela = pagina("/financas?evento=" + id);

        assertEquals(
                trecho(tela, "<table class=\"dados demonstrativo\"", "</table>"),
                trecho(relatorio, "<table class=\"dados demonstrativo\"", "</table>"));
        assertEquals(
                trecho(tela, "<div class=\"indicadores cartoes-financas\"", "id=\"cartao-itens\""),
                trecho(relatorio, "<div class=\"indicadores cartoes-financas\"", "id=\"cartao-itens\""));
        for (String numero : new String[] {"R$ 135,00", "R$ 3,45", "63,70%", "R$ 33,75", "R$ 159,00", "R$ 49,00"}) {
            assertTrue(relatorio.contains(numero), numero);
        }
        assertTrue(relatorio.contains("01/03/2025 09:00 a 01/03/2025 18:00"));
        assertTrue(!relatorio.contains("class=\"nav-links\""), "relatório sem menu");
        assertTrue(relatorio.contains("id=\"aviso-parcial\""));
        assertTrue(relatorio.contains("<svg") && relatorio.contains("Top 10 produtos por receita líquida"));
        String top = trecho(relatorio, "id=\"relatorio-top\"", "</table>");
        assertTrue(top.indexOf("Camiseta 1") < top.indexOf("Camiseta 3"), top);
        String despesas = trecho(relatorio, "id=\"relatorio-despesas\"", "</table>");
        assertTrue(despesas.contains("Lona") && despesas.contains("Mesa") && despesas.contains("R$ 80,00"), despesas);
        assertTrue(!despesas.contains("Uber"), "despesa sem evento fica fora");
        String cancelamentos = trecho(relatorio, "id=\"relatorio-cancelamentos\"", "</table>");
        assertTrue(cancelamentos.contains("R$ 30,00") && cancelamentos.contains("Ensaio"), cancelamentos);
        String ajustes = trecho(relatorio, "id=\"relatorio-ajustes\"", "</table>");
        assertTrue(
                ajustes.contains("R$ -16,00") && ajustes.contains("R$ -5,00") && ajustes.contains("Ensaio"), ajustes);
    }

    @Test
    @DisplayName("Relatório de evento sem vendas e sem login")
    void relatorioVazioESemLogin() throws Exception {
        long id = massa.e.get("Feira 3").getId();
        assertTrue(pagina("/eventos/" + id + "/relatorio").contains("Nenhuma venda neste evento."));
        mvc.perform(get("/eventos/" + id + "/relatorio")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/eventos/comparativo")).andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Comparativo com os 3 eventos lado a lado e o gráfico de receita e lucro")
    void comparativo() throws Exception {
        String html = pagina("/eventos/comparativo");
        String tabela = trecho(html, "id=\"tabela-comparativo\"", "</table>");
        assertTrue(tabela.indexOf("Feira 1") < tabela.indexOf("Feira 2")
                && tabela.indexOf("Feira 2") < tabela.indexOf("Feira 3"));
        String receita = trecho(tabela, "Receita líquida</th>", "</tr>");
        assertTrue(
                receita.contains("R$ 135,00") && receita.contains("R$ 29,00") && receita.contains("R$ 0,00"), receita);
        String lucro = trecho(tabela, "Lucro líquido</th>", "</tr>");
        assertTrue(lucro.contains("R$ 3,45 (parcial)") && lucro.contains("R$ 5,37 (parcial)"), lucro);
        assertTrue(html.contains("Receita líquida e lucro líquido por evento"));
        String grafico = trecho(html, "class=\"grafico-svg\"", "</svg>");
        assertTrue(
                grafico.contains("Feira 3") && grafico.contains("R$ 135,00") && grafico.contains("R$ 5,37"), grafico);
    }

    @Test
    @DisplayName("Metas: progresso com valor e % na tela de Finanças e no relatório; só com filtro de evento")
    void metas() throws Exception {
        var feira1 = massa.e.get("Feira 1");
        var feira2 = massa.e.get("Feira 2");
        try {
            eventos.editar(
                    feira1.getId(),
                    feira1.getNome(),
                    feira1.getInicio(),
                    feira1.getFim(),
                    null,
                    new BigDecimal("100"),
                    new BigDecimal("10"));

            mvc.perform(get("/financas?evento=" + feira1.getId()).sessionAttr("autenticado", true))
                    .andExpect(content().string(containsString("R$ 135,00 de R$ 100,00 (135,00%)")))
                    .andExpect(content().string(containsString("meta atingida")))
                    .andExpect(content().string(containsString("aria-valuenow=\"100.00\"")))
                    .andExpect(content().string(containsString("R$ 3,45 de R$ 10,00 (34,50%)")))
                    .andExpect(content().string(containsString("aria-valuenow=\"34.50\"")))
                    .andExpect(content().string(containsString("(lucro parcial: há itens sem custo)")));
            assertTrue(
                    pagina("/eventos/" + feira1.getId() + "/relatorio").contains("R$ 135,00 de R$ 100,00 (135,00%)"));
            mvc.perform(get("/financas?evento=" + feira1.getId() + "&categoria="
                                    + massa.c.get("Bóton").getId())
                            .sessionAttr("autenticado", true))
                    .andExpect(content().string(not(containsString("id=\"metas\""))));
            mvc.perform(get("/financas?evento=" + feira2.getId()).sessionAttr("autenticado", true))
                    .andExpect(content().string(not(containsString("id=\"metas\""))));
            mvc.perform(get("/financas?atalho=personalizado&inicio=2025-03-01&fim=2025-03-01")
                            .sessionAttr("autenticado", true))
                    .andExpect(content().string(not(containsString("id=\"metas\""))));
            var futuro = eventos.criar(
                    "Feira futura",
                    java.time.LocalDateTime.of(2030, 1, 5, 9, 0),
                    java.time.LocalDateTime.of(2030, 1, 5, 18, 0),
                    null,
                    new BigDecimal("500"),
                    null);
            mvc.perform(get("/financas?evento=" + futuro.getId()).sessionAttr("autenticado", true))
                    .andExpect(content().string(containsString("Nenhuma venda neste período.")))
                    .andExpect(content().string(containsString("R$ 0,00 de R$ 500,00 (0,00%)")));
        } finally {
            eventos.editar(feira1.getId(), feira1.getNome(), feira1.getInicio(), feira1.getFim(), null, null, null);
        }
    }
}
