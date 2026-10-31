package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.FormaPagamento;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** As agregações das finanças em SQL que roda igual no PostgreSQL e no H2. */
@Repository
public class ConsultasFinanceiras {

    private final NamedParameterJdbcTemplate jdbc;

    public ConsultasFinanceiras(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static final String DIA_A_DIA = " AND NOT EXISTS (SELECT 1 FROM evento ev WHERE ev.inicio <= v.data_hora"
            + " AND (ev.fim > v.data_hora OR (ev.fim IS NULL AND v.data_hora <= :agora"
            + " AND NOT EXISTS (SELECT 1 FROM evento prox WHERE prox.inicio > ev.inicio AND prox.inicio <= v.data_hora))))";

    static final String CMV =
            "CASE WHEN i.custo_unitario IS NOT NULL THEN ROUND(i.custo_unitario * i.quantidade, 2) END";

    private static String partes(FiltroFinancas f) {
        return f.forma() == null ? "" : " AND pg.forma = :forma";
    }

    private record Onde(String vendas, String itens, MapSqlParameterSource parametros) {}

    private static Onde onde(FiltroFinancas f) {
        var params =
                new MapSqlParameterSource().addValue("ini", f.inicioConsulta()).addValue("fim", f.fimConsulta());
        StringBuilder vendas = new StringBuilder("v.cancelada = FALSE AND v.data_hora >= :ini AND v.data_hora < :fim");
        String itens = "";
        if (f.forma() != null) {
            vendas.append(
                    " AND EXISTS (SELECT 1 FROM pagamento_venda pf WHERE pf.venda_id = v.id AND pf.forma = :forma)");
            params.addValue("forma", f.forma().name());
        }
        if (f.diaADia()) {
            vendas.append(DIA_A_DIA);
            params.addValue("agora", f.agora());
        }
        if (f.categoriaId() != null) {
            vendas.append(" AND EXISTS (SELECT 1 FROM item_venda ic JOIN produto pc ON pc.id = ic.produto_id"
                    + " WHERE ic.venda_id = v.id AND pc.categoria_id = :categoria)");
            itens = " AND p.categoria_id = :categoria";
            params.addValue("categoria", f.categoriaId());
        }
        return new Onde(vendas.toString(), itens, params);
    }

    private static BigDecimal dec(ResultSet r, String coluna) throws SQLException {
        BigDecimal v = r.getBigDecimal(coluna);
        return v == null ? BigDecimal.ZERO : v;
    }

    private static Long idOuNull(ResultSet r, String coluna) throws SQLException {
        long v = r.getLong(coluna);
        return r.wasNull() ? null : v;
    }

    public record SomaItens(
            BigDecimal bruto,
            BigDecimal liquido,
            BigDecimal cmv,
            BigDecimal liquidoComCusto,
            long linhasSemCusto,
            long unidades) {}

    public SomaItens somaItens(FiltroFinancas f) {
        Onde o = onde(f);
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(i.valor_bruto), 0) AS bruto, COALESCE(SUM(i.valor_liquido), 0) AS liquido,"
                        + " COALESCE(SUM(" + CMV + "), 0) AS cmv,"
                        + " COALESCE(SUM(CASE WHEN i.custo_unitario IS NOT NULL THEN i.valor_liquido END), 0) AS liquido_com_custo,"
                        + " COALESCE(SUM(CASE WHEN i.custo_unitario IS NULL THEN 1 ELSE 0 END), 0) AS linhas_sem_custo,"
                        + " COALESCE(SUM(i.quantidade), 0) AS unidades"
                        + " FROM item_venda i JOIN venda v ON v.id = i.venda_id JOIN produto p ON p.id = i.produto_id"
                        + " WHERE " + o.vendas() + o.itens(),
                o.parametros(),
                (r, n) -> new SomaItens(
                        dec(r, "bruto"),
                        dec(r, "liquido"),
                        dec(r, "cmv"),
                        dec(r, "liquido_com_custo"),
                        r.getLong("linhas_sem_custo"),
                        r.getLong("unidades")));
    }

    public record SomaVendas(
            long vendas,
            BigDecimal cobrado,
            BigDecimal descontos,
            BigDecimal ajustes,
            long cortesias,
            long semItens,
            BigDecimal cobradoSemItens) {}

    public SomaVendas somaVendas(FiltroFinancas f) {
        Onde o = onde(f);
        return jdbc.queryForObject(
                "SELECT COUNT(*) AS vendas, COALESCE(SUM(v.valor_cobrado), 0) AS cobrado,"
                        + " COALESCE(SUM(COALESCE(v.desconto, 0)), 0) AS descontos,"
                        + " COALESCE(SUM(v.valor_cobrado - COALESCE(v.valor_calculado, v.valor_cobrado)), 0) AS ajustes,"
                        + " COALESCE(SUM(CASE WHEN v.valor_cobrado = 0 THEN 1 ELSE 0 END), 0) AS cortesias,"
                        + " COALESCE(SUM(CASE WHEN NOT EXISTS (SELECT 1 FROM item_venda s WHERE s.venda_id = v.id)"
                        + " THEN 1 ELSE 0 END), 0) AS sem_itens,"
                        + " COALESCE(SUM(CASE WHEN NOT EXISTS (SELECT 1 FROM item_venda s WHERE s.venda_id = v.id)"
                        + " THEN v.valor_cobrado ELSE 0 END), 0) AS cobrado_sem_itens"
                        + " FROM venda v WHERE " + o.vendas(),
                o.parametros(),
                (r, n) -> new SomaVendas(
                        r.getLong("vendas"),
                        dec(r, "cobrado"),
                        dec(r, "descontos"),
                        dec(r, "ajustes"),
                        r.getLong("cortesias"),
                        r.getLong("sem_itens"),
                        dec(r, "cobrado_sem_itens")));
    }

    public BigDecimal taxas(FiltroFinancas f) {
        Onde o = onde(f);
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(pg.taxa_estimada), 0) FROM pagamento_venda pg JOIN venda v ON v.id = pg.venda_id"
                        + " WHERE " + o.vendas() + partes(f),
                o.parametros(),
                BigDecimal.class);
    }

    public BigDecimal despesas(FiltroFinancas f) {
        if (f.evento() != null) {
            return jdbc.queryForObject(
                    "SELECT COALESCE(SUM(valor), 0) FROM despesa WHERE evento_id = :evento",
                    new MapSqlParameterSource("evento", f.evento().id()),
                    BigDecimal.class);
        }
        if (f.diaADia()) {
            return jdbc.queryForObject(
                    "SELECT COALESCE(SUM(valor), 0) FROM despesa WHERE evento_id IS NULL AND data >= :de AND data <= :ate",
                    new MapSqlParameterSource().addValue("de", f.inicio()).addValue("ate", f.fim()),
                    BigDecimal.class);
        }
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(d.valor), 0) FROM despesa d LEFT JOIN evento e ON e.id = d.evento_id"
                        + " WHERE COALESCE(CAST(e.inicio AS DATE), d.data) >= :de"
                        + " AND COALESCE(CAST(e.inicio AS DATE), d.data) <= :ate",
                new MapSqlParameterSource().addValue("de", f.inicio()).addValue("ate", f.fim()),
                BigDecimal.class);
    }

    public record Balde(LocalDate dia, int hora, BigDecimal liquido, BigDecimal cmv, long vendas) {}

    public List<Balde> porDiaEHora(FiltroFinancas f) {
        Onde o = onde(f);
        return jdbc.query(
                "SELECT CAST(v.data_hora AS DATE) AS dia, EXTRACT(HOUR FROM v.data_hora) AS hora,"
                        + " COALESCE(SUM(i.valor_liquido), 0) AS liquido,"
                        + " COALESCE(SUM(" + CMV + "), 0) AS cmv,"
                        + " COUNT(DISTINCT v.id) AS vendas"
                        + " FROM item_venda i JOIN venda v ON v.id = i.venda_id JOIN produto p ON p.id = i.produto_id"
                        + " WHERE " + o.vendas() + o.itens()
                        + " GROUP BY CAST(v.data_hora AS DATE), EXTRACT(HOUR FROM v.data_hora)"
                        + " ORDER BY dia, hora",
                o.parametros(),
                (r, n) -> new Balde(
                        r.getObject("dia", LocalDate.class),
                        r.getInt("hora"),
                        dec(r, "liquido"),
                        dec(r, "cmv"),
                        r.getLong("vendas")));
    }

    public record SomaProduto(
            Long produtoId,
            String nome,
            boolean ativo,
            Long categoriaId,
            String categoria,
            long unidades,
            BigDecimal liquido,
            BigDecimal cmv,
            long unidadesSemCusto) {}

    public List<SomaProduto> porProduto(FiltroFinancas f) {
        Onde o = onde(f);
        return jdbc.query(
                "SELECT p.id, p.nome, p.ativo, p.categoria_id, c.nome AS categoria,"
                        + " SUM(i.quantidade) AS unidades, COALESCE(SUM(i.valor_liquido), 0) AS liquido,"
                        + " COALESCE(SUM(" + CMV + "), 0) AS cmv,"
                        + " COALESCE(SUM(CASE WHEN i.custo_unitario IS NULL THEN i.quantidade ELSE 0 END), 0) AS sem_custo"
                        + " FROM item_venda i JOIN venda v ON v.id = i.venda_id JOIN produto p ON p.id = i.produto_id"
                        + " LEFT JOIN categoria c ON c.id = p.categoria_id"
                        + " WHERE " + o.vendas() + o.itens()
                        + " GROUP BY p.id, p.nome, p.ativo, p.categoria_id, c.nome",
                o.parametros(),
                (r, n) -> new SomaProduto(
                        r.getLong("id"),
                        r.getString("nome"),
                        r.getBoolean("ativo"),
                        idOuNull(r, "categoria_id"),
                        r.getString("categoria"),
                        r.getLong("unidades"),
                        dec(r, "liquido"),
                        dec(r, "cmv"),
                        r.getLong("sem_custo")));
    }

    public record SomaCategoria(
            Long categoriaId,
            String categoria,
            long unidades,
            BigDecimal liquido,
            BigDecimal cmv,
            long unidadesSemCusto,
            long vendas) {}

    public List<SomaCategoria> porCategoria(FiltroFinancas f) {
        Onde o = onde(f);
        return jdbc.query(
                "SELECT p.categoria_id, c.nome AS categoria,"
                        + " SUM(i.quantidade) AS unidades, COALESCE(SUM(i.valor_liquido), 0) AS liquido,"
                        + " COALESCE(SUM(" + CMV + "), 0) AS cmv,"
                        + " COALESCE(SUM(CASE WHEN i.custo_unitario IS NULL THEN i.quantidade ELSE 0 END), 0) AS sem_custo,"
                        + " COUNT(DISTINCT v.id) AS vendas"
                        + " FROM item_venda i JOIN venda v ON v.id = i.venda_id JOIN produto p ON p.id = i.produto_id"
                        + " LEFT JOIN categoria c ON c.id = p.categoria_id"
                        + " WHERE " + o.vendas() + o.itens()
                        + " GROUP BY p.categoria_id, c.nome",
                o.parametros(),
                (r, n) -> new SomaCategoria(
                        idOuNull(r, "categoria_id"),
                        r.getString("categoria"),
                        r.getLong("unidades"),
                        dec(r, "liquido"),
                        dec(r, "cmv"),
                        r.getLong("sem_custo"),
                        r.getLong("vendas")));
    }

    public record SomaForma(FormaPagamento forma, BigDecimal valor, long vendas, BigDecimal taxas) {}

    public List<SomaForma> porForma(FiltroFinancas f) {
        Onde o = onde(f);
        return jdbc.query(
                "SELECT pg.forma, COALESCE(SUM(pg.valor), 0) AS valor, COUNT(DISTINCT pg.venda_id) AS vendas,"
                        + " COALESCE(SUM(pg.taxa_estimada), 0) AS taxas"
                        + " FROM pagamento_venda pg JOIN venda v ON v.id = pg.venda_id"
                        + " WHERE " + o.vendas() + partes(f) + " GROUP BY pg.forma",
                o.parametros(),
                (r, n) -> new SomaForma(
                        FormaPagamento.valueOf(r.getString("forma")),
                        dec(r, "valor"),
                        r.getLong("vendas"),
                        dec(r, "taxas")));
    }

    public record SomaPromocao(Long promocaoId, long vendas, BigDecimal desconto, BigDecimal receita) {}

    public List<SomaPromocao> porPromocao(FiltroFinancas f) {
        Onde o = onde(f);
        String receita = f.categoriaId() == null
                ? " JOIN (SELECT x.promocao_id, SUM(v.valor_cobrado) AS receita"
                        + "  FROM (SELECT DISTINCT promocao_id, venda_id FROM venda_desconto) x"
                        + "  JOIN venda v ON v.id = x.venda_id WHERE " + o.vendas()
                        + "  GROUP BY x.promocao_id) r"
                : " JOIN (SELECT x.promocao_id, SUM(i.valor_liquido) AS receita"
                        + "  FROM (SELECT DISTINCT promocao_id, venda_id FROM venda_desconto) x"
                        + "  JOIN venda v ON v.id = x.venda_id JOIN item_venda i ON i.venda_id = v.id"
                        + "  JOIN produto p ON p.id = i.produto_id WHERE " + o.vendas() + o.itens()
                        + "  GROUP BY x.promocao_id) r";
        return jdbc.query(
                "SELECT d.promocao_id, d.vendas, d.desconto, r.receita FROM"
                        + " (SELECT vd.promocao_id, COUNT(DISTINCT vd.venda_id) AS vendas, SUM(vd.valor) AS desconto"
                        + "  FROM venda_desconto vd JOIN venda v ON v.id = vd.venda_id WHERE " + o.vendas()
                        + "  GROUP BY vd.promocao_id) d"
                        + receita
                        + " ON r.promocao_id = d.promocao_id OR (r.promocao_id IS NULL AND d.promocao_id IS NULL)",
                o.parametros(),
                (r, n) -> new SomaPromocao(
                        idOuNull(r, "promocao_id"), r.getLong("vendas"), dec(r, "desconto"), dec(r, "receita")));
    }

    public record Estoque(
            Long produtoId,
            String nome,
            String categoria,
            int quantidade,
            BigDecimal preco,
            BigDecimal custoUnitario,
            long vendidas) {}

    public List<Estoque> estoque(FiltroFinancas f) {
        Onde o = onde(f);
        String categoria = f.categoriaId() == null ? "" : " AND p.categoria_id = :categoria";
        return jdbc.query(
                "SELECT p.id, p.nome, c.nome AS categoria, p.quantidade, p.preco, p.custo_unitario,"
                        + " COALESCE(s.unidades, 0) AS vendidas"
                        + " FROM produto p LEFT JOIN categoria c ON c.id = p.categoria_id"
                        + " LEFT JOIN (SELECT i.produto_id, SUM(i.quantidade) AS unidades"
                        + "  FROM item_venda i JOIN venda v ON v.id = i.venda_id WHERE " + o.vendas()
                        + "  GROUP BY i.produto_id) s ON s.produto_id = p.id"
                        + " WHERE p.ativo = TRUE" + categoria
                        + " ORDER BY p.nome, p.id",
                o.parametros(),
                (r, n) -> new Estoque(
                        r.getLong("id"),
                        r.getString("nome"),
                        r.getString("categoria"),
                        r.getInt("quantidade"),
                        r.getBigDecimal("preco"),
                        r.getBigDecimal("custo_unitario"),
                        r.getLong("vendidas")));
    }

    public List<Estoque> parados(FiltroFinancas f) {
        Onde o = onde(f);
        String categoria = f.categoriaId() == null ? "" : " AND p.categoria_id = :categoria";
        return jdbc.query(
                "SELECT p.id, p.nome, c.nome AS categoria, p.quantidade, p.preco, p.custo_unitario, 0 AS vendidas"
                        + " FROM produto p LEFT JOIN categoria c ON c.id = p.categoria_id"
                        + " WHERE p.ativo = TRUE AND p.quantidade > 0" + categoria
                        + " AND NOT EXISTS (SELECT 1 FROM item_venda i JOIN venda v ON v.id = i.venda_id"
                        + "  WHERE i.produto_id = p.id AND " + o.vendas() + ")"
                        + " ORDER BY p.nome, p.id",
                o.parametros(),
                (r, n) -> new Estoque(
                        r.getLong("id"),
                        r.getString("nome"),
                        r.getString("categoria"),
                        r.getInt("quantidade"),
                        r.getBigDecimal("preco"),
                        r.getBigDecimal("custo_unitario"),
                        0));
    }

    public record ValorEstoque(BigDecimal aCusto, BigDecimal aPreco, long produtosSemCusto) {}

    public ValorEstoque valorDoEstoque(FiltroFinancas f) {
        var params = new MapSqlParameterSource();
        String categoria = "";
        if (f.categoriaId() != null) {
            categoria = " AND p.categoria_id = :categoria";
            params.addValue("categoria", f.categoriaId());
        }
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(CASE WHEN p.custo_unitario IS NOT NULL THEN p.custo_unitario * p.quantidade END), 0) AS a_custo,"
                        + " COALESCE(SUM(p.preco * p.quantidade), 0) AS a_preco,"
                        + " COALESCE(SUM(CASE WHEN p.custo_unitario IS NULL AND p.quantidade > 0 THEN 1 ELSE 0 END), 0) AS sem_custo"
                        + " FROM produto p WHERE p.ativo = TRUE" + categoria,
                params,
                (r, n) -> new ValorEstoque(dec(r, "a_custo"), dec(r, "a_preco"), r.getLong("sem_custo")));
    }

    public record Cancelamento(
            Long vendaId, LocalDateTime dataHora, BigDecimal valor, LocalDateTime canceladaEm, String motivo) {}

    public List<Cancelamento> cancelamentos(FiltroFinancas f) {
        var params = new MapSqlParameterSource()
                .addValue("ini", f.inicioConsulta())
                .addValue("fim", f.fimConsulta())
                .addValue("agora", f.agora());
        return jdbc.query(
                "SELECT v.id, v.data_hora, v.valor_cobrado, v.data_cancelamento, v.motivo_cancelamento FROM venda v"
                        + " WHERE v.cancelada = TRUE AND v.data_hora >= :ini AND v.data_hora < :fim"
                        + (f.diaADia() ? DIA_A_DIA : "") + " ORDER BY v.data_hora, v.id",
                params,
                (r, n) -> new Cancelamento(
                        r.getLong("id"),
                        r.getObject("data_hora", LocalDateTime.class),
                        dec(r, "valor_cobrado"),
                        r.getObject("data_cancelamento", LocalDateTime.class),
                        r.getString("motivo_cancelamento")));
    }

    public record Ajuste(
            Long vendaId, LocalDateTime dataHora, BigDecimal calculado, BigDecimal cobrado, String motivo) {}

    public List<Ajuste> ajustesManuais(FiltroFinancas f) {
        Onde o = onde(f);
        return jdbc.query(
                "SELECT v.id, v.data_hora, v.valor_calculado, v.valor_cobrado, v.motivo_ajuste FROM venda v"
                        + " WHERE " + o.vendas()
                        + " AND v.valor_calculado IS NOT NULL AND v.valor_cobrado <> v.valor_calculado"
                        + " ORDER BY v.data_hora, v.id",
                o.parametros(),
                (r, n) -> new Ajuste(
                        r.getLong("id"),
                        r.getObject("data_hora", LocalDateTime.class),
                        dec(r, "valor_calculado"),
                        dec(r, "valor_cobrado"),
                        r.getString("motivo_ajuste")));
    }
}
