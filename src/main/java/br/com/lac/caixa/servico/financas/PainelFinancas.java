package br.com.lac.caixa.servico.financas;

import br.com.lac.caixa.consulta.FiltroFinancas;
import br.com.lac.caixa.repositorio.ConsultasFinanceiras.Balde;
import br.com.lac.caixa.repositorio.ConsultasFinanceiras.SomaProduto;
import br.com.lac.caixa.servico.financas.Financas.ClasseAbc;
import br.com.lac.caixa.servico.financas.Financas.Comparacao;
import br.com.lac.caixa.servico.financas.Financas.LinhaAbc;
import br.com.lac.caixa.servico.financas.Financas.LinhaCategoria;
import br.com.lac.caixa.servico.financas.Financas.LinhaDemonstrativo;
import br.com.lac.caixa.servico.financas.Financas.LinhaForma;
import br.com.lac.caixa.servico.financas.Financas.LinhaGiro;
import br.com.lac.caixa.servico.financas.Financas.LinhaProduto;
import br.com.lac.caixa.servico.financas.Financas.LinhaPromocao;
import br.com.lac.caixa.servico.financas.Financas.Metrica;
import br.com.lac.caixa.servico.financas.Financas.Resumo;
import br.com.lac.caixa.servico.financas.Financas.Serie;
import br.com.lac.caixa.servico.financas.Financas.ValorEstoque;
import br.com.lac.caixa.servico.financas.Financas.Variacao;
import br.com.lac.caixa.util.Dinheiro;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Monta o painel e os dados dos gráficos de Finanças, com os textos já formatados. */
@Service
@Transactional(readOnly = true)
public class PainelFinancas {
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DecimalFormatSymbols SIMBOLOS = DecimalFormatSymbols.getInstance(PT_BR);
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH'h'");
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MM/yyyy");

    private final ResumoFinanceiro resumos;
    private final SerieFinanceira series;
    private final RankingsFinanceiros rankings;
    private final EstoqueFinanceiro estoque;

    public PainelFinancas(
            ResumoFinanceiro resumos, SerieFinanceira series, RankingsFinanceiros rankings, EstoqueFinanceiro estoque) {
        this.resumos = resumos;
        this.series = series;
        this.rankings = rankings;
        this.estoque = estoque;
    }

    public record Cartao(String id, String rotulo, String valor, String variacao, String direcao, String nota) {}

    public record ProdutoAbc(LinhaProduto produto, ClasseAbc classe) {}

    public record CelulaMapa(int hora, long vendas, BigDecimal receita, int nivel) {}

    public record LinhaMapa(DayOfWeek dia, String rotulo, List<CelulaMapa> horas) {}

    public record Painel(
            boolean vazio,
            Resumo resumo,
            Comparacao comparacao,
            List<Cartao> cartoes,
            List<LinhaDemonstrativo> demonstrativo,
            Serie serie,
            Metrica metrica,
            List<ProdutoAbc> produtos,
            List<LinhaCategoria> categorias,
            List<LinhaForma> formas,
            List<LinhaMapa> mapa,
            List<LinhaPromocao> promocoes,
            List<LinhaGiro> giro,
            List<LinhaGiro> parados,
            ValorEstoque valorEstoque,
            List<Financas.Meta> metas) {}

    public record Grafico(List<String> rotulos, List<BigDecimal> valores, List<String> textos) {}

    public record Dados(
            boolean vazio,
            boolean porHora,
            Grafico receita,
            Grafico lucroBruto,
            String metrica,
            Grafico produtos,
            List<Long> categoriaIds,
            Grafico categorias,
            Grafico formasValor,
            Grafico formasVendas) {}

    public enum Parte {
        RESUMO,
        SERIE,
        PRODUTOS,
        CATEGORIAS,
        FORMAS,
        MAPA,
        PROMOCOES,
        GIRO,
        PARADOS
    }

    public Painel montar(FiltroFinancas f, Metrica metrica) {
        return montar(f, metrica, EnumSet.allOf(Parte.class), true);
    }

    public Painel tabela(FiltroFinancas f, Metrica metrica, Parte parte) {
        return montar(f, metrica, EnumSet.of(parte), false);
    }

    private Painel montar(FiltroFinancas f, Metrica metrica, Set<Parte> partes, boolean tela) {
        Comparacao comparacao = tela ? resumos.comparar(f) : null;
        Resumo r = tela ? comparacao.atual() : partes.contains(Parte.RESUMO) ? resumos.resumo(f) : null;
        List<Balde> baldes = partes.contains(Parte.SERIE) || partes.contains(Parte.MAPA) ? series.baldes(f) : List.of();
        List<SomaProduto> somas = partes.contains(Parte.PRODUTOS) || partes.contains(Parte.CATEGORIAS)
                ? rankings.somasPorProduto(f)
                : List.of();
        return new Painel(
                r != null && r.vendas() == 0,
                r,
                comparacao,
                tela ? cartoes(comparacao) : List.of(),
                r == null ? List.of() : resumos.demonstrativo(r),
                partes.contains(Parte.SERIE) ? series.serie(f, baldes) : null,
                metrica,
                partes.contains(Parte.PRODUTOS) ? produtosComAbc(somas, metrica) : List.of(),
                partes.contains(Parte.CATEGORIAS) ? rankings.rankingCategorias(f, metrica, somas) : List.of(),
                partes.contains(Parte.FORMAS) ? rankings.formasDePagamento(f) : List.of(),
                partes.contains(Parte.MAPA) ? mapa(baldes) : List.of(),
                partes.contains(Parte.PROMOCOES) ? rankings.promocoes(f) : List.of(),
                partes.contains(Parte.GIRO) ? giroOrdenado(f) : List.of(),
                partes.contains(Parte.PARADOS) ? estoque.parados(f) : List.of(),
                tela ? estoque.valorDoEstoque(f) : null,
                tela ? resumos.metas(f, r) : List.of());
    }

    private List<ProdutoAbc> produtosComAbc(List<SomaProduto> somas, Metrica metrica) {
        List<LinhaProduto> porReceita = rankings.rankingProdutos(somas, Metrica.RECEITA);
        Map<Long, ClasseAbc> abc = new HashMap<>();
        for (LinhaAbc l : rankings.curvaAbc(porReceita)) abc.put(l.produtoId(), l.classe());
        List<LinhaProduto> produtos =
                metrica == Metrica.RECEITA ? porReceita : rankings.rankingProdutos(somas, metrica);
        return produtos.stream()
                .map(p -> new ProdutoAbc(p, abc.get(p.produtoId())))
                .toList();
    }

    private List<LinhaGiro> giroOrdenado(FiltroFinancas f) {
        List<LinhaGiro> giro = new ArrayList<>(estoque.giro(f));
        giro.sort(Comparator.comparing(
                        LinhaGiro::diasAteAcabar, Comparator.nullsLast(Comparator.<BigDecimal>naturalOrder()))
                .thenComparing(LinhaGiro::nome));
        return giro;
    }

    public Dados dados(FiltroFinancas f, Metrica metrica) {
        var serie = series.serie(f);
        var somas = rankings.somasPorProduto(f);
        DateTimeFormatter rotulo = switch (serie.granularidade()) {
            case HORA -> HORA;
            case DIA, SEMANA -> DIA;
            case MES -> MES;
        };
        List<String> dias = new ArrayList<>();
        List<BigDecimal> receita = new ArrayList<>();
        List<BigDecimal> lucro = new ArrayList<>();
        for (var p : serie.pontos()) {
            String r = serie.porHora() && !dias.isEmpty() && p.inicio().getHour() == 0
                    ? p.inicio().format(DIA) + " " + p.inicio().format(rotulo)
                    : p.inicio().format(rotulo);
            dias.add(r);
            receita.add(p.receitaLiquida());
            lucro.add(p.lucroBruto());
        }
        var top = rankings.rankingProdutos(somas, metrica).stream().limit(10).toList();
        List<BigDecimal> valoresTop =
                top.stream().map(p -> valorDaMetrica(p, metrica)).toList();
        List<String> textosTop = metrica == Metrica.QUANTIDADE
                ? top.stream().map(p -> p.quantidade() + " un.").toList()
                : valoresTop.stream().map(Dinheiro::reais).toList();
        var categorias = rankings.rankingCategorias(f, Metrica.RECEITA, somas);
        var formas = rankings.formasDePagamento(f);
        boolean vazio = serie.pontos().stream().allMatch(p -> p.vendas() == 0);
        return new Dados(
                vazio,
                serie.porHora(),
                grafico(dias, receita),
                grafico(dias, lucro),
                metrica.name(),
                new Grafico(top.stream().map(LinhaProduto::nome).toList(), valoresTop, textosTop),
                categorias.stream().map(LinhaCategoria::categoriaId).toList(),
                grafico(
                        categorias.stream().map(LinhaCategoria::categoria).toList(),
                        categorias.stream().map(LinhaCategoria::receitaLiquida).toList()),
                grafico(
                        formas.stream().map(l -> l.forma().getRotulo()).toList(),
                        formas.stream().map(LinhaForma::valor).toList()),
                new Grafico(
                        formas.stream().map(l -> l.forma().getRotulo()).toList(),
                        formas.stream().map(l -> BigDecimal.valueOf(l.vendas())).toList(),
                        formas.stream()
                                .map(l -> l.vendas() + (l.vendas() == 1 ? " venda" : " vendas"))
                                .toList()));
    }

    private static Grafico grafico(List<String> rotulos, List<BigDecimal> valores) {
        return new Grafico(
                rotulos, valores, valores.stream().map(Dinheiro::reais).toList());
    }

    static BigDecimal valorDaMetrica(LinhaProduto p, Metrica m) {
        return switch (m) {
            case QUANTIDADE -> BigDecimal.valueOf(p.quantidade());
            case LUCRO -> p.lucroBruto();
            case MARGEM -> p.margemPercentual() == null ? BigDecimal.ZERO : p.margemPercentual();
            case RECEITA, PARTICIPACAO -> p.receitaLiquida();
        };
    }

    private List<Cartao> cartoes(Comparacao c) {
        Resumo r = c.atual();
        Map<String, Variacao> v = new HashMap<>();
        c.variacoes().forEach(x -> v.put(x.indicador(), x));
        String contra = c.periodoAnterior() == null ? null : "em relação a " + c.periodoAnterior();
        List<Cartao> lista = new ArrayList<>();
        lista.add(cartao(
                "receita",
                "Receita líquida",
                Dinheiro.reais(r.receitaLiquida()),
                v.get("receitaLiquida"),
                contra,
                false,
                null));
        lista.add(cartao(
                "lucro",
                "Lucro líquido",
                r.lucroLiquido() == null ? "—" : Dinheiro.reais(r.lucroLiquido()),
                v.get("lucroLiquido"),
                contra,
                false,
                r.lucroLiquido() == null ? "Não se aplica com filtro de categoria ou de forma." : null));
        lista.add(cartao(
                "margem",
                "Margem bruta",
                r.margemBrutaPercentual() == null ? "—" : percentual(r.margemBrutaPercentual()),
                v.get("margemBrutaPercentual"),
                contra,
                true,
                null));
        lista.add(cartao(
                "ticket",
                "Ticket médio",
                r.ticketMedio() == null ? "—" : Dinheiro.reais(r.ticketMedio()),
                v.get("ticketMedio"),
                contra,
                false,
                null));
        lista.add(cartao("vendas", "Vendas", String.valueOf(r.vendas()), v.get("vendas"), contra, false, null));
        lista.add(cartao(
                "itens",
                "Itens vendidos",
                String.valueOf(r.itensVendidos()),
                v.get("itensVendidos"),
                contra,
                false,
                null));
        return lista;
    }

    private static Cartao cartao(
            String id, String rotulo, String valor, Variacao v, String contra, boolean pontos, String nota) {
        if (contra == null || v == null || v.diferenca() == null) {
            return new Cartao(id, rotulo, valor, "Sem período anterior para comparar", "sem", nota);
        }
        int sinal = v.diferenca().signum();
        String seta = sinal > 0 ? "▲" : sinal < 0 ? "▼" : "=";
        String direcao = sinal > 0 ? "sobe" : sinal < 0 ? "desce" : "igual";
        String quanto;
        if (pontos) {
            quanto = assinado(v.diferenca()) + " p.p.";
        } else if (v.percentual() == null) {
            quanto = "antes era zero";
        } else {
            quanto = assinado(v.percentual()) + "%";
        }
        return new Cartao(id, rotulo, valor, seta + " " + quanto + " " + contra, direcao, nota);
    }

    static String percentual(BigDecimal p) {
        return new DecimalFormat("0.00", SIMBOLOS).format(p) + "%";
    }

    static String assinado(BigDecimal valor) {
        return (valor.signum() > 0 ? "+" : "") + new DecimalFormat("0.00", SIMBOLOS).format(valor);
    }

    private List<LinhaMapa> mapa(List<Balde> baldes) {
        var celulas = series.mapaDeCalor(baldes);
        long maximo = celulas.stream().mapToLong(Financas.Celula::vendas).max().orElse(0);
        Map<DayOfWeek, List<CelulaMapa>> porDia = new EnumMap<>(DayOfWeek.class);
        for (var c : celulas) {
            int nivel = c.vendas() == 0 || maximo == 0 ? 0 : (int) Math.max(1, Math.ceil(4.0 * c.vendas() / maximo));
            porDia.computeIfAbsent(c.diaDaSemana(), d -> new ArrayList<>())
                    .add(new CelulaMapa(c.hora(), c.vendas(), c.receitaLiquida(), nivel));
        }
        List<LinhaMapa> linhas = new ArrayList<>();
        porDia.forEach(
                (dia, horas) -> linhas.add(new LinhaMapa(dia, dia.getDisplayName(TextStyle.SHORT, PT_BR), horas)));
        return linhas;
    }
}
