package br.com.lac.caixa.postgres;

import org.junit.jupiter.api.Test;
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
@SpringBootTest(properties = {"spring.profiles.active=test", "app.senha=teste"})
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
