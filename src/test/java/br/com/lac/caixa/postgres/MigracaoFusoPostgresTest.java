package br.com.lac.caixa.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * V20 (só no Postgres) no banco legado: versão original migrada até a V19 com os
 * horários gravados em UTC, e então o resto das migrações, com as mesmas pastas
 * que o app usa. Depois, as colunas do relógio estão em Brasília e as digitadas
 * pela pessoa ficam como estavam.
 */
@ExtendWith(ExigeDocker.class)
class MigracaoFusoPostgresTest {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    /** As pastas do spring.flyway.locations, com {vendor} = postgresql. */
    static final String[] PASTAS = {"classpath:db/migration", "classpath:db/migration-postgresql"};

    static Connection c;
    static long noturna;
    static long cancelada;
    static String versaoFinal;

    @BeforeAll
    static void legadoMigradoComHorariosEmUtc() throws Exception {
        POSTGRES.start();
        c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        try (var s = c.createStatement()) {
            s.execute(new String(
                    MigracaoFusoPostgresTest.class
                            .getResourceAsStream("/db/postgres-legado.sql")
                            .readAllBytes(),
                    StandardCharsets.UTF_8));
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .baselineOnMigrate(true)
                .locations(PASTAS)
                .target("19")
                .load()
                .migrate();
        // Como o app em UTC gravava: 01:30 UTC de 11/05 = 22:30 de 10/05 em Brasília.
        noturna = venda("2024-05-11 01:30:00", null);
        cancelada = venda("2024-05-10 20:00:00", "2024-05-11 02:15:00");
        try (var s = c.createStatement()) {
            s.execute(
                    "INSERT INTO evento (nome, inicio, fim) VALUES ('Feira digitada', '2024-05-10 18:00', '2024-05-10 23:00')");
            s.execute(
                    "INSERT INTO despesa (data, descricao, categoria_id, valor, registrada_em)"
                            + " VALUES ('2024-05-10', 'Lona', (SELECT MIN(id) FROM categoria_despesa), 30, '2024-05-11 01:00')");
            s.execute("INSERT INTO compra_estoque (produto_id, quantidade, custo_total, data_compra, estoque_anterior,"
                    + " custo_novo, registrada_em) VALUES (1, 5, 10, '2024-05-10', 27, 2, '2024-05-11 00:10')");
            s.execute(
                    "INSERT INTO cobranca_pix (token, parte, valor, tentativa, order_id, status, valor_pago, criada_em,"
                            + " paga_em, resolvida_em) VALUES ('t', 0, 6, 1, 'ORD1', 'PAGA', 6, '2024-05-11 01:29', '2024-05-11 01:30',"
                            + " '2024-05-11 03:00')");
            s.execute("INSERT INTO taxa_pagamento (forma, percentual, valor_fixo, vigente_desde, registrada_em)"
                    + " VALUES ('PIX', 1, 0, '2024-05-10', '2024-05-11 00:05')");
            s.execute("UPDATE movimento_estoque SET data_hora = '2024-05-11 00:00' WHERE observacao = 'saldo inicial'");
        }

        // Act: o resto das migrações, como no deploy
        var resultado = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .baselineOnMigrate(true)
                .locations(PASTAS)
                .load()
                .migrate();
        versaoFinal = resultado.targetSchemaVersion;
    }

    static long venda(String dataHora, String canceladaEm) throws Exception {
        try (var s = c.prepareStatement(
                "INSERT INTO venda (data_hora, valor_cobrado, valor_calculado, desconto, cancelada, data_cancelamento,"
                        + " motivo_cancelamento) VALUES (CAST(? AS TIMESTAMP), 6, 6, 0, ?, CAST(? AS TIMESTAMP), ?) RETURNING id")) {
            s.setString(1, dataHora);
            s.setBoolean(2, canceladaEm != null);
            s.setString(3, canceladaEm);
            s.setString(4, canceladaEm == null ? null : "Ensaio");
            try (var r = s.executeQuery()) {
                r.next();
                return r.getLong(1);
            }
        }
    }

    @AfterAll
    static void fechar() throws Exception {
        c.close();
    }

    static LocalDateTime hora(String sql) throws Exception {
        try (var s = c.createStatement();
                var r = s.executeQuery(sql)) {
            r.next();
            return r.getObject(1, LocalDateTime.class);
        }
    }

    static LocalDate dia(String sql) throws Exception {
        try (var s = c.createStatement();
                var r = s.executeQuery(sql)) {
            r.next();
            return r.getObject(1, LocalDate.class);
        }
    }

    static LocalDateTime t(String texto) {
        return LocalDateTime.parse(texto);
    }

    @Test
    @DisplayName("Venda das 01:30 UTC vira 22:30 de 10/05 (dia certo); a V20 foi aplicada no Postgres")
    void vendas() throws Exception {
        try (var s = c.createStatement();
                var r = s.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '20' AND success")) {
            r.next();
            assertEquals(1, r.getInt(1));
        }
        assertEquals(t("2024-05-10T22:30"), hora("SELECT data_hora FROM venda WHERE id = " + noturna));
        assertEquals(t("2024-05-10T17:00"), hora("SELECT data_hora FROM venda WHERE id = " + cancelada));
        assertEquals(t("2024-05-10T23:15"), hora("SELECT data_cancelamento FROM venda WHERE id = " + cancelada));
        // As vendas da versão original (massa legada: 10/05 13:03:50 UTC) também passam a Brasília
        assertEquals(
                LocalDateTime.parse("2024-05-10T10:03:50.608709"), hora("SELECT data_hora FROM venda WHERE id = 1"));
    }

    @Test
    @DisplayName("Movimentos, compras, Pix, despesas e taxas: o registrado pelo relógio passa a Brasília")
    void colunasDoRelogio() throws Exception {
        assertEquals(t("2024-05-10T21:00"), hora("SELECT MIN(data_hora) FROM movimento_estoque"));
        assertEquals(t("2024-05-10T21:10"), hora("SELECT registrada_em FROM compra_estoque"));
        assertEquals(t("2024-05-10T22:29"), hora("SELECT criada_em FROM cobranca_pix"));
        assertEquals(t("2024-05-10T22:30"), hora("SELECT paga_em FROM cobranca_pix"));
        assertEquals(t("2024-05-11T00:00"), hora("SELECT resolvida_em FROM cobranca_pix"));
        assertEquals(t("2024-05-10T22:00"), hora("SELECT registrada_em FROM despesa"));
        assertEquals(t("2024-05-10T21:05"), hora("SELECT registrada_em FROM taxa_pagamento"));
    }

    @Test
    @DisplayName("O digitado pela pessoa não muda: evento do formulário e as datas (DATE)")
    void colunasDigitadas() throws Exception {
        assertEquals(t("2024-05-10T18:00"), hora("SELECT inicio FROM evento"));
        assertEquals(t("2024-05-10T23:00"), hora("SELECT fim FROM evento"));
        assertEquals(LocalDate.of(2024, 5, 10), dia("SELECT data FROM despesa"));
        assertEquals(LocalDate.of(2024, 5, 10), dia("SELECT data_compra FROM compra_estoque"));
        assertEquals(LocalDate.of(2024, 5, 10), dia("SELECT vigente_desde FROM taxa_pagamento"));
        // A venda das 22:30 cai na Feira das 18h às 23h; antes da proposta (01:30 de 11/05) não caía
        try (var s = c.createStatement();
                var r = s.executeQuery("SELECT COUNT(*) FROM venda v JOIN evento e ON v.data_hora >= e.inicio"
                        + " AND v.data_hora < e.fim WHERE v.id = " + noturna)) {
            r.next();
            assertEquals(1, r.getInt(1));
        }
    }
}
