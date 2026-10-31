package br.com.lac.caixa.config;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import br.com.lac.caixa.CaixaApplication;
import br.com.lac.caixa.consulta.FiltroHistorico;
import br.com.lac.caixa.migracao.Migracoes;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.servico.CaixaService;
import br.com.lac.caixa.servico.ConsultaHistorico;
import br.com.lac.caixa.servico.EstoqueService;
import br.com.lac.caixa.servico.EventoService;
import br.com.lac.caixa.servico.PagamentoInformado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

/** Com a JVM em UTC, o app grava e mostra a hora de Brasília. */
@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:fuso_horario;DB_CLOSE_DELAY=-1"
        })
@AutoConfigureMockMvc
class FusoHorarioTest {
    static final Instant INSTANTE = Instant.parse("2026-03-08T00:30:00Z");

    static TimeZone original;

    @BeforeAll
    static void jvmEmUtc() {
        original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterAll
    static void restaurar() {
        TimeZone.setDefault(original);
    }

    @TestConfiguration
    static class RelogioParado {
        @Bean
        @Primary
        Clock relogioParado() {
            return Clock.fixed(INSTANTE, new RelogioConfig().relogio().getZone());
        }
    }

    @Autowired
    CaixaService caixa;

    @Autowired
    EstoqueService estoque;

    @Autowired
    EventoService eventos;

    @Autowired
    ConsultaHistorico historico;

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("JVM em UTC: venda às 21:30 de Brasília cai em 07/03 e dentro do evento das 18h às 23h")
    void vendaNoDiaENoEventoCertos() throws Exception {
        LocalDate dia = LocalDate.of(2026, 3, 7);
        var feira = eventos.criar("Feira noturna", dia.atTime(18, 0), dia.atTime(23, 0), null);
        var p = estoque.criarNovo("Bóton Noturno", new BigDecimal("6"), 10, null, new BigDecimal("2"));

        Venda v = caixa.registrarVenda(
                        Map.of(p.getId(), 1),
                        List.of(PagamentoInformado.total(FormaPagamento.PIX)),
                        null,
                        null,
                        UUID.randomUUID().toString())
                .venda();

        assertEquals(LocalDateTime.of(2026, 3, 7, 21, 30), v.getDataHora());
        assertEquals(
                feira.getId(), eventos.doMomento(v.getDataHora()).orElseThrow().getId());
        var doDia = historico
                .consultar(FiltroHistorico.periodo(dia, dia), 0)
                .pagina()
                .map(Venda::getId);
        assertTrue(doDia.getContent().contains(v.getId()), "a venda é do dia 07/03");
        var doEvento = historico
                .consultar(FiltroHistorico.doEvento(eventos.periodo(feira.getId())), 0)
                .pagina()
                .map(Venda::getId);
        assertTrue(doEvento.getContent().contains(v.getId()), "a venda é da Feira noturna");
    }

    @Test
    @DisplayName("Estoque: a data sugerida da compra é o dia de Brasília, pelo relógio da aplicação")
    void hojeDoEstoque() throws Exception {
        estoque.criarNovo("Adesivo do Fuso", new BigDecimal("8"), 5, null, null);
        mvc.perform(get("/estoque").sessionAttr("autenticado", true))
                .andExpect(content().string(containsString("name=\"data\" value=\"2026-03-07\"")));
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("No H2 a V20 (conversão de UTC para Brasília) não roda: o H2 local já está em Brasília")
    void h2NaoRodaAV20() {
        Integer v20 = jdbc.queryForObject(
                "select count(*) from \"flyway_schema_history\" where \"version\" = '20'", Integer.class);
        assertEquals(0, v20);
        Integer comuns = jdbc.queryForObject(
                "select count(*) from \"flyway_schema_history\" where \"type\" = 'SQL' and \"success\"", Integer.class);
        assertEquals(Migracoes.quantidade(), comuns);
    }

    @Test
    @DisplayName("O relógio da aplicação é o de Brasília, qualquer que seja o fuso da máquina")
    void relogioEmBrasilia() {
        assertEquals(
                "America/Sao_Paulo", new RelogioConfig().relogio().getZone().getId());
    }

    @Test
    @DisplayName("O main fixa o fuso da JVM em America/Sao_Paulo antes de subir o Spring")
    void mainFixaOFuso() throws Exception {
        var fixar = CaixaApplication.class.getDeclaredMethod("fixarFuso");
        TimeZone antes = TimeZone.getDefault();
        try {
            fixar.setAccessible(true);
            fixar.invoke(null);
            assertEquals("America/Sao_Paulo", TimeZone.getDefault().getID());
        } finally {
            TimeZone.setDefault(antes);
        }
    }
}
