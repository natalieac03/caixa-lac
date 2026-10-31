package br.com.lac.caixa.migracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.lac.caixa.util.Rateio;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A V12 rateia as vendas antigas igual ao Rateio.java. */
class RateioMigradoTest {

    String banco() {
        return "jdbc:h2:mem:rateio_migrado_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    }

    Flyway flyway(String url, String alvo) {
        var config = Flyway.configure().dataSource(url, "sa", "");
        return (alvo == null ? config : config.target(alvo)).load();
    }

    void executar(Connection c, String sql, Object... parametros) throws Exception {
        try (var s = c.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) s.setObject(i + 1, parametros[i]);
            s.execute();
        }
    }

    long produto(Connection c) throws Exception {
        executar(c, "insert into produto(nome, ativo, preco, quantidade) values ('Antigo', true, 6, 0)");
        try (var s = c.createStatement();
                var r = s.executeQuery("select max(id) from produto")) {
            r.next();
            return r.getLong(1);
        }
    }

    long vendaAntiga(Connection c, long produto, long cobrado, long[][] itens) throws Exception {
        executar(
                c,
                "insert into venda(data_hora, valor_cobrado, valor_calculado, cancelada) values (current_timestamp, ?, ?, false)",
                BigDecimal.valueOf(cobrado, 2),
                BigDecimal.valueOf(cobrado, 2));
        long id;
        try (var s = c.createStatement();
                var r = s.executeQuery("select max(id) from venda")) {
            r.next();
            id = r.getLong(1);
        }
        for (long[] item : itens) {
            executar(
                    c,
                    "insert into item_venda(produto_id, quantidade, preco_unitario, venda_id) values (?, ?, ?, ?)",
                    produto,
                    (int) item[1],
                    BigDecimal.valueOf(item[0], 2),
                    id);
        }
        return id;
    }

    record Item(long bruto, long desconto, long liquido) {}

    List<Item> itens(Connection c, long venda) throws Exception {
        List<Item> lista = new ArrayList<>();
        try (var s = c.prepareStatement(
                "select valor_bruto, desconto_rateado, valor_liquido from item_venda where venda_id = ? order by id")) {
            s.setLong(1, venda);
            try (var r = s.executeQuery()) {
                while (r.next()) {
                    lista.add(new Item(
                            Rateio.centavos(r.getBigDecimal(1)),
                            Rateio.centavos(r.getBigDecimal(2)),
                            Rateio.centavos(r.getBigDecimal(3))));
                }
            }
        }
        return lista;
    }

    boolean retroativo(Connection c, long venda) throws Exception {
        try (var s = c.prepareStatement("select rateio_retroativo from venda where id = ?")) {
            s.setLong(1, venda);
            try (var r = s.executeQuery()) {
                r.next();
                return r.getBoolean(1);
            }
        }
    }

    @Test
    @DisplayName("Propriedade: 400 vendas antigas aleatórias (semente fixa) migram igual ao Rateio.java")
    void vendasAntigasAleatoriasMigramIgualAoRateio() throws Exception {
        String url = banco();
        flyway(url, "11").migrate();
        Random aleatorio = new Random(777L);
        List<Long> vendas = new ArrayList<>();
        List<long[][]> itensDasVendas = new ArrayList<>();
        List<Long> cobrados = new ArrayList<>();
        long semItens;
        try (var c = DriverManager.getConnection(url, "sa", "")) {
            long produto = produto(c);
            for (int k = 0; k < 400; k++) {
                int n = 1 + aleatorio.nextInt(7);
                long[][] itens = new long[n][];
                long soma = 0;
                for (int i = 0; i < n; i++) {
                    itens[i] = new long[] {1 + aleatorio.nextInt(5000), 1 + aleatorio.nextInt(4)};
                    soma += itens[i][0] * itens[i][1];
                }
                long cobrado = switch (aleatorio.nextInt(5)) {
                    case 0 -> 0;
                    case 1 -> soma;
                    case 2 -> soma + aleatorio.nextInt(1000);
                    default -> (long) (soma * aleatorio.nextDouble());
                };
                vendas.add(vendaAntiga(c, produto, cobrado, itens));
                itensDasVendas.add(itens);
                cobrados.add(cobrado);
            }
            semItens = vendaAntiga(c, produto, 700, new long[0][]);
        }

        flyway(url, null).migrate();

        try (var c = DriverManager.getConnection(url, "sa", "")) {
            for (int k = 0; k < vendas.size(); k++) {
                long[][] itens = itensDasVendas.get(k);
                long[] brutos = new long[itens.length];
                long[] quantidades = new long[itens.length];
                long soma = 0;
                for (int i = 0; i < itens.length; i++) {
                    brutos[i] = itens[i][0] * itens[i][1];
                    quantidades[i] = itens[i][1];
                    soma += brutos[i];
                }
                long[] esperado = Rateio.ratear(soma - cobrados.get(k), brutos, quantidades);
                List<Item> migrados = itens(c, vendas.get(k));
                long somaLiquidos = 0;
                for (int i = 0; i < itens.length; i++) {
                    Item m = migrados.get(i);
                    assertEquals(brutos[i], m.bruto(), "venda " + vendas.get(k) + " item " + i);
                    assertEquals(esperado[i], m.desconto(), "venda " + vendas.get(k) + " item " + i);
                    assertEquals(m.bruto() - m.desconto(), m.liquido());
                    somaLiquidos += m.liquido();
                }
                assertEquals(cobrados.get(k), somaLiquidos, "venda " + vendas.get(k));
                assertTrue(retroativo(c, vendas.get(k)), "venda " + vendas.get(k));
            }
            assertFalse(retroativo(c, semItens));
        }
    }

    @Test
    @DisplayName("Casos fixos: 1 item, dízima, ajuste para mais e cortesia")
    void casosFixos() throws Exception {
        String url = banco();
        flyway(url, "11").migrate();
        long umItem, dizima, mais, cortesia;
        try (var c = DriverManager.getConnection(url, "sa", "")) {
            long p = produto(c);
            umItem = vendaAntiga(c, p, 1500, new long[][] {{600, 3}});
            dizima = vendaAntiga(c, p, 1700, new long[][] {{600, 1}, {600, 1}, {600, 1}});
            mais = vendaAntiga(c, p, 2000, new long[][] {{600, 1}, {1200, 1}});
            cortesia = vendaAntiga(c, p, 0, new long[][] {{600, 2}, {800, 1}});
        }

        flyway(url, null).migrate();

        try (var c = DriverManager.getConnection(url, "sa", "")) {
            assertEquals(List.of(new Item(1800, 300, 1500)), itens(c, umItem));
            assertEquals(
                    List.of(new Item(600, 34, 566), new Item(600, 33, 567), new Item(600, 33, 567)), itens(c, dizima));
            assertEquals(List.of(new Item(600, -67, 667), new Item(1200, -133, 1333)), itens(c, mais));
            assertEquals(List.of(new Item(1200, 1200, 0), new Item(800, 800, 0)), itens(c, cortesia));
        }
    }

    @Test
    @DisplayName("Item antigo sem preço unitário: a V12 para com mensagem (não inventa preço)")
    void semPrecoPara() throws Exception {
        String url = banco();
        flyway(url, "11").migrate();
        try (var c = DriverManager.getConnection(url, "sa", "")) {
            long p = produto(c);
            long v = vendaAntiga(c, p, 600, new long[0][]);
            executar(
                    c,
                    "insert into item_venda(produto_id, quantidade, preco_unitario, venda_id) values (?, 1, null, ?)",
                    p,
                    v);
        }

        var erro = assertThrows(FlywayException.class, () -> flyway(url, null).migrate());

        assertTrue(erro.getMessage().contains("V12 parada"), erro.getMessage());
    }
}
