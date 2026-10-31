package br.com.lac.caixa.postgres;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lac.caixa.migracao.Migracoes;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.repositorio.MovimentoEstoqueRepository;
import br.com.lac.caixa.servico.ConsultaHistorico;
import br.com.lac.caixa.servico.FechamentoService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

/** O caminho da produção: banco da versão original migrado no Postgres sem perder dados. */
@ExtendWith(ExigeDocker.class)
@SpringBootTest(properties = {"spring.profiles.active=test"})
@AutoConfigureMockMvc
class PostgresLegadoTest {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final Map<FormaPagamento, BigDecimal> ANTES = new EnumMap<>(FormaPagamento.class);

    record VendaAntiga(long id, String forma, BigDecimal valor, boolean cancelada) {}

    static final List<VendaAntiga> VENDAS_ANTES = new ArrayList<>();

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) throws Exception {
        POSTGRES.start();
        String sql = new String(
                PostgresLegadoTest.class
                        .getResourceAsStream("/db/postgres-legado.sql")
                        .readAllBytes(),
                StandardCharsets.UTF_8);
        try (var c = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var s = c.createStatement()) {
            s.execute(sql);
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .baselineOnMigrate(true)
                .target("5")
                .load()
                .migrate();
        try (var c = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var s = c.createStatement()) {
            s.execute(
                    "insert into venda(data_hora, forma_pagamento, valor_total) values ('2024-05-10 18:00', 'DINHEIRO', 0)");
            s.execute(
                    "insert into venda(data_hora, forma_pagamento, valor_total, cancelada, data_cancelamento, motivo_cancelamento)"
                            + " values ('2024-05-10 19:00', 'PIX', 7, true, '2024-05-10 19:05', 'Teste')");
            try (var r = s.executeQuery(
                    "select forma_pagamento, count(*), sum(coalesce(valor_cobrado, valor_total)) from venda"
                            + " where not cancelada group by forma_pagamento")) {
                while (r.next()) ANTES.put(FormaPagamento.valueOf(r.getString(1)), r.getBigDecimal(3));
            }
            try (var r = s.executeQuery("select id, forma_pagamento, coalesce(valor_cobrado, valor_total), cancelada"
                    + " from venda order by id")) {
                while (r.next()) {
                    VENDAS_ANTES.add(
                            new VendaAntiga(r.getLong(1), r.getString(2), r.getBigDecimal(3), r.getBoolean(4)));
                }
            }
        }
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ConsultaHistorico historico;

    @Autowired
    FechamentoService fechamento;

    @Autowired
    MovimentoEstoqueRepository movimentos;

    @ParameterizedTest(name = "forma={0} inicio={1} fim={2} produto={3}")
    @CsvSource({
        ",,,,                                  3, 5, 28",
        ",2024-05-10,2024-05-10,,              3, 5, 28",
        ",2024-05-11,,,                        0, 0, 0",
        ",,2024-05-09,,                        0, 0, 0",
        "PIX,,,,                               1, 4, 23",
        "OUTRO,,,,                             1, 1, 5",
        "DEBITO,,,,                            0, 0, 0",
        ",,,CAPIVARA,                          1, 4, 23",
        ",,,logo,                              1, 1, 5",
        ",,,bóton,                             2, 5, 28",
        ",,,boton,                             2, 5, 28",
        ",,,BOTON LOGO,                        1, 1, 5",
        ",,,adesivo capivára,                  1, 4, 23",
        ",,,%,                                 0, 0, 0",
        "PIX,2024-05-10,2024-05-10,capivara,   1, 4, 23",
        "PIX,2024-05-10,2024-05-10,logo,       0, 0, 0",
        "OUTRO,2024-05-01,2024-05-31,lac,      1, 1, 5"
    })
    void filtrosNoBancoLegado(
            String forma, String inicio, String fim, String produto, long vendas, long itens, String total)
            throws Exception {
        ConferenciaDeFiltros.conferir(mvc, historico, fechamento, forma, inicio, fim, produto, vendas, itens, total);
    }

    @Test
    @DisplayName("Banco legado: baseline, V2-V9 aplicadas e vínculos de itens e promoções preservados")
    void migracaoPreservaVendasEVinculos() {
        assertEquals(
                1,
                jdbc.queryForObject(
                        "select count(*) from flyway_schema_history where type = 'BASELINE'", Integer.class));
        assertEquals(
                Migracoes.quantidadePostgres() - 1,
                jdbc.queryForObject(
                        "select count(*) from flyway_schema_history where type = 'SQL' and success", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from item_venda where venda_id = 1", Integer.class));
        assertEquals(
                2,
                jdbc.queryForObject("select count(*) from promocao_item where promocao_id is not null", Integer.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "select count(*) from information_schema.tables where table_name in ('venda_itens', 'promocao_itens')",
                        Integer.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "select count(*) from item_venda where venda_id <= 4 and custo_unitario is not null",
                        Integer.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "select count(*) from venda v where v.rateio_retroativo and v.valor_cobrado <>"
                                + " (select sum(i.valor_liquido) from item_venda i where i.venda_id = v.id)",
                        Integer.class));
        assertEquals(
                List.of(1L, 2L),
                jdbc.queryForList("select id from venda where rateio_retroativo order by id", Long.class));
        assertEquals(
                0,
                new BigDecimal("23.00")
                        .compareTo(jdbc.queryForObject(
                                "select sum(valor_liquido) from item_venda where venda_id = 1", BigDecimal.class)));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "select count(*) from pagamento_venda where venda_id <= 4 and (taxa_registrada or taxa_estimada <> 0)",
                        Integer.class));
        assertEquals(
                0,
                new BigDecimal("23.00")
                        .compareTo(
                                jdbc.queryForObject("select valor_cobrado from venda where id = 1", BigDecimal.class)));
        assertEquals(
                0,
                new BigDecimal("23.00")
                        .compareTo(jdbc.queryForObject(
                                "select valor_calculado from venda where id = 1", BigDecimal.class)));
    }

    @Test
    @DisplayName(
            "Depois da V7: mesmas vendas, uma linha de pagamento por venda antiga e fechamento por forma igual ao de antes")
    void v7MantemVendasEFechamentoDeAntes() {
        var idsAntigos = VENDAS_ANTES.stream().map(VendaAntiga::id).toList();
        long maiorId = idsAntigos.stream().mapToLong(Long::longValue).max().orElseThrow();
        var depois = fechamento.consultar(null, LocalDate.of(2024, 12, 31));

        assertEquals(
                0,
                jdbc.queryForObject(
                        "select count(*) from information_schema.columns where table_name = 'venda'"
                                + " and column_name in ('forma_pagamento', 'forma_pagamento_outro', 'valor_total')",
                        Integer.class));
        assertEquals(
                VENDAS_ANTES.size(),
                jdbc.queryForObject("select count(*) from venda where id <= ?", Integer.class, maiorId));
        for (VendaAntiga antes : VENDAS_ANTES) {
            BigDecimal cobrado =
                    jdbc.queryForObject("select valor_cobrado from venda where id = ?", BigDecimal.class, antes.id());
            assertEquals(0, antes.valor().compareTo(cobrado), "venda " + antes.id());
            int linhas = jdbc.queryForObject(
                    "select count(*) from pagamento_venda where venda_id = ?", Integer.class, antes.id());
            int linhasIguais = jdbc.queryForObject(
                    "select count(*) from pagamento_venda where venda_id = ? and forma = ? and valor = ?",
                    Integer.class,
                    antes.id(),
                    antes.forma(),
                    antes.valor());
            if (antes.valor().signum() > 0) {
                assertEquals(1, linhas, "venda " + antes.id());
                assertEquals(1, linhasIguais, "venda " + antes.id());
            } else {
                assertEquals(0, linhas, "cortesia " + antes.id());
            }
        }
        assertEquals(
                "Vale", jdbc.queryForObject("select descricao from pagamento_venda where venda_id = 2", String.class));
        for (FormaPagamento forma : FormaPagamento.values()) {
            BigDecimal antes = ANTES.getOrDefault(forma, BigDecimal.ZERO);
            BigDecimal agora = depois.formas().stream()
                    .filter(l -> l.forma() == forma)
                    .findFirst()
                    .orElseThrow()
                    .total();
            assertEquals(0, antes.compareTo(agora), forma + ": antes " + antes + ", depois " + agora);
        }
        BigDecimal totalAntes = ANTES.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, totalAntes.compareTo(depois.total()));
        assertEquals(VENDAS_ANTES.stream().filter(v -> !v.cancelada()).count(), depois.quantidade());
        assertTrue(depois.isConfere());
        assertEquals(1, depois.cortesias());
        assertEquals(0, BigDecimal.ZERO.compareTo(depois.ajustes()), "ajuste das vendas antigas continua zero");
    }

    @Test
    void historicoEFechamentoComFiltrosVazios() throws Exception {
        for (String url : new String[] {
            "/historico",
            "/historico?forma=PIX",
            "/historico?inicio=2020-01-01",
            "/historico?fim=2040-01-01",
            "/historico?produto=b%C3%B3ton",
            "/historico?pagina=1",
            "/historico/exportar.csv",
            "/fechamento?inicio=2020-01-01",
            "/fechamento/exportar.csv?fim=2040-01-01"
        }) {
            mvc.perform(get(url).sessionAttr("autenticado", true)).andExpect(status().isOk());
        }
        mvc.perform(get("/historico").sessionAttr("autenticado", true))
                .andExpect(content().string(containsString("23,00")))
                .andExpect(content().string(containsString("Rateio retroativo")));
        mvc.perform(get("/historico/exportar.csv").param("forma", "OUTRO").sessionAttr("autenticado", true))
                .andExpect(content().string(containsString("\"Vale\"")));
    }

    @Test
    void vendaECancelamentoNoBancoMigrado() throws Exception {
        mvc.perform(post("/venda/previa").sessionAttr("autenticado", true).param("qtd_1", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(15.0));
        int antes = jdbc.queryForObject("select quantidade from produto where id = 3", Integer.class);
        String token = UUID.randomUUID().toString();
        mvc.perform(post("/venda")
                        .sessionAttr("autenticado", true)
                        .param("token", token)
                        .param("qtd_3", "2")
                        .param("pag_forma_0", "PIX"))
                .andExpect(redirectedUrl("/historico"));
        long id = jdbc.queryForObject("select id from venda where token = ?", Long.class, token);
        assertTrue(id > 2, "a identidade continua depois das vendas antigas");
        assertEquals(
                0,
                new java.math.BigDecimal("14")
                        .compareTo(jdbc.queryForObject(
                                "select valor_cobrado from venda where id = ?", java.math.BigDecimal.class, id)));
        assertEquals(antes - 2, jdbc.queryForObject("select quantidade from produto where id = 3", Integer.class));
        assertEquals(
                0,
                new BigDecimal("14")
                        .compareTo(jdbc.queryForObject(
                                "select sum(valor_liquido) from item_venda where venda_id = ?", BigDecimal.class, id)));
        assertTrue(!jdbc.queryForObject("select rateio_retroativo from venda where id = ?", Boolean.class, id));
        mvc.perform(post("/historico/" + id + "/cancelar")
                        .sessionAttr("autenticado", true)
                        .param("motivo", "Ensaio"))
                .andExpect(redirectedUrl("/historico"));
        assertEquals(antes, jdbc.queryForObject("select quantidade from produto where id = 3", Integer.class));
        assertTrue(jdbc.queryForObject("select cancelada from venda where id = ?", Boolean.class, id));
        mvc.perform(post("/estoque/compra")
                        .sessionAttr("autenticado", true)
                        .param("produtoId", "3")
                        .param("quantidade", "4")
                        .param("custoTotal", "10,00"))
                .andExpect(redirectedUrl("/estoque"));
        assertEquals(List.of(), movimentos.produtosComSaldoDivergente());
        assertEquals(
                List.of("ENTRADA_AJUSTE", "SAIDA_VENDA", "DEVOLUCAO_CANCELAMENTO", "ENTRADA_COMPRA"),
                jdbc.queryForList("select tipo from movimento_estoque where produto_id = 3 order by id", String.class));
    }

    @Test
    @DisplayName("V11: cada produto antigo com estoque ganha um movimento \"saldo inicial\" igual à quantidade")
    void saldoInicialDaV11() {
        assertEquals(
                0,
                jdbc.queryForObject(
                        "select count(*) from produto p where p.quantidade > 0 and p.quantidade <>"
                                + " (select coalesce(sum(m.quantidade), 0) from movimento_estoque m"
                                + " where m.produto_id = p.id and m.observacao = 'saldo inicial')",
                        Integer.class));
    }
}
