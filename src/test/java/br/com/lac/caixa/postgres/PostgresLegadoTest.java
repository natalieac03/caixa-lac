package br.com.lac.caixa.postgres;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.servico.ConsultaHistorico;
import br.com.lac.caixa.servico.FechamentoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.flywaydb.core.Flyway;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.sql.DriverManager;
import java.util.UUID;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * O caminho da produção: banco criado pelo Hibernate da versão original,
 * Flyway faz baseline e aplica V2-V5, Hibernate valida. Depois as telas
 * rodam no Postgres de verdade (o H2 aceita SQL que o Postgres recusa).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"spring.profiles.active=test"})
@AutoConfigureMockMvc
class PostgresLegadoTest {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    /** Total por forma do fechamento antigo (V5), calculado antes da V6. */
    static final Map<FormaPagamento, BigDecimal> ANTES_DA_V6 = new EnumMap<>(FormaPagamento.class);

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) throws Exception {
        POSTGRES.start();
        String sql = new String(PostgresLegadoTest.class.getResourceAsStream("/db/postgres-legado.sql").readAllBytes(), StandardCharsets.UTF_8);
        try (var c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var s = c.createStatement()) {
            s.execute(sql);
        }
        // Até a V5, como estava em produção antes do pagamento dividido. Mais duas
        // vendas antigas: uma de valor zero e uma cancelada.
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .baselineOnMigrate(true).target("5").load().migrate();
        try (var c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var s = c.createStatement()) {
            s.execute("insert into venda(data_hora, forma_pagamento, valor_total) values ('2024-05-10 18:00', 'DINHEIRO', 0)");
            s.execute("insert into venda(data_hora, forma_pagamento, valor_total, cancelada, data_cancelamento, motivo_cancelamento)"
                    + " values ('2024-05-10 19:00', 'PIX', 7, true, '2024-05-10 19:05', 'Teste')");
            // Fechamento como a versão anterior calculava: pela forma da venda, sem canceladas.
            try (var r = s.executeQuery("select forma_pagamento, count(*), sum(coalesce(valor_cobrado, valor_total)) from venda"
                    + " where not cancelada group by forma_pagamento")) {
                while (r.next()) ANTES_DA_V6.put(FormaPagamento.valueOf(r.getString(1)), r.getBigDecimal(3));
            }
        }
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConsultaHistorico historico;
    @Autowired FechamentoService fechamento;

    /**
     * Vendas antigas, todas em 10/05/2024: #1 Pix R$ 23 (Bóton Capivara x3,
     * Adesivo Capivara Grande x1), #2 Outro "Vale" R$ 5 (Bóton Logo LAC x1),
     * #3 valor zero sem itens (cortesia) e #4 cancelada.
     */
    @ParameterizedTest(name = "forma={0} inicio={1} fim={2} produto={3}")
    @CsvSource({
            // vazio
            ",,,,                                  3, 5, 28",
            // só data
            ",2024-05-10,2024-05-10,,              3, 5, 28",
            ",2024-05-11,,,                        0, 0, 0",
            ",,2024-05-09,,                        0, 0, 0",
            // só forma
            "PIX,,,,                               1, 4, 23",
            "OUTRO,,,,                             1, 1, 5",
            "DEBITO,,,,                            0, 0, 0",
            // só produto (trecho, sem diferenciar maiúsculas)
            ",,,CAPIVARA,                          1, 4, 23",
            ",,,logo,                              1, 1, 5",
            ",,,bóton,                             2, 5, 28",
            ",,,%,                                 0, 0, 0",
            // todos juntos
            "PIX,2024-05-10,2024-05-10,capivara,   1, 4, 23",
            "PIX,2024-05-10,2024-05-10,logo,       0, 0, 0",
            "OUTRO,2024-05-01,2024-05-31,lac,      1, 1, 5"})
    void filtrosNoBancoLegado(String forma, String inicio, String fim, String produto,
                              long vendas, long itens, String total) throws Exception {
        ConferenciaDeFiltros.conferir(mvc, historico, fechamento, forma, inicio, fim, produto, vendas, itens, total);
    }

    @Test void migracaoPreservaVendasEVinculos() {
        assertEquals(1, jdbc.queryForObject("select count(*) from flyway_schema_history where type = 'BASELINE'", Integer.class));
        assertEquals(5, jdbc.queryForObject("select count(*) from flyway_schema_history where type = 'SQL' and success", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from item_venda where venda_id = 1", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from promocao_item where promocao_id is not null", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from information_schema.tables where table_name in ('venda_itens', 'promocao_itens')", Integer.class));
        assertNull(jdbc.queryForObject("select valor_cobrado from venda where id = 1", Object.class));
    }

    @Test void v6DaUmaLinhaDePagamentoPorVendaAntigaEOFechamentoNaoMuda() {
        assertEquals(5, jdbc.queryForObject("select count(*) from flyway_schema_history where type = 'SQL' and success", Integer.class));
        // Cada venda antiga com valor tem exatamente uma linha, com a forma e o valor que tinha.
        var semLinhaCerta = jdbc.queryForList("""
                select v.id from venda v
                where v.id <= 4 and coalesce(v.valor_cobrado, v.valor_total) > 0
                  and (select count(*) from pagamento_venda p where p.venda_id = v.id
                       and p.forma = v.forma_pagamento and p.valor = coalesce(v.valor_cobrado, v.valor_total)) <> 1
                """, Long.class);
        assertEquals(java.util.List.of(), semLinhaCerta);
        assertEquals(3, jdbc.queryForObject("select count(*) from pagamento_venda where venda_id <= 4", Integer.class));
        assertEquals("Vale", jdbc.queryForObject("select descricao from pagamento_venda where venda_id = 2", String.class));
        // Conferência pós-deploy: vendas = vendas com pagamento + cortesias.
        int antigas = jdbc.queryForObject("select count(*) from venda where id <= 4", Integer.class);
        int comPagamento = jdbc.queryForObject("select count(distinct venda_id) from pagamento_venda where venda_id <= 4", Integer.class);
        int cortesias = jdbc.queryForObject("select count(*) from venda v where id <= 4 and coalesce(valor_cobrado, valor_total) = 0", Integer.class);
        assertEquals(antigas, comPagamento + cortesias);

        var depois = fechamento.consultar(null, java.time.LocalDate.of(2024, 12, 31));
        for (FormaPagamento forma : FormaPagamento.values()) {
            BigDecimal antes = ANTES_DA_V6.getOrDefault(forma, BigDecimal.ZERO);
            BigDecimal agora = depois.formas().stream().filter(l -> l.forma() == forma).findFirst().orElseThrow().total();
            assertEquals(0, antes.compareTo(agora), forma + ": antes " + antes + ", depois " + agora);
        }
        assertTrue(depois.isConfere());
        assertEquals(1, depois.cortesias());
    }

    @Test void historicoEFechamentoComFiltrosVazios() throws Exception {
        for (String url : new String[]{"/historico", "/historico?forma=PIX", "/historico?inicio=2020-01-01",
                "/historico?fim=2040-01-01", "/historico?produto=b%C3%B3ton", "/historico?pagina=1",
                "/historico/exportar.csv", "/fechamento?inicio=2020-01-01", "/fechamento/exportar.csv?fim=2040-01-01"}) {
            mvc.perform(get(url).sessionAttr("autenticado", true)).andExpect(status().isOk());
        }
        mvc.perform(get("/historico").sessionAttr("autenticado", true)).andExpect(content().string(containsString("23,00")));
        mvc.perform(get("/historico/exportar.csv").param("forma", "OUTRO").sessionAttr("autenticado", true))
                .andExpect(content().string(containsString("\"Vale\"")));
    }

    @Test void vendaECancelamentoNoBancoMigrado() throws Exception {
        mvc.perform(post("/venda/previa").sessionAttr("autenticado", true).param("qtd_1", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(15.0));
        int antes = jdbc.queryForObject("select quantidade from produto where id = 3", Integer.class);
        String token = UUID.randomUUID().toString();
        mvc.perform(post("/venda").sessionAttr("autenticado", true).param("token", token)
                        .param("qtd_3", "2").param("pag_forma_0", "PIX"))
                .andExpect(redirectedUrl("/historico"));
        long id = jdbc.queryForObject("select id from venda where token = ?", Long.class, token);
        assertTrue(id > 2, "a identidade continua depois das vendas antigas");
        assertEquals(0, new java.math.BigDecimal("14").compareTo(
                jdbc.queryForObject("select valor_cobrado from venda where id = ?", java.math.BigDecimal.class, id)));
        assertEquals(antes - 2, jdbc.queryForObject("select quantidade from produto where id = 3", Integer.class));
        mvc.perform(post("/historico/" + id + "/cancelar").sessionAttr("autenticado", true).param("motivo", "Ensaio"))
                .andExpect(redirectedUrl("/historico"));
        assertEquals(antes, jdbc.queryForObject("select quantidade from produto where id = 3", Integer.class));
        assertTrue(jdbc.queryForObject("select cancelada from venda where id = ?", Boolean.class, id));
    }
}
