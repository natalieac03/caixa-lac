package br.com.lac.caixa.repositorio;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * As agregações das finanças, em SQL que roda igual no PostgreSQL e no H2.
 * Só entram as cláusulas dos filtros preenchidos (nada de "? is null", que o
 * Postgres recusa sem tipo). Canceladas ficam sempre de fora. Os valores voltam
 * como o banco somou; arredondar e calcular percentuais fica com o serviço.
 */
@Repository
public class ConsultasFinanceiras {

    private final NamedParameterJdbcTemplate jdbc;

    public ConsultasFinanceiras(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** WHERE das vendas (alias v) e, se o filtro tem categoria, dos itens (alias p = produto do item). */
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

    // ---------------------------------------------------------------- resumo

    /** Somas dos itens das vendas do filtro. cmv = custo x quantidade dos itens com custo (4 casas). */
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
                        + " COALESCE(SUM(CASE WHEN i.custo_unitario IS NOT NULL THEN i.custo_unitario * i.quantidade END), 0) AS cmv,"
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

    /**
     * Somas por venda: quantidade, cobrado, descontos promocionais, ajustes
     * (cobrado - calculado, com sinal), cortesias e as vendas sem itens (antigas).
     */
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

    /** Soma das taxas estimadas das partes de pagamento das vendas do filtro. */
    public BigDecimal taxas(FiltroFinancas f) {
        Onde o = onde(f);
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(pg.taxa_estimada), 0) FROM pagamento_venda pg JOIN venda v ON v.id = pg.venda_id"
                        + " WHERE " + o.vendas(),
                o.parametros(),
                BigDecimal.class);
    }

    /** Despesas: do evento (ligadas a ele) ou do período (pela data). */
    public BigDecimal despesas(FiltroFinancas f) {
        if (f.evento() != null) {
            return jdbc.queryForObject(
                    "SELECT COALESCE(SUM(valor), 0) FROM despesa WHERE evento_id = :evento",
                    new MapSqlParameterSource("evento", f.evento().id()),
                    BigDecimal.class);
        }
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(valor), 0) FROM despesa WHERE data >= :de AND data <= :ate",
                new MapSqlParameterSource().addValue("de", f.inicio()).addValue("ate", f.fim()),
                BigDecimal.class);
    }

    // ---------------------------------------------------------------- séries

    /** Um balde de dia e hora: receita líquida, CMV (itens com custo) e vendas distintas. */
    public record Balde(LocalDate dia, int hora, BigDecimal liquido, BigDecimal cmv, long vendas) {}

    /** Por dia e hora (o serviço junta por dia, por hora ou por dia da semana). */
    public List<Balde> porDiaEHora(FiltroFinancas f) {
        Onde o = onde(f);
        return jdbc.query(
                "SELECT CAST(v.data_hora AS DATE) AS dia, EXTRACT(HOUR FROM v.data_hora) AS hora,"
                        + " COALESCE(SUM(i.valor_liquido), 0) AS liquido,"
                        + " COALESCE(SUM(CASE WHEN i.custo_unitario IS NOT NULL THEN i.custo_unitario * i.quantidade END), 0) AS cmv,"
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
}
