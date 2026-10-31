package br.com.lac.caixa.servico;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
import br.com.lac.caixa.modelo.Evento;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.repositorio.FiltroFinancas;
import br.com.lac.caixa.repositorio.FiltroHistorico;
import br.com.lac.caixa.repositorio.VendaRepository;
import br.com.lac.caixa.servico.financas.FinancasService;
import java.math.BigDecimal;
import java.time.Clock;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Evento sem fim (em andamento), iniciar e encerrar, e o "Dia a dia" (vendas fora de qualquer evento). */
@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:evento_aberto;DB_CLOSE_DELAY=-1"
        })
@AutoConfigureMockMvc
class EventoAbertoTest {
    @TestConfiguration
    static class Relogio {
        @Bean
        @Primary
        Clock relogioDeTeste() {
            return new RelogioDeTeste();
        }
    }

    static final LocalDateTime INICIO = LocalDateTime.of(2026, 3, 7, 14, 0);

    @Autowired
    Clock relogio;

    @Autowired
    EventoService eventos;

    @Autowired
    CaixaService caixa;

    @Autowired
    EstoqueService estoque;

    @Autowired
    VendaRepository vendas;

    @Autowired
    ConsultaHistorico historico;

    @Autowired
    FechamentoService fechamento;

    @Autowired
    FinancasService financas;

    @Autowired
    AlertasService alertas;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    Produto boton;

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
        irPara(INICIO);
        boton = estoque.criarNovo("Bóton Aberto", new BigDecimal("6"), 500, null, new BigDecimal("2"));
    }

    void irPara(LocalDateTime quando) {
        ((RelogioDeTeste) relogio).definir(quando);
    }

    /** Venda de R$ 6 registrada no relógio de agora (ou movida para a data dada). */
    Venda vender(LocalDateTime quando) {
        var v = caixa.registrarVenda(
                        Map.of(boton.getId(), 1),
                        List.of(PagamentoInformado.total(FormaPagamento.PIX)),
                        null,
                        null,
                        UUID.randomUUID().toString())
                .venda();
        if (quando != null) {
            v.setDataHora(quando);
            v = vendas.saveAndFlush(v);
        }
        return v;
    }

    List<Long> idsDoEvento(Evento e) {
        return historico
                .consultar(FiltroHistorico.doEvento(eventos.periodo(e.getId())), 0)
                .pagina()
                .map(Venda::getId)
                .getContent();
    }

    List<Long> idsDoDiaADia(LocalDate de, LocalDate ate) {
        return historico
                .consultar(new FiltroHistorico(null, de, ate, "").comDiaADia(LocalDateTime.now(relogio)), 0)
                .pagina()
                .map(Venda::getId)
                .getContent();
    }

    @Test
    @DisplayName("Iniciar com outro aberto é recusado (pelo botão e pela tela Eventos); encerrar grava o fim agora")
    void iniciarEEncerrar() {
        Evento feira = eventos.iniciar("Feira de Março");
        assertTrue(feira.isAberto());
        assertEquals(INICIO, feira.getInicio());
        assertEquals(feira.getId(), eventos.aberto().orElseThrow().getId());

        irPara(INICIO.plusHours(1));
        var dois = assertThrows(RegraDeNegocioException.class, () -> eventos.iniciar("Outra"));
        assertTrue(dois.getMessage().contains("Feira de Março"), dois.getMessage());
        assertThrows(RegraDeNegocioException.class, () -> eventos.criar("Pela tela", INICIO.minusDays(1), null, null));

        irPara(INICIO.plusHours(4).plusMinutes(30));
        Evento encerrado = eventos.encerrar(feira.getId());
        assertEquals(INICIO.plusHours(4).plusMinutes(30), encerrado.getFim());
        assertTrue(eventos.aberto().isEmpty());
        assertThrows(RegraDeNegocioException.class, () -> eventos.encerrar(feira.getId()));

        // Nome vazio: a sugestão "Evento dd/mm"; encerrar no mesmo instante do início é recusado.
        Evento sugerido = eventos.iniciar(" ");
        assertEquals("Evento 07/03", sugerido.getNome());
        assertThrows(RegraDeNegocioException.class, () -> eventos.encerrar(sugerido.getId()));
    }

    @Test
    @DisplayName("Venda durante o evento aberto cai nele; antes e depois, no dia a dia; bordas exatas")
    void vendasNoEventoENoDiaADia() {
        Venda antes = vender(INICIO.minusMinutes(1));
        Evento feira = eventos.iniciar("Feira de Março");
        Venda noInicio = vender(INICIO);
        irPara(INICIO.plusHours(2));
        Venda durante = vender(null);

        // Aberto: vale até agora
        assertEquals(List.of(durante.getId(), noInicio.getId()), idsDoEvento(feira));
        assertEquals(
                "Feira de Março",
                eventos.doMomento(durante.getDataHora()).orElseThrow().getNome());
        assertEquals(List.of(antes.getId()), idsDoDiaADia(INICIO.toLocalDate(), INICIO.toLocalDate()));

        // Encerra às 17:00: a venda exatamente às 17:00 já é do dia a dia
        irPara(INICIO.plusHours(3));
        eventos.encerrar(feira.getId());
        Venda noFim = vender(null);
        assertEquals(INICIO.plusHours(3), noFim.getDataHora());
        assertFalse(idsDoEvento(feira).contains(noFim.getId()));
        assertEquals(List.of(noFim.getId(), antes.getId()), idsDoDiaADia(INICIO.toLocalDate(), INICIO.toLocalDate()));
        assertTrue(eventos.doMomento(noFim.getDataHora()).isEmpty());

        // Fechamento e finanças com Dia a dia: só as duas fora do evento
        var f = fechamento.consultar(new FiltroHistorico(null, INICIO.toLocalDate(), INICIO.toLocalDate(), "")
                .comDiaADia(LocalDateTime.now(relogio)));
        assertEquals(2, f.quantidade());
        var r = financas.resumo(
                FiltroFinancas.diaADia(INICIO.toLocalDate(), INICIO.toLocalDate(), LocalDateTime.now(relogio)));
        assertEquals(2, r.vendas());
        assertEquals(0, new BigDecimal("12").compareTo(r.receitaLiquida()));
    }

    @Test
    @DisplayName("Sobreposição: o aberto conta como indo até agora; um evento marcado depois limita o aberto")
    void sobreposicaoComAberto() {
        Evento feira = eventos.iniciar("Feira de Março");
        irPara(INICIO.plusHours(3));
        // Cruza [14:00, agora=17:00)
        assertThrows(
                RegraDeNegocioException.class,
                () -> eventos.criar("Cruza", INICIO.plusHours(2), INICIO.plusHours(4), null));
        // Encosta no início do aberto: ok
        eventos.criar("Manhã", INICIO.minusHours(4), INICIO, null);
        // Marcado para depois de agora: ok; quando ele começa, o aberto passa a valer só até o início dele
        Evento noite = eventos.criar("Noite", INICIO.plusHours(5), INICIO.plusHours(8), null);
        irPara(INICIO.plusHours(6));
        assertEquals(INICIO.plusHours(5), eventos.periodo(feira.getId()).fim());
        Venda daNoite = vender(null);
        assertEquals(
                "Noite", eventos.doMomento(daNoite.getDataHora()).orElseThrow().getNome());
        assertFalse(idsDoEvento(feira).contains(daNoite.getId()));
        assertTrue(idsDoEvento(noite).contains(daNoite.getId()));
        // Encerrar grava o fim no início do evento seguinte (nunca sobrepõe)
        assertEquals(INICIO.plusHours(5), eventos.encerrar(feira.getId()).getFim());
    }

    @Test
    @DisplayName("Aviso \"Evento aberto há mais de 12 horas\" aparece depois de 12 h e some ao encerrar")
    void avisoDeEventoAberto() {
        Evento feira = eventos.iniciar("Feira esquecida");
        irPara(INICIO.plusHours(12));
        assertTrue(alertas.calcular().secoes().stream().noneMatch(s -> s.id().equals("alerta-evento-aberto")));
        irPara(INICIO.plusHours(12).plusMinutes(1));
        var secao = alertas.calcular().secoes().stream()
                .filter(s -> s.id().equals("alerta-evento-aberto"))
                .findFirst()
                .orElseThrow();
        assertEquals("evento-aberto-" + feira.getId(), secao.avisos().get(0).id());
        assertTrue(secao.avisos().get(0).texto().contains("Feira esquecida"));
        assertEquals("Encerrar", secao.avisos().get(0).linkTexto());
        eventos.encerrar(feira.getId());
        assertTrue(alertas.calcular().secoes().stream().noneMatch(s -> s.id().equals("alerta-evento-aberto")));
    }

    @Test
    @DisplayName("Telas: iniciar e encerrar pelo topo, selo no topo, filtro Dia a dia e etiqueta no histórico")
    void telas() throws Exception {
        Venda antes = vender(INICIO.minusHours(1));
        // Sem evento aberto: o topo oferece "Iniciar evento" com a sugestão de nome
        mvc.perform(get("/").sessionAttr("autenticado", true))
                .andExpect(content().string(containsString("id=\"botao-iniciar-evento\"")))
                .andExpect(content().string(containsString("value=\"Evento 07/03\"")))
                .andExpect(content().string(not(containsString("id=\"selo-evento\""))));

        mvc.perform(post("/eventos/iniciar")
                        .sessionAttr("autenticado", true)
                        .header("Referer", "http://localhost/historico")
                        .param("nome", "Feira de Outubro II"))
                .andExpect(redirectedUrl("/historico"))
                .andExpect(flash().attribute("sucesso", containsString("Feira de Outubro II")));
        Evento feira = eventos.aberto().orElseThrow();
        irPara(INICIO.plusMinutes(30));
        Venda durante = vender(null);

        // Com evento aberto: selo "Nome, desde HH:mm" e Encerrar com confirmação, em qualquer tela
        for (String url : new String[] {"/", "/estoque", "/financas"}) {
            mvc.perform(get(url).sessionAttr("autenticado", true))
                    .andExpect(content().string(containsString("Feira de Outubro II, desde 14:00")))
                    .andExpect(content().string(containsString("id=\"botao-encerrar\"")))
                    .andExpect(content().string(containsString("onsubmit=\"return confirm(")));
        }
        // Histórico: etiqueta com o evento ou "Dia a dia"; filtro Dia a dia e Todos
        String todos = mvc.perform(get("/historico").sessionAttr("autenticado", true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(todos.contains("<span class=\"evento-da-venda\">Feira de Outubro II</span>"), todos);
        assertTrue(todos.contains("<span class=\"evento-da-venda dia-a-dia\">Dia a dia</span>"), todos);
        assertTrue(todos.contains("<option value=\"dia-a-dia\">Dia a dia (fora dos eventos)</option>"));
        mvc.perform(get("/historico").param("evento", "dia-a-dia").sessionAttr("autenticado", true))
                .andExpect(content().string(containsString("<td class=\"num suave\">" + antes.getId() + "</td>")))
                .andExpect(
                        content().string(not(containsString("<td class=\"num suave\">" + durante.getId() + "</td>"))));
        mvc.perform(get("/fechamento")
                        .param("evento", "dia-a-dia")
                        .param("inicio", "2026-03-07")
                        .param("fim", "2026-03-07")
                        .sessionAttr("autenticado", true))
                .andExpect(content().string(containsString("R$ 6,00")));
        mvc.perform(get("/financas")
                        .param("atalho", "personalizado")
                        .param("inicio", "2026-03-07")
                        .param("fim", "2026-03-07")
                        .param("evento", "dia-a-dia")
                        .sessionAttr("autenticado", true))
                .andExpect(content().string(containsString("07/03/2026 · dia a dia (fora dos eventos)")));
        mvc.perform(get("/financas").param("evento", feira.getId().toString()).sessionAttr("autenticado", true))
                .andExpect(content()
                        .string(containsString("Feira de Outubro II (07/03/2026 14:00 a agora, em andamento)")));

        // Encerrar pelo topo
        irPara(INICIO.plusHours(5));
        mvc.perform(post("/eventos/" + feira.getId() + "/encerrar")
                        .sessionAttr("autenticado", true)
                        .header("Referer", "http://localhost/"))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("sucesso", containsString("encerrado às 19:00")));
        assertNull(eventos.aberto().orElse(null));
        // Tela Eventos: criar sem fim com outro aberto é recusado com mensagem
        eventos.iniciar("Outra feira");
        mvc.perform(post("/eventos")
                        .sessionAttr("autenticado", true)
                        .header("Referer", "http://localhost/eventos")
                        .param("nome", "Pela tela")
                        .param("inicio", "2026-03-06T10:00")
                        .param("fim", ""))
                .andExpect(flash().attribute("erro", containsString("Já há um evento em andamento")));
        mvc.perform(get("/eventos").sessionAttr("autenticado", true))
                .andExpect(content().string(containsString("id=\"evento-aberto\"")))
                .andExpect(content().string(containsString("em andamento")));
    }
}
