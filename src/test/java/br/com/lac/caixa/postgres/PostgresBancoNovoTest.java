package br.com.lac.caixa.postgres;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.repositorio.VendaRepository;
import br.com.lac.caixa.servico.CaixaService;
import br.com.lac.caixa.servico.ConsultaHistorico;
import br.com.lac.caixa.servico.FechamentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Banco vazio: Flyway roda todas as migrações e o Hibernate valida o esquema no Postgres. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"spring.profiles.active=test"})
@AutoConfigureMockMvc
class PostgresBancoNovoTest {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        POSTGRES.start();
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired CaixaService caixa;
    @Autowired VendaRepository vendas;
    @Autowired ConsultaHistorico historico;
    @Autowired FechamentoService fechamento;
    static boolean vendasCriadas;

    /**
     * Duas vendas feitas pela aplicação sobre o estoque semeado:
     * 01/06/2024 Pix R$ 12 (Bóton Capivara x2) e 02/06/2024 Dinheiro R$ 14
     * (Adesivo Capivara Grande x1 + Bóton Logo LAC x1).
     */
    @BeforeEach void criarVendas() {
        if (vendasCriadas) return;
        vendasCriadas = true;
        var a = caixa.registrarVenda(Map.of(1L, 2), FormaPagamento.PIX, null, null, null, UUID.randomUUID().toString()).venda();
        a.setDataHora(LocalDate.of(2024, 6, 1).atTime(10, 0)); vendas.saveAndFlush(a);
        var b = caixa.registrarVenda(Map.of(3L, 1, 2L, 1), FormaPagamento.DINHEIRO, null, null, null, UUID.randomUUID().toString()).venda();
        b.setDataHora(LocalDate.of(2024, 6, 2).atTime(23, 59)); vendas.saveAndFlush(b);
    }

    @ParameterizedTest(name = "forma={0} inicio={1} fim={2} produto={3}")
    @CsvSource({
            ",,,,                                     2, 4, 26",
            ",2024-06-01,2024-06-01,,                 1, 2, 12",
            ",2024-06-02,,,                           1, 2, 14",
            ",,2024-06-01,,                           1, 2, 12",
            "DINHEIRO,,,,                             1, 2, 14",
            "CREDITO,,,,                              0, 0, 0",
            ",,,logo,                                 1, 2, 14",
            ",,,capivara,                             2, 4, 26",
            "PIX,2024-06-01,2024-06-02,capivara,      1, 2, 12",
            "DINHEIRO,2024-06-01,2024-06-01,,         0, 0, 0"})
    void filtrosNoBancoNovo(String forma, String inicio, String fim, String produto,
                            long vendas, long itens, String total) throws Exception {
        ConferenciaDeFiltros.conferir(mvc, historico, fechamento, forma, inicio, fim, produto, vendas, itens, total);
    }

    @Test void migraDoZeroEValidaComHibernate() throws Exception {
        assertEquals(5, jdbc.queryForObject("select count(*) from flyway_schema_history where type = 'SQL' and success", Integer.class));
        assertEquals(3, jdbc.queryForObject("select count(*) from produto", Integer.class));
        mvc.perform(get("/historico").sessionAttr("autenticado", true)).andExpect(status().isOk());
    }
}
