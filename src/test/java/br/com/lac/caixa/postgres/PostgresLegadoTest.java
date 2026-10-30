package br.com.lac.caixa.postgres;

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
import java.nio.charset.StandardCharsets;
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

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) throws Exception {
        POSTGRES.start();
        String sql = new String(PostgresLegadoTest.class.getResourceAsStream("/db/postgres-legado.sql").readAllBytes(), StandardCharsets.UTF_8);
        try (var c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var s = c.createStatement()) {
            s.execute(sql);
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
     * Vendas antigas, ambas em 10/05/2024: #1 Pix R$ 23 (Bóton Capivara x3,
     * Adesivo Capivara Grande x1) e #2 Outro "Vale" R$ 5 (Bóton Logo LAC x1).
     */
    @ParameterizedTest(name = "forma={0} inicio={1} fim={2} produto={3}")
    @CsvSource({
            // vazio
            ",,,,                                  2, 5, 28",
            // só data
            ",2024-05-10,2024-05-10,,              2, 5, 28",
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
        assertEquals(4, jdbc.queryForObject("select count(*) from flyway_schema_history where type = 'SQL' and success", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from item_venda where venda_id = 1", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from promocao_item where promocao_id is not null", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from information_schema.tables where table_name in ('venda_itens', 'promocao_itens')", Integer.class));
        assertNull(jdbc.queryForObject("select valor_cobrado from venda where id = 1", Object.class));
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
                        .param("qtd_3", "2").param("formaPagamento", "PIX"))
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
