package br.com.lac.caixa.servico.financas;

import static br.com.lac.caixa.servico.financas.Contas.CEM;
import static br.com.lac.caixa.servico.financas.Contas.dinheiro;
import static br.com.lac.caixa.servico.financas.Contas.percentual;

import br.com.lac.caixa.consulta.FiltroFinancas;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.repositorio.ConsultasFinanceiras;
import br.com.lac.caixa.repositorio.ConsultasFinanceiras.SomaCategoria;
import br.com.lac.caixa.repositorio.ConsultasFinanceiras.SomaForma;
import br.com.lac.caixa.repositorio.ConsultasFinanceiras.SomaProduto;
import br.com.lac.caixa.repositorio.ConsultasFinanceiras.SomaPromocao;
import br.com.lac.caixa.repositorio.PromocaoRepository;
import br.com.lac.caixa.servico.financas.Financas.ClasseAbc;
import br.com.lac.caixa.servico.financas.Financas.LinhaAbc;
import br.com.lac.caixa.servico.financas.Financas.LinhaCategoria;
import br.com.lac.caixa.servico.financas.Financas.LinhaForma;
import br.com.lac.caixa.servico.financas.Financas.LinhaProduto;
import br.com.lac.caixa.servico.financas.Financas.LinhaPromocao;
import br.com.lac.caixa.servico.financas.Financas.Metrica;
import br.com.lac.caixa.servico.financas.Financas.Ranqueavel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rankings de produtos e categorias, curva ABC, formas de pagamento e promoções do período. */
@Service
@Transactional(readOnly = true)
public class RankingsFinanceiros {
    private final ConsultasFinanceiras consultas;
    private final PromocaoRepository promocoes;

    public RankingsFinanceiros(ConsultasFinanceiras consultas, PromocaoRepository promocoes) {
        this.consultas = consultas;
        this.promocoes = promocoes;
    }

    static <T extends Ranqueavel<T>> List<T> ordenarENumerar(List<T> linhas, Metrica metrica, Comparator<T> desempate) {
        Comparator<T> c = switch (metrica) {
            case QUANTIDADE -> Comparator.comparingLong((T l) -> l.quantidade());
            case RECEITA, PARTICIPACAO -> Comparator.comparing((T l) -> l.receitaLiquida());
            case LUCRO -> Comparator.comparing((T l) -> l.lucroBruto());
            case MARGEM ->
                Comparator.comparing((T l) -> l.margemPercentual(), Comparator.nullsFirst(Comparator.naturalOrder()));
        };
        List<T> ordenadas = new ArrayList<>(linhas);
        ordenadas.sort(c.reversed().thenComparing(desempate));
        List<T> numeradas = new ArrayList<>();
        for (int i = 0; i < ordenadas.size(); i++)
            numeradas.add(ordenadas.get(i).comPosicao(i + 1));
        return numeradas;
    }

    private static List<LinhaProduto> linhasDeProduto(List<SomaProduto> somas, BigDecimal total, Metrica metrica) {
        List<LinhaProduto> linhas = new ArrayList<>();
        for (SomaProduto s : somas) {
            BigDecimal liquido = dinheiro(s.liquido());
            BigDecimal cmv = dinheiro(s.cmv());
            BigDecimal lucro = liquido.subtract(cmv);
            linhas.add(new LinhaProduto(
                    0,
                    s.produtoId(),
                    s.nome(),
                    s.categoria(),
                    !s.ativo(),
                    s.unidades(),
                    liquido,
                    cmv,
                    lucro,
                    percentual(lucro, liquido),
                    percentual(liquido, total),
                    s.unidadesSemCusto()));
        }
        return ordenarENumerar(
                linhas, metrica, Comparator.comparing(LinhaProduto::nome).thenComparing(LinhaProduto::produtoId));
    }

    public List<SomaProduto> somasPorProduto(FiltroFinancas f) {
        return consultas.porProduto(f);
    }

    public List<LinhaProduto> rankingProdutos(FiltroFinancas f, Metrica metrica) {
        return rankingProdutos(somasPorProduto(f), metrica);
    }

    public List<LinhaProduto> rankingProdutos(List<SomaProduto> somas, Metrica metrica) {
        BigDecimal total = somas.stream().map(s -> dinheiro(s.liquido())).reduce(BigDecimal.ZERO, BigDecimal::add);
        return linhasDeProduto(somas, total, metrica);
    }

    public List<LinhaCategoria> rankingCategorias(FiltroFinancas f, Metrica metrica) {
        return rankingCategorias(f, metrica, somasPorProduto(f));
    }

    public List<LinhaCategoria> rankingCategorias(FiltroFinancas f, Metrica metrica, List<SomaProduto> produtos) {
        List<SomaCategoria> categorias = consultas.porCategoria(f);
        BigDecimal total = categorias.stream().map(c -> dinheiro(c.liquido())).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<LinhaCategoria> linhas = new ArrayList<>();
        for (var c : categorias) {
            BigDecimal liquido = dinheiro(c.liquido());
            BigDecimal cmv = dinheiro(c.cmv());
            BigDecimal lucro = liquido.subtract(cmv);
            var daCategoria = produtos.stream()
                    .filter(p -> Objects.equals(p.categoriaId(), c.categoriaId()))
                    .toList();
            linhas.add(new LinhaCategoria(
                    0,
                    c.categoriaId(),
                    c.categoria() == null ? "Sem categoria" : c.categoria(),
                    c.unidades(),
                    c.vendas(),
                    liquido,
                    cmv,
                    lucro,
                    percentual(lucro, liquido),
                    percentual(liquido, total),
                    c.unidadesSemCusto(),
                    linhasDeProduto(daCategoria, liquido, metrica)));
        }
        return ordenarENumerar(linhas, metrica, Comparator.comparing(LinhaCategoria::categoria));
    }

    public List<LinhaAbc> curvaAbc(FiltroFinancas f) {
        return curvaAbc(rankingProdutos(f, Metrica.RECEITA));
    }

    public List<LinhaAbc> curvaAbc(List<LinhaProduto> porReceita) {
        BigDecimal total =
                porReceita.stream().map(LinhaProduto::receitaLiquida).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<LinhaAbc> linhas = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;
        for (LinhaProduto p : porReceita) {
            BigDecimal antes =
                    total.signum() == 0 ? CEM : acumulado.multiply(CEM).divide(total, 10, RoundingMode.HALF_EVEN);
            ClasseAbc classe = antes.compareTo(BigDecimal.valueOf(80)) < 0
                    ? ClasseAbc.A
                    : antes.compareTo(BigDecimal.valueOf(95)) < 0 ? ClasseAbc.B : ClasseAbc.C;
            acumulado = acumulado.add(p.receitaLiquida());
            linhas.add(new LinhaAbc(
                    p.posicao(),
                    p.produtoId(),
                    p.nome(),
                    p.receitaLiquida(),
                    p.participacaoPercentual(),
                    percentual(acumulado, total),
                    classe));
        }
        return linhas;
    }

    public List<LinhaForma> formasDePagamento(FiltroFinancas f) {
        if (f.categoriaId() != null) return List.of();
        var somas = consultas.porForma(f);
        BigDecimal total = somas.stream().map(s -> dinheiro(s.valor())).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<LinhaForma> linhas = new ArrayList<>();
        for (FormaPagamento forma : FormaPagamento.values()) {
            var s = somas.stream().filter(x -> x.forma() == forma).findFirst();
            BigDecimal valor = s.map(x -> dinheiro(x.valor())).orElse(BigDecimal.ZERO.setScale(2));
            linhas.add(new LinhaForma(
                    forma,
                    valor,
                    s.map(SomaForma::vendas).orElse(0L),
                    s.map(x -> dinheiro(x.taxas())).orElse(BigDecimal.ZERO.setScale(2)),
                    percentual(valor, total)));
        }
        return linhas;
    }

    public List<LinhaPromocao> promocoes(FiltroFinancas f) {
        var somas = consultas.porPromocao(f);
        Map<Long, Promocao> porId = new HashMap<>();
        promocoes
                .findAllById(somas.stream()
                        .map(SomaPromocao::promocaoId)
                        .filter(Objects::nonNull)
                        .toList())
                .forEach(p -> porId.put(p.getId(), p));
        List<LinhaPromocao> linhas = new ArrayList<>();
        for (var s : somas) {
            String nome = s.promocaoId() == null
                    ? "Promoções apagadas"
                    : porId.containsKey(s.promocaoId())
                            ? porId.get(s.promocaoId()).getRotuloComPreco()
                            : "Promoção #" + s.promocaoId();
            linhas.add(
                    new LinhaPromocao(s.promocaoId(), nome, s.vendas(), dinheiro(s.desconto()), dinheiro(s.receita())));
        }
        linhas.sort(
                Comparator.comparing(LinhaPromocao::descontoTotal).reversed().thenComparing(LinhaPromocao::promocao));
        return linhas;
    }
}
