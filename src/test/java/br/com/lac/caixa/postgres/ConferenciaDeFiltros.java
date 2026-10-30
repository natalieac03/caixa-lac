package br.com.lac.caixa.postgres;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.servico.ConsultaHistorico;
import br.com.lac.caixa.servico.FechamentoService;
import br.com.lac.caixa.servico.FiltroHistorico;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uma combinação de filtros conferida no Postgres: indicadores, CSV e,
 * quando só há datas, o fechamento; e as três telas respondem 200.
 * Campo vazio no CsvSource = filtro não preenchido.
 */
final class ConferenciaDeFiltros {
    private ConferenciaDeFiltros() {
    }

    static void conferir(MockMvc mvc, ConsultaHistorico historico, FechamentoService fechamento,
                         String forma, String inicio, String fim, String produto,
                         long vendas, long itens, String total) throws Exception {
        String f = forma == null ? "" : forma, i = inicio == null ? "" : inicio, t = fim == null ? "" : fim, p = produto == null ? "" : produto;
        var filtro = FiltroHistorico.criar(f.isEmpty() ? null : FormaPagamento.valueOf(f), i, t, null, p);

        var r = historico.consultar(filtro, 0).resumo();
        assertEquals(vendas, r.getVendas(), "vendas");
        assertEquals(itens, r.getItens(), "itens");
        assertEquals(0, new BigDecimal(total).compareTo(r.getTotal()), "total " + r.getTotal());
        assertEquals(vendas, historico.exportar(filtro).size(), "linhas do CSV");

        if (f.isEmpty() && p.isEmpty()) {
            var fe = fechamento.consultar(filtro.inicio(), filtro.fim());
            assertEquals(vendas, fe.quantidade(), "vendas no fechamento");
            assertEquals(0, new BigDecimal(total).compareTo(fe.total()), "total do fechamento " + fe.total());
        }

        for (String url : new String[]{"/historico", "/historico/exportar.csv", "/fechamento", "/fechamento/exportar.csv"}) {
            var req = get(url).sessionAttr("autenticado", true).param("inicio", i).param("fim", t);
            if (url.startsWith("/historico")) req.param("forma", f).param("produto", p);
            var resposta = mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse();
            if (url.equals("/historico/exportar.csv")) {
                long linhas = new String(resposta.getContentAsByteArray(), StandardCharsets.UTF_8).lines().count() - 1;
                assertEquals(vendas, linhas, "linhas do CSV por HTTP");
            }
        }
    }
}
