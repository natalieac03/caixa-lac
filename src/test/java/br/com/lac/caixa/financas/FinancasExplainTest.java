package br.com.lac.caixa.financas;

import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.lac.caixa.postgres.ExigeDocker;
import br.com.lac.caixa.repositorio.ConsultasFinanceiras;
import br.com.lac.caixa.repositorio.FiltroFinancas;
import br.com.lac.caixa.repositorio.PeriodoDoEvento;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** EXPLAIN das consultas das finanças no Postgres com volume: usam os índices. */
@ExtendWith(ExigeDocker.class)
@SpringBootTest(properties = {"spring.profiles.active=test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FinancasExplainTest {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        POSTGRES.start();
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbc;

    static class Espiao extends NamedParameterJdbcTemplate {
        final List<String> planos = new ArrayList<>();
        private final NamedParameterJdbcTemplate real;

        Espiao(DataSource ds) {
            super(ds);
            real = new NamedParameterJdbcTemplate(ds);
        }

        private void explicar(String sql, SqlParameterSource params) {
            planos.add(real.queryForList("EXPLAIN " + sql, params).stream()
                    .map(l -> String.valueOf(l.values().iterator().next()))
                    .collect(Collectors.joining("\n")));
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource params, RowMapper<T> mapper) {
            explicar(sql, params);
            return List.of();
        }

        @Override
        public <T> T queryForObject(String sql, SqlParameterSource params, RowMapper<T> mapper) {
            explicar(sql, params);
            return null;
        }

        @Override
        public <T> T queryForObject(String sql, SqlParameterSource params, Class<T> tipo) {
            explicar(sql, params);
            return null;
        }
    }

    @BeforeAll
    void volume() {
        jdbc.execute(
                "INSERT INTO categoria (nome, nome_normalizado) SELECT 'Cat ' || g, 'cat ' || g FROM generate_series(1, 5) g");
        jdbc.execute("INSERT INTO produto (nome, ativo, preco, quantidade, custo_unitario, categoria_id)"
                + " SELECT 'Produto ' || g, TRUE, 5 + g % 40, 100, 1 + g % 10,"
                + " (SELECT min(id) FROM categoria) + g % 5 FROM generate_series(1, 300) g");
        jdbc.execute("INSERT INTO venda (data_hora, valor_cobrado, valor_calculado, desconto, cancelada,"
                + " data_cancelamento, motivo_cancelamento)"
                + " SELECT TIMESTAMP '2024-01-01 09:00' + (g % 730) * INTERVAL '1 day' + (g % 9) * INTERVAL '1 hour',"
                + " 20, 20, 0, g % 33 = 0, CASE WHEN g % 33 = 0 THEN TIMESTAMP '2026-01-01' END,"
                + " CASE WHEN g % 33 = 0 THEN 'volume' END FROM generate_series(1, 60000) g");
        jdbc.execute("INSERT INTO item_venda (venda_id, produto_id, quantidade, preco_unitario, valor_bruto,"
                + " desconto_rateado, valor_liquido, custo_unitario)"
                + " SELECT v.id, (SELECT min(id) FROM produto) + (v.id * k) % 300, 1, 10, 10, 0, 10, 3"
                + " FROM venda v CROSS JOIN generate_series(1, 2) k");
        jdbc.execute("INSERT INTO pagamento_venda (venda_id, forma, valor) SELECT id,"
                + " CASE WHEN id % 2 = 0 THEN 'CREDITO' ELSE 'DINHEIRO' END, 20 FROM venda");
        jdbc.execute(
                "INSERT INTO venda_desconto (venda_id, descricao, valor) SELECT id, 'volume', 1 FROM venda WHERE id % 10 = 0");
        jdbc.execute("ANALYZE");
    }

    Map<String, String> planos(FiltroFinancas f) {
        var espiao = new Espiao(dataSource);
        var c = new ConsultasFinanceiras(espiao);
        Map<String, Runnable> consultas = new LinkedHashMap<>();
        consultas.put("somaItens", () -> c.somaItens(f));
        consultas.put("somaVendas", () -> c.somaVendas(f));
        consultas.put("taxas", () -> c.taxas(f));
        consultas.put("porDiaEHora", () -> c.porDiaEHora(f));
        consultas.put("porProduto", () -> c.porProduto(f));
        consultas.put("porCategoria", () -> c.porCategoria(f));
        consultas.put("porForma", () -> c.porForma(f));
        consultas.put("porPromocao", () -> c.porPromocao(f));
        consultas.put("estoque", () -> c.estoque(f));
        consultas.put("parados", () -> c.parados(f));
        Map<String, String> resultado = new LinkedHashMap<>();
        consultas.forEach((nome, consulta) -> {
            espiao.planos.clear();
            consulta.run();
            resultado.put(nome, String.join("\n---\n", espiao.planos));
        });
        return resultado;
    }

    @Test
    @DisplayName("EXPLAIN: um dia usa os índices de data, itens e pagamentos; parados de um ano usa o de produto")
    void explain() throws Exception {
        var dia = FiltroFinancas.doEvento(new PeriodoDoEvento(
                0L, "Dia", LocalDateTime.of(2025, 3, 1, 9, 0), LocalDateTime.of(2025, 3, 1, 18, 0)));
        var ano = FiltroFinancas.periodo(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
        var curto = planos(dia);
        var longo = planos(ano);
        var relatorio = new StringBuilder();
        curto.forEach((nome, plano) -> relatorio
                .append("== ")
                .append(nome)
                .append(" (um dia)\n")
                .append(plano)
                .append("\n\n"));
        longo.forEach((nome, plano) -> relatorio
                .append("== ")
                .append(nome)
                .append(" (um ano)\n")
                .append(plano)
                .append("\n\n"));
        Files.writeString(Path.of("target", "explain-financas.txt"), relatorio, StandardCharsets.UTF_8);

        for (String nome : List.of("somaItens", "somaVendas", "taxas", "porDiaEHora", "porProduto", "porForma")) {
            assertTrue(curto.get(nome).contains("idx_venda_data_hora"), nome + ":\n" + curto.get(nome));
        }
        assertTrue(curto.get("somaItens").contains("idx_item_venda_venda"), curto.get("somaItens"));
        assertTrue(curto.get("porForma").contains("idx_pagamento_venda_venda"), curto.get("porForma"));
        assertTrue(longo.get("parados").contains("ix_item_venda_produto"), longo.get("parados"));
    }
}
