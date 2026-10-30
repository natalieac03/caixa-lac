package br.com.lac.caixa.servico.financas;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
 * Camada de consulta das finanças (sem tela ainda). As somas são feitas no
 * banco (ConsultasFinanceiras); aqui ficam arredondamento, percentuais,
 * ordenação e o preenchimento de dias e horas sem venda.
 */
@Service
@Transactional(readOnly = true)
public class FinancasService {
    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ConsultasFinanceiras consultas;
    private final EventoRepository eventos;
    private final PromocaoRepository promocoes;

    public FinancasService(ConsultasFinanceiras consultas, EventoRepository eventos, PromocaoRepository promocoes) {
        this.consultas = consultas;
        this.eventos = eventos;
        this.promocoes = promocoes;
    }

    static BigDecimal dinheiro(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2, RoundingMode.HALF_EVEN);
    }

    /** parte / todo x 100, 2 casas; null se o todo é zero ou ausente. */
    static BigDecimal percentual(BigDecimal parte, BigDecimal todo) {
        if (parte == null || todo == null || todo.signum() == 0) return null;
        return parte.multiply(CEM).divide(todo, 2, RoundingMode.HALF_EVEN);
    }

    static BigDecimal razao(BigDecimal a, long b) {
        return b == 0 ? null : a.divide(BigDecimal.valueOf(b), 2, RoundingMode.HALF_EVEN);
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
            long dias = f.dias();
            LocalDate ate = f.inicio().minusDays(1);
            LocalDate de = ate.minusDays(dias - 1);
            anterior = f.noPeriodo(de, ate);
            descricao = de.format(DATA) + " a " + ate.format(DATA);
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

    /** Por dia (ou por hora, se o período tem até 2 dias), com zeros onde não houve venda. */
    public Serie serie(FiltroFinancas f) {
        boolean porHora = f.porHora();
        Map<LocalDateTime, BigDecimal[]> somas = new TreeMap<>();
        Map<LocalDateTime, Long> vendas = new TreeMap<>();
        for (Balde b : consultas.porDiaEHora(f)) {
            LocalDateTime chave =
                    porHora ? b.dia().atTime(b.hora(), 0) : b.dia().atStartOfDay();
            somas.merge(chave, new BigDecimal[] {b.liquido(), b.cmv()}, (x, y) ->
                    new BigDecimal[] {x[0].add(y[0]), x[1].add(y[1])});
            vendas.merge(chave, b.vendas(), Long::sum);
        }
        List<Ponto> pontos = new ArrayList<>();
        LocalDateTime cursor = porHora
                ? f.inicioConsulta().withMinute(0).withSecond(0).withNano(0)
                : f.inicioConsulta().toLocalDate().atStartOfDay();
        while (cursor.isBefore(f.fimConsulta())) {
            BigDecimal[] s = somas.getOrDefault(cursor, new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal liquido = dinheiro(s[0]);
            pontos.add(new Ponto(cursor, liquido, liquido.subtract(dinheiro(s[1])), vendas.getOrDefault(cursor, 0L)));
            cursor = porHora ? cursor.plusHours(1) : cursor.plusDays(1);
        }
        return new Serie(porHora, pontos);
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

    /** Por forma de pagamento, pelas partes: valor, vendas, taxa estimada e participação. Todas as formas. */
    public List<LinhaForma> formasDePagamento(FiltroFinancas f) {
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
}
