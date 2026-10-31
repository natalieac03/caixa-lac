package br.com.lac.caixa.servico.financas;

import static br.com.lac.caixa.servico.financas.Contas.CEM;
import static br.com.lac.caixa.servico.financas.Contas.dinheiro;
import static br.com.lac.caixa.servico.financas.Contas.percentual;
import static br.com.lac.caixa.servico.financas.Contas.razao;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.repositorio.ConsultasFinanceiras;
import br.com.lac.caixa.repositorio.ConsultasFinanceiras.Balde;
import br.com.lac.caixa.repositorio.ConsultasFinanceiras.SomaProduto;
import br.com.lac.caixa.repositorio.EventoRepository;
import br.com.lac.caixa.repositorio.FiltroFinancas;
import br.com.lac.caixa.repositorio.PeriodoDoEvento;
import br.com.lac.caixa.repositorio.PromocaoRepository;
import br.com.lac.caixa.servico.financas.Financas.Celula;
import br.com.lac.caixa.servico.financas.Financas.ClasseAbc;
import br.com.lac.caixa.servico.financas.Financas.Cobertura;
import br.com.lac.caixa.servico.financas.Financas.Comparacao;
import br.com.lac.caixa.servico.financas.Financas.Granularidade;
import br.com.lac.caixa.servico.financas.Financas.LinhaAbc;
import br.com.lac.caixa.servico.financas.Financas.LinhaCategoria;
import br.com.lac.caixa.servico.financas.Financas.LinhaForma;
import br.com.lac.caixa.servico.financas.Financas.LinhaProduto;
import br.com.lac.caixa.servico.financas.Financas.LinhaPromocao;
import br.com.lac.caixa.servico.financas.Financas.Metrica;
import br.com.lac.caixa.servico.financas.Financas.Ponto;
import br.com.lac.caixa.servico.financas.Financas.Resumo;
import br.com.lac.caixa.servico.financas.Financas.Serie;
import br.com.lac.caixa.servico.financas.Financas.Variacao;
import br.com.lac.caixa.util.Dinheiro;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As contas da tela de Finanças (o painel): resumo e comparação, série, mapa
 * de calor, rankings, curva ABC, formas, promoções, demonstrativo e metas. As
 * somas são feitas no banco (ConsultasFinanceiras); aqui ficam arredondamento,
 * percentuais, ordenação e o preenchimento dos pontos sem venda. O estoque, o
 * relatório do evento e o comparativo têm os seus serviços; as contas comuns
 * ficam em {@link Contas}.
 */
@Service
@Transactional(readOnly = true)
public class FinancasService {
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ConsultasFinanceiras consultas;
    private final EventoRepository eventos;
    private final PromocaoRepository promocoes;

    public FinancasService(ConsultasFinanceiras consultas, EventoRepository eventos, PromocaoRepository promocoes) {
        this.consultas = consultas;
        this.eventos = eventos;
        this.promocoes = promocoes;
    }

    // ---------------------------------------------------------------- A: resumo

    public Resumo resumo(FiltroFinancas f) {
        var itens = consultas.somaItens(f);
        var vendas = consultas.somaVendas(f);
        boolean porCategoria = f.categoriaId() != null;

        BigDecimal liquido = dinheiro(itens.liquido());
        BigDecimal cmv = dinheiro(itens.cmv());
        BigDecimal margem = liquido.subtract(cmv);
        BigDecimal taxas = porCategoria ? null : dinheiro(consultas.taxas(f));
        BigDecimal despesas = f.aceitaDespesas() ? dinheiro(consultas.despesas(f)) : null;
        BigDecimal lucro = taxas == null || despesas == null
                ? null
                : liquido.subtract(taxas).subtract(cmv).subtract(despesas);

        BigDecimal comCusto = dinheiro(itens.liquidoComCusto());
        var cobertura = new Cobertura(
                comCusto,
                liquido.subtract(comCusto),
                percentual(comCusto, liquido),
                itens.linhasSemCusto(),
                itens.linhasSemCusto() > 0);

        return new Resumo(
                dinheiro(itens.bruto()),
                porCategoria ? null : dinheiro(vendas.descontos()),
                porCategoria ? null : dinheiro(vendas.ajustes()),
                liquido,
                taxas,
                cmv,
                margem,
                percentual(margem, liquido),
                despesas,
                lucro,
                percentual(lucro, liquido),
                vendas.vendas(),
                itens.unidades(),
                razao(liquido, vendas.vendas()),
                razao(BigDecimal.valueOf(itens.unidades()), vendas.vendas()),
                vendas.cortesias(),
                cobertura,
                porCategoria ? 0 : vendas.semItens(),
                porCategoria ? BigDecimal.ZERO.setScale(2) : dinheiro(vendas.cobradoSemItens()));
    }

    /** O resumo e o do período anterior de mesmo tamanho (ou do evento anterior), com as variações. */
    public Comparacao comparar(FiltroFinancas f) {
        Resumo atual = resumo(f);
        FiltroFinancas anterior;
        String descricao;
        if (f.evento() != null) {
            var ev =
                    eventos.findFirstByFimLessThanEqualOrderByFimDesc(f.evento().inicio());
            if (ev.isEmpty()) return new Comparacao(atual, null, null, variacoes(atual, null));
            var e = ev.get();
            anterior = f.noEvento(new PeriodoDoEvento(e.getId(), e.getNome(), e.getInicio(), e.getFim()));
            descricao = "evento " + e.getNome();
        } else {
            // Mesmo tamanho até agora e, se o período ainda não acabou, o mesmo recorte de horas.
            long dias = f.dias();
            LocalDate ate = f.inicio().minusDays(1);
            LocalDate de = ate.minusDays(dias - 1);
            anterior = f.noPeriodo(de, ate, dias);
            descricao = de.equals(ate) ? de.format(DATA) : de.format(DATA) + " a " + ate.format(DATA);
            if (anterior.fimConsulta().isBefore(anterior.fimEscolhido())) {
                descricao += " até " + anterior.fimConsulta().format(HORA);
            }
        }
        Resumo antes = resumo(anterior);
        return new Comparacao(atual, antes, descricao, variacoes(atual, antes));
    }

    private static List<Variacao> variacoes(Resumo atual, Resumo antes) {
        Map<String, Function<Resumo, BigDecimal>> indicadores = new LinkedHashMap<>();
        indicadores.put("receitaBruta", Resumo::receitaBruta);
        indicadores.put("descontosPromocionais", Resumo::descontosPromocionais);
        indicadores.put("ajustes", Resumo::ajustes);
        indicadores.put("receitaLiquida", Resumo::receitaLiquida);
        indicadores.put("taxas", Resumo::taxas);
        indicadores.put("cmv", Resumo::cmv);
        indicadores.put("margemBruta", Resumo::margemBruta);
        indicadores.put("margemBrutaPercentual", Resumo::margemBrutaPercentual);
        indicadores.put("despesas", Resumo::despesas);
        indicadores.put("lucroLiquido", Resumo::lucroLiquido);
        indicadores.put("lucroLiquidoPercentual", Resumo::lucroLiquidoPercentual);
        indicadores.put("vendas", r -> BigDecimal.valueOf(r.vendas()));
        indicadores.put("itensVendidos", r -> BigDecimal.valueOf(r.itensVendidos()));
        indicadores.put("ticketMedio", Resumo::ticketMedio);
        indicadores.put("itensPorVenda", Resumo::itensPorVenda);
        indicadores.put("cortesias", r -> BigDecimal.valueOf(r.cortesias()));
        List<Variacao> lista = new ArrayList<>();
        indicadores.forEach((nome, valor) -> {
            BigDecimal a = valor.apply(atual);
            BigDecimal b = antes == null ? null : valor.apply(antes);
            BigDecimal diferenca = a == null || b == null ? null : a.subtract(b);
            lista.add(new Variacao(nome, a, b, diferenca, diferenca == null ? null : percentual(diferenca, b.abs())));
        });
        return lista;
    }

    // ---------------------------------------------------------------- B: séries

    /**
     * De quanto em quanto tempo vai a série, pelo tamanho do período escolhido:
     * por hora até 48 horas, por dia até 90 dias, por semana até 1 ano e por mês acima.
     */
    static Granularidade granularidade(FiltroFinancas f) {
        LocalDateTime de = f.inicioConsulta();
        LocalDateTime ate = f.fimEscolhido();
        if (Duration.between(de, ate).compareTo(Duration.ofHours(48)) <= 0) return Granularidade.HORA;
        if (!ate.isAfter(de.plusDays(90))) return Granularidade.DIA;
        if (!ate.isAfter(de.plusYears(1))) return Granularidade.SEMANA;
        return Granularidade.MES;
    }

    /** Início do ponto em que cai o momento: a hora, o dia, a semana (segunda) ou o mês, nunca antes do período. */
    private static LocalDateTime inicioDoPonto(Granularidade g, LocalDateTime momento, LocalDate inicioDoPeriodo) {
        LocalDate dia = momento.toLocalDate();
        LocalDate comeco = switch (g) {
            case HORA -> null;
            case DIA -> dia;
            case SEMANA -> dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> dia.withDayOfMonth(1);
        };
        if (comeco == null) return momento.truncatedTo(ChronoUnit.HOURS);
        return (comeco.isBefore(inicioDoPeriodo) ? inicioDoPeriodo : comeco).atStartOfDay();
    }

    private static LocalDateTime proximoPonto(Granularidade g, LocalDateTime cursor) {
        return switch (g) {
            case HORA -> cursor.plusHours(1);
            case DIA -> cursor.plusDays(1);
            case SEMANA ->
                cursor.toLocalDate()
                        .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                        .atStartOfDay();
            case MES -> cursor.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay();
        };
    }

    /** Por hora, dia, semana ou mês (pelo tamanho do período), com zeros onde não houve venda. */
    public Serie serie(FiltroFinancas f) {
        Granularidade g = granularidade(f);
        LocalDate inicioDoPeriodo = f.inicioConsulta().toLocalDate();
        Map<LocalDateTime, BigDecimal[]> somas = new TreeMap<>();
        Map<LocalDateTime, Long> vendas = new TreeMap<>();
        for (Balde b : consultas.porDiaEHora(f)) {
            LocalDateTime chave = inicioDoPonto(g, b.dia().atTime(b.hora(), 0), inicioDoPeriodo);
            somas.merge(chave, new BigDecimal[] {b.liquido(), b.cmv()}, (x, y) ->
                    new BigDecimal[] {x[0].add(y[0]), x[1].add(y[1])});
            vendas.merge(chave, b.vendas(), Long::sum);
        }
        List<Ponto> pontos = new ArrayList<>();
        LocalDateTime cursor = inicioDoPonto(g, f.inicioConsulta(), inicioDoPeriodo);
        while (cursor.isBefore(f.fimConsulta())) {
            BigDecimal[] s = somas.getOrDefault(cursor, new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal liquido = dinheiro(s[0]);
            pontos.add(new Ponto(cursor, liquido, liquido.subtract(dinheiro(s[1])), vendas.getOrDefault(cursor, 0L)));
            cursor = proximoPonto(g, cursor);
        }
        return new Serie(g, pontos);
    }

    /** Vendas e receita por dia da semana x hora (7 x 24 células, segunda a domingo). */
    public List<Celula> mapaDeCalor(FiltroFinancas f) {
        Map<DayOfWeek, long[]> vendas = new EnumMap<>(DayOfWeek.class);
        Map<DayOfWeek, BigDecimal[]> receita = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) {
            vendas.put(d, new long[24]);
            BigDecimal[] zeros = new BigDecimal[24];
            java.util.Arrays.fill(zeros, BigDecimal.ZERO);
            receita.put(d, zeros);
        }
        // Cada venda está num único dia e hora: somar os baldes por dia da semana é exato.
        for (Balde b : consultas.porDiaEHora(f)) {
            DayOfWeek d = b.dia().getDayOfWeek();
            vendas.get(d)[b.hora()] += b.vendas();
            receita.get(d)[b.hora()] = receita.get(d)[b.hora()].add(b.liquido());
        }
        List<Celula> celulas = new ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            for (int h = 0; h < 24; h++) celulas.add(new Celula(d, h, vendas.get(d)[h], dinheiro(receita.get(d)[h])));
        }
        return celulas;
    }

    // ---------------------------------------------------------------- C: rankings

    private static Comparator<LinhaProduto> ordem(Metrica metrica) {
        Comparator<LinhaProduto> c = switch (metrica) {
            case QUANTIDADE -> Comparator.comparingLong(LinhaProduto::quantidade);
            case RECEITA, PARTICIPACAO -> Comparator.comparing(LinhaProduto::receitaLiquida);
            case LUCRO -> Comparator.comparing(LinhaProduto::lucroBruto);
            case MARGEM ->
                Comparator.comparing(LinhaProduto::margemPercentual, Comparator.nullsFirst(Comparator.naturalOrder()));
        };
        // Maior primeiro; empate pelo nome e pelo id.
        return c.reversed().thenComparing(LinhaProduto::nome).thenComparing(LinhaProduto::produtoId);
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
        linhas.sort(ordem(metrica));
        List<LinhaProduto> numeradas = new ArrayList<>();
        for (int i = 0; i < linhas.size(); i++) {
            LinhaProduto l = linhas.get(i);
            numeradas.add(new LinhaProduto(
                    i + 1,
                    l.produtoId(),
                    l.nome(),
                    l.categoria(),
                    l.arquivado(),
                    l.quantidade(),
                    l.receitaLiquida(),
                    l.cmv(),
                    l.lucroBruto(),
                    l.margemPercentual(),
                    l.participacaoPercentual(),
                    l.unidadesSemCusto()));
        }
        return numeradas;
    }

    private static BigDecimal somaLiquidos(List<SomaProduto> somas) {
        return somas.stream().map(s -> dinheiro(s.liquido())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Produtos vendidos no filtro, ordenados pela métrica (maior primeiro), com posição. */
    public List<LinhaProduto> rankingProdutos(FiltroFinancas f, Metrica metrica) {
        var somas = consultas.porProduto(f);
        return linhasDeProduto(somas, somaLiquidos(somas), metrica);
    }

    /** Categorias com as mesmas métricas e, em cada uma, o ranking dos seus produtos. */
    public List<LinhaCategoria> rankingCategorias(FiltroFinancas f, Metrica metrica) {
        var categorias = consultas.porCategoria(f);
        var produtos = consultas.porProduto(f);
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
        Comparator<LinhaCategoria> c = switch (metrica) {
            case QUANTIDADE -> Comparator.comparingLong(LinhaCategoria::quantidade);
            case RECEITA, PARTICIPACAO -> Comparator.comparing(LinhaCategoria::receitaLiquida);
            case LUCRO -> Comparator.comparing(LinhaCategoria::lucroBruto);
            case MARGEM ->
                Comparator.comparing(
                        LinhaCategoria::margemPercentual, Comparator.nullsFirst(Comparator.naturalOrder()));
        };
        linhas.sort(c.reversed().thenComparing(LinhaCategoria::categoria));
        List<LinhaCategoria> numeradas = new ArrayList<>();
        for (int i = 0; i < linhas.size(); i++) {
            var l = linhas.get(i);
            numeradas.add(new LinhaCategoria(
                    i + 1,
                    l.categoriaId(),
                    l.categoria(),
                    l.quantidade(),
                    l.vendas(),
                    l.receitaLiquida(),
                    l.cmv(),
                    l.lucroBruto(),
                    l.margemPercentual(),
                    l.participacaoPercentual(),
                    l.unidadesSemCusto(),
                    l.produtos()));
        }
        return numeradas;
    }

    /** Curva ABC por receita líquida: A até 80% acumulado, B até 95%, C o resto. */
    public List<LinhaAbc> curvaAbc(FiltroFinancas f) {
        var produtos = rankingProdutos(f, Metrica.RECEITA);
        BigDecimal total = produtos.stream().map(LinhaProduto::receitaLiquida).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<LinhaAbc> linhas = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;
        for (LinhaProduto p : produtos) {
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

    /**
     * Por forma de pagamento, pelas partes: valor, vendas, taxa estimada e
     * participação. Todas as formas. Com filtro de categoria, nenhuma: o
     * pagamento é da venda inteira e não se separa por item.
     */
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
                    s.map(ConsultasFinanceiras.SomaForma::vendas).orElse(0L),
                    s.map(x -> dinheiro(x.taxas())).orElse(BigDecimal.ZERO.setScale(2)),
                    percentual(valor, total)));
        }
        return linhas;
    }

    /** Por promoção: vendas em que entrou, desconto concedido e receita líquida dessas vendas. */
    public List<LinhaPromocao> promocoes(FiltroFinancas f) {
        List<LinhaPromocao> linhas = new ArrayList<>();
        for (var s : consultas.porPromocao(f)) {
            String nome = s.promocaoId() == null
                    ? "Promoções apagadas"
                    : promocoes
                            .findById(s.promocaoId())
                            .map(FinancasService::nome)
                            .orElse("Promoção #" + s.promocaoId());
            linhas.add(
                    new LinhaPromocao(s.promocaoId(), nome, s.vendas(), dinheiro(s.desconto()), dinheiro(s.receita())));
        }
        linhas.sort(
                Comparator.comparing(LinhaPromocao::descontoTotal).reversed().thenComparing(LinhaPromocao::promocao));
        return linhas;
    }

    private static String nome(Promocao p) {
        String tipo = p.getTipo() == null
                ? ""
                : switch (p.getTipo()) {
                    case A_PARTIR_DE -> "A partir de: ";
                    case PACOTE -> "Pacote: ";
                };
        return tipo + p.getNomesCategorias() + " por " + Dinheiro.reais(p.getPreco());
    }

    // ---------------------------------------------------------------- demonstrativo

    /**
     * O demonstrativo em linhas, cada uma com o valor e o % da receita líquida.
     * Com filtro de categoria, descontos e ajustes não se separam: vira uma linha
     * "descontos e ajustes rateados" (bruto - líquido); taxas, despesas e lucro
     * líquido ficam sem valor.
     */
    public List<Financas.LinhaDemonstrativo> demonstrativo(Resumo r) {
        BigDecimal base = r.receitaLiquida();
        List<Financas.LinhaDemonstrativo> linhas = new ArrayList<>();
        linhas.add(linha("Receita bruta", "", r.receitaBruta(), base, false));
        if (r.descontosPromocionais() != null) {
            linhas.add(linha("Descontos promocionais", "(-)", r.descontosPromocionais(), base, false));
            linhas.add(linha("Ajustes manuais", "(+/-)", r.ajustes(), base, false));
        } else {
            linhas.add(linha(
                    "Descontos e ajustes rateados", "(-)", r.receitaBruta().subtract(base), base, false));
        }
        linhas.add(linha("Receita líquida", "=", base, base, true));
        linhas.add(linha("Taxas estimadas", "(-)", r.taxas(), base, false));
        linhas.add(linha("CMV (custo da mercadoria vendida)", "(-)", r.cmv(), base, false));
        linhas.add(linha("Margem bruta", "=", r.margemBruta(), base, true));
        linhas.add(linha("Despesas", "(-)", r.despesas(), base, false));
        linhas.add(linha("Lucro líquido", "=", r.lucroLiquido(), base, true));
        return linhas;
    }

    private static Financas.LinhaDemonstrativo linha(
            String rotulo, String operador, BigDecimal valor, BigDecimal base, boolean resultado) {
        return new Financas.LinhaDemonstrativo(rotulo, operador, valor, percentual(valor, base), resultado);
    }

    // ---------------------------------------------------------------- metas

    /**
     * Metas do evento do filtro (receita líquida e lucro líquido). Só com filtro
     * de evento e sem categoria nem forma: a meta é do evento inteiro.
     */
    public List<Financas.Meta> metas(FiltroFinancas f, Resumo r) {
        if (f.evento() == null || !f.aceitaDespesas()) return List.of();
        var evento = eventos.findById(f.evento().id());
        if (evento.isEmpty()) return List.of();
        List<Financas.Meta> metas = new ArrayList<>();
        if (evento.get().getMetaReceita() != null) {
            metas.add(meta(
                    "receita", "Meta de receita líquida", evento.get().getMetaReceita(), r.receitaLiquida(), false));
        }
        if (evento.get().getMetaLucro() != null && r.lucroLiquido() != null) {
            metas.add(meta(
                    "lucro",
                    "Meta de lucro líquido",
                    evento.get().getMetaLucro(),
                    r.lucroLiquido(),
                    r.isLucroParcial()));
        }
        return metas;
    }

    private static Financas.Meta meta(String id, String rotulo, BigDecimal meta, BigDecimal atual, boolean parcial) {
        BigDecimal p = percentual(atual, meta);
        BigDecimal barra = p.max(BigDecimal.ZERO).min(CEM.setScale(2));
        return new Financas.Meta(id, rotulo, meta, atual, p, barra, atual.compareTo(meta) >= 0, parcial);
    }
}
