package br.com.lac.caixa.servico;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.repositorio.FiltroHistorico;
import br.com.lac.caixa.repositorio.VendaRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Eventos: sobreposição recusada e a venda no evento pela data e hora. */
@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:eventos;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class EventoTest {
    @Autowired
    EventoService eventos;

    @Autowired
    CaixaService caixa;

    @Autowired
    EstoqueService estoque;

    @Autowired
    VendaRepository vendas;

    @Autowired
    FechamentoService fechamento;

    @Autowired
    ConsultaHistorico historico;

    @Autowired
    MockMvc mvc;

    static LocalDateTime t(String texto) {
        return LocalDateTime.parse(texto);
    }

    Venda vendaEm(LocalDateTime quando) {
        var p = estoque.criarNovo("Bóton Evento " + System.nanoTime(), new BigDecimal("6"), 10, null);
        var v = caixa.registrarVenda(
                        Map.of(p.getId(), 1),
                        List.of(PagamentoInformado.total(FormaPagamento.PIX)),
                        null,
                        null,
                        UUID.randomUUID().toString())
                .venda();
        v.setDataHora(quando);
        return vendas.saveAndFlush(v);
    }

    @Test
    @DisplayName("Sobreposição: cruzar, conter ou ficar dentro é recusado; encostar (fim = início) é aceito")
    void sobreposicao() {
        eventos.criar("Feira de março", t("2025-03-10T10:00"), t("2025-03-10T18:00"), null);

        var cruza = assertThrows(
                RegraDeNegocioException.class,
                () -> eventos.criar("Cruza", t("2025-03-10T17:00"), t("2025-03-10T20:00"), null));
        assertTrue(cruza.getMessage().contains("Feira de março"), cruza.getMessage());
        assertThrows(
                RegraDeNegocioException.class,
                () -> eventos.criar("Contém", t("2025-03-10T09:00"), t("2025-03-10T19:00"), null));
        assertThrows(
                RegraDeNegocioException.class,
                () -> eventos.criar("Dentro", t("2025-03-10T11:00"), t("2025-03-10T12:00"), null));
        assertThrows(
                RegraDeNegocioException.class,
                () -> eventos.criar("Começa antes", t("2025-03-10T08:00"), t("2025-03-10T10:01"), null));
        var depois = eventos.criar("Depois", t("2025-03-10T18:00"), t("2025-03-10T22:00"), null);
        var antes = eventos.criar("Antes", t("2025-03-10T06:00"), t("2025-03-10T10:00"), null);
        assertThrows(
                RegraDeNegocioException.class,
                () -> eventos.criar("Ao contrário", t("2025-03-12T10:00"), t("2025-03-12T09:00"), null));
        assertThrows(
                RegraDeNegocioException.class,
                () -> eventos.editar(depois.getId(), "Depois", t("2025-03-10T17:59"), t("2025-03-10T22:00"), null));
        eventos.editar(antes.getId(), "Antes (manhã)", t("2025-03-10T06:00"), t("2025-03-10T10:00"), "café");
        assertEquals("Antes (manhã)", eventos.buscar(antes.getId()).getNome());
    }

    @Test
    @DisplayName("Venda no evento certo pela data e hora, com as bordas: início entra, fim não")
    void vendaNoEventoCerto() {
        var manha = eventos.criar("Manhã", t("2025-04-05T08:00"), t("2025-04-05T12:00"), null);
        var tarde = eventos.criar("Tarde", t("2025-04-05T12:00"), t("2025-04-05T18:00"), null);
        var noInicio = vendaEm(t("2025-04-05T08:00"));
        var antesDoFim = vendaEm(LocalDateTime.of(2025, 4, 5, 11, 59, 59, 999_000_000));
        var naBorda = vendaEm(t("2025-04-05T12:00"));
        var noFimDaTarde = vendaEm(t("2025-04-05T18:00"));
        var antesDeTudo = vendaEm(t("2025-04-05T07:59:59"));

        var daManha = historico
                .consultar(FiltroHistorico.doEvento(eventos.periodo(manha.getId())), 0)
                .pagina()
                .map(Venda::getId)
                .getContent();
        var daTarde = historico
                .consultar(FiltroHistorico.doEvento(eventos.periodo(tarde.getId())), 0)
                .pagina()
                .map(Venda::getId)
                .getContent();

        assertEquals(
                List.of(antesDoFim.getId(), noInicio.getId()).stream().sorted().toList(),
                daManha.stream().sorted().toList());
        assertEquals(List.of(naBorda.getId()), daTarde);
        assertEquals(
                "Tarde", eventos.doMomento(naBorda.getDataHora()).orElseThrow().getNome());
        assertTrue(eventos.doMomento(noFimDaTarde.getDataHora()).isEmpty());
        assertTrue(eventos.doMomento(antesDeTudo.getDataHora()).isEmpty());
        var f = fechamento.consultar(FiltroHistorico.doEvento(eventos.periodo(manha.getId())));
        assertEquals(2, f.quantidade());
        assertEquals(0, new BigDecimal("12").compareTo(f.total()));
    }

    @Test
    @DisplayName("Corrigir a data da venda a move de evento (não há vínculo gravado)")
    void corrigirDataMoveDeEvento() {
        var junho = eventos.criar("Junho", t("2025-06-01T10:00"), t("2025-06-01T18:00"), null);
        var julho = eventos.criar("Julho", t("2025-07-01T10:00"), t("2025-07-01T18:00"), null);
        var v = vendaEm(t("2025-06-01T15:00"));
        assertEquals("Junho", eventos.doMomento(v.getDataHora()).orElseThrow().getNome());

        caixa.alterarDataHora(v.getId(), t("2025-07-01T11:00"));

        var deJunho = historico.consultar(FiltroHistorico.doEvento(eventos.periodo(junho.getId())), 0);
        var deJulho = historico.consultar(FiltroHistorico.doEvento(eventos.periodo(julho.getId())), 0);
        assertEquals(0, deJunho.pagina().getTotalElements());
        assertEquals(List.of(v.getId()), deJulho.pagina().map(Venda::getId).getContent());
    }

    @Test
    @DisplayName("Telas: criar, recusar sobreposição com mensagem, filtrar histórico e fechamento pelo evento")
    void telas() throws Exception {
        mvc.perform(post("/eventos")
                        .sessionAttr("autenticado", true)
                        .param("nome", "Encontro LAC")
                        .param("inicio", "2025-08-09T09:00")
                        .param("fim", "2025-08-09T17:00"))
                .andExpect(redirectedUrl("/eventos"))
                .andExpect(flash().attribute("sucesso", containsString("Encontro LAC")));
        mvc.perform(post("/eventos")
                        .sessionAttr("autenticado", true)
                        .header("Referer", "/eventos")
                        .param("nome", "Cruza")
                        .param("inicio", "2025-08-09T16:00")
                        .param("fim", "2025-08-09T20:00"))
                .andExpect(flash().attribute("erro", containsString("Eventos não podem se sobrepor")));
        var evento = eventos.listar().stream()
                .filter(e -> e.getNome().equals("Encontro LAC"))
                .findFirst()
                .orElseThrow();
        var dentro = vendaEm(t("2025-08-09T10:30"));
        var fora = vendaEm(t("2025-08-09T17:00"));

        mvc.perform(get("/eventos").sessionAttr("autenticado", true))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Encontro LAC")));
        mvc.perform(get("/eventos").param("editar", evento.getId().toString()).sessionAttr("autenticado", true))
                .andExpect(content().string(containsString("Salvar evento")));
        mvc.perform(get("/historico").param("evento", evento.getId().toString()).sessionAttr("autenticado", true))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("evento-da-venda")))
                .andExpect(content().string(containsString("<td class=\"num suave\">" + dentro.getId() + "</td>")))
                .andExpect(content().string(not(containsString("<td class=\"num suave\">" + fora.getId() + "</td>"))));
        mvc.perform(get("/fechamento")
                        .param("evento", evento.getId().toString())
                        .sessionAttr("autenticado", true))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("R$ 6,00")));
        mvc.perform(get("/fechamento/exportar.csv")
                        .param("evento", evento.getId().toString())
                        .sessionAttr("autenticado", true))
                .andExpect(content().string(containsString("09/08/2025")));
        mvc.perform(get("/historico/exportar.csv")
                        .param("evento", evento.getId().toString())
                        .sessionAttr("autenticado", true))
                .andExpect(status().isOk());
    }
}
