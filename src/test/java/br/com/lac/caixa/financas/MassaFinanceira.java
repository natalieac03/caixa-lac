package br.com.lac.caixa.financas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.Evento;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.repositorio.CategoriaDespesaRepository;
import br.com.lac.caixa.repositorio.FiltroFinancas;
import br.com.lac.caixa.repositorio.FiltroHistorico;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import br.com.lac.caixa.repositorio.PromocaoRepository;
import br.com.lac.caixa.repositorio.VendaRepository;
import br.com.lac.caixa.servico.CaixaService;
import br.com.lac.caixa.servico.CategoriaService;
import br.com.lac.caixa.servico.DespesaService;
import br.com.lac.caixa.servico.EstoqueService;
import br.com.lac.caixa.servico.EventoService;
import br.com.lac.caixa.servico.FechamentoService;
import br.com.lac.caixa.servico.PromocaoService;
import br.com.lac.caixa.servico.TaxaService;
import br.com.lac.caixa.servico.financas.ComparativoDeEventos;
import br.com.lac.caixa.servico.financas.EstoqueFinanceiro;
import br.com.lac.caixa.servico.financas.Financas.ClasseAbc;
import br.com.lac.caixa.servico.financas.Financas.LinhaAbc;
import br.com.lac.caixa.servico.financas.Financas.LinhaCategoria;
import br.com.lac.caixa.servico.financas.Financas.LinhaForma;
import br.com.lac.caixa.servico.financas.Financas.LinhaGiro;
import br.com.lac.caixa.servico.financas.Financas.LinhaProduto;
import br.com.lac.caixa.servico.financas.Financas.Metrica;
import br.com.lac.caixa.servico.financas.Financas.Ponto;
import br.com.lac.caixa.servico.financas.Financas.Resumo;
import br.com.lac.caixa.servico.financas.FinancasService;
import br.com.lac.caixa.servico.financas.RelatorioDoEvento;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/** Massa fixa das finanças com os resultados calculados à mão, para H2 e Postgres. */
abstract class MassaFinanceira {
    @Autowired
    FinancasService financas;

    @Autowired
    EstoqueFinanceiro estoqueFinanceiro;

    @Autowired
    ComparativoDeEventos comparativo;

    @Autowired
    RelatorioDoEvento relatorio;

    @Autowired
    FechamentoService fechamento;

    @Autowired
    CaixaService caixa;

    @Autowired
    EstoqueService estoque;

    @Autowired
    CategoriaService categorias;

    @Autowired
    PromocaoService promocoes;

    @Autowired
    PromocaoRepository repositorioDePromocoes;

    @Autowired
    TaxaService taxas;

    @Autowired
    EventoService eventos;

    @Autowired
    DespesaService despesas;

    @Autowired
    CategoriaDespesaRepository categoriasDeDespesa;

    @Autowired
    ProdutoRepository produtos;

    @Autowired
    VendaRepository vendas;

    final Map<String, Produto> p = new HashMap<>();
    final Map<String, Evento> e = new HashMap<>();
    final Map<String, Categoria> c = new HashMap<>();

    static BigDecimal r(String v) {
        return new BigDecimal(v);
    }

    static void assertValor(String esperado, BigDecimal valor) {
        assertEquals(r(esperado), valor, "esperado " + esperado + ", veio " + valor);
    }

    static LocalDateTime t(String texto) {
        return LocalDateTime.parse(texto);
    }

    @Autowired
    ApplicationContext contexto;

    @BeforeEach
    void montar() {
        var massa = new MassaDeTeste(contexto).montar();
        p.putAll(massa.p);
        e.putAll(massa.e);
        c.putAll(massa.c);
    }

    FiltroFinancas evento(String nome) {
        return FiltroFinancas.doEvento(eventos.periodo(e.get(nome).getId()));
    }

    static final FiltroFinancas MARCO = FiltroFinancas.periodo(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 15));

    @Test
    @DisplayName("Resumo da Feira 1: V1, V2, V3 e V5 (V4 cancelada, V6 às 18:00 fora)")
    void resumoDaFeira1() {
        Resumo x = financas.resumo(evento("Feira 1"));
        assertValor("159.00", x.receitaBruta());
        assertValor("3.00", x.descontosPromocionais());
        assertValor("-21.00", x.ajustes());
        assertValor("135.00", x.receitaLiquida());
        assertValor("2.55", x.taxas());
        assertValor("49.00", x.cmv());
        assertValor("86.00", x.margemBruta());
        assertValor("63.70", x.margemBrutaPercentual());
        assertValor("80.00", x.despesas());
        assertValor("3.45", x.lucroLiquido());
        assertValor("2.56", x.lucroLiquidoPercentual());
        assertEquals(4, x.vendas());
        assertEquals(8, x.itensVendidos());
        assertValor("33.75", x.ticketMedio());
        assertValor("2.00", x.itensPorVenda());
        assertEquals(1, x.cortesias());
        assertValor("95.00", x.cobertura().receitaComCusto());
        assertValor("40.00", x.cobertura().receitaSemCusto());
        assertValor("70.37", x.cobertura().percentual());
        assertTrue(x.isLucroParcial());
        assertEquals(0, x.vendasSemItens());
    }

    @Test
    @DisplayName("Resumo de 01 a 15/03: todas as vendas válidas; despesa de evento no dia em que ele começa")
    void resumoDoMes() {
        Resumo x = financas.resumo(MARCO);
        assertValor("257.00", x.receitaBruta());
        assertValor("8.00", x.descontosPromocionais());
        assertValor("-20.00", x.ajustes());
        assertValor("229.00", x.receitaLiquida());
        assertValor("3.58", x.taxas());
        assertValor("83.50", x.cmv());
        assertValor("145.50", x.margemBruta());
        assertValor("63.54", x.margemBrutaPercentual());
        assertValor("115.50", x.despesas());
        assertValor("26.42", x.lucroLiquido());
        assertValor("11.54", x.lucroLiquidoPercentual());
        assertEquals(8, x.vendas());
        assertEquals(16, x.itensVendidos());
        assertValor("28.62", x.ticketMedio());
        assertValor("2.00", x.itensPorVenda());
        assertEquals(1, x.cortesias());
        assertValor("183.00", x.cobertura().receitaComCusto());
        assertValor("79.91", x.cobertura().percentual());
        assertEquals(2, x.cobertura().itensSemCusto());
    }

    @Test
    @DisplayName("Filtro de categoria e de forma: métricas da venda inteira e despesas ficam nulas")
    void filtros() {
        Resumo caneca = financas.resumo(MARCO.comCategoria(c.get("Caneca").getId()));
        assertValor("85.00", caneca.receitaBruta());
        assertValor("81.67", caneca.receitaLiquida());
        assertValor("34.00", caneca.cmv());
        assertValor("47.67", caneca.margemBruta());
        assertEquals(3, caneca.vendas());
        assertEquals(3, caneca.itensVendidos());
        assertNull(caneca.descontosPromocionais());
        assertNull(caneca.ajustes());
        assertNull(caneca.taxas());
        assertNull(caneca.despesas());
        assertNull(caneca.lucroLiquido());
        assertFalse(caneca.isLucroParcial());

        Resumo pix = financas.resumo(MARCO.comForma(FormaPagamento.PIX));
        assertEquals(3, pix.vendas());
        assertValor("67.00", pix.receitaLiquida());
        assertValor("0.67", pix.taxas());
        assertNull(pix.despesas());
        assertNull(pix.lucroLiquido());
    }

    @Test
    @DisplayName("Comparação: evento anterior, período anterior de mesmo tamanho e evento sem anterior")
    void comparacao() {
        var feira2 = financas.comparar(evento("Feira 2"));
        assertValor("29.00", feira2.atual().receitaLiquida());
        assertValor("135.00", feira2.anterior().receitaLiquida());
        assertEquals("evento Feira 1", feira2.periodoAnterior());
        var receita = feira2.variacoes().stream()
                .filter(v -> v.indicador().equals("receitaLiquida"))
                .findFirst()
                .orElseThrow();
        assertValor("-106.00", receita.diferenca());
        assertValor("-78.52", receita.percentual());
        assertValor("5.37", feira2.atual().lucroLiquido());

        var semana = financas.comparar(FiltroFinancas.periodo(LocalDate.of(2025, 3, 8), LocalDate.of(2025, 3, 14)));
        assertEquals("01/03/2025 a 07/03/2025", semana.periodoAnterior());
        assertValor("200.00", semana.anterior().receitaLiquida());
        var vendasVar = semana.variacoes().stream()
                .filter(v -> v.indicador().equals("vendas"))
                .findFirst()
                .orElseThrow();
        assertValor("-4", vendasVar.diferenca());
        assertValor("-66.67", vendasVar.percentual());

        var feira3 = financas.comparar(evento("Feira 3"));
        assertValor("0.00", feira3.atual().receitaLiquida());
        var r3 = feira3.variacoes().stream()
                .filter(v -> v.indicador().equals("receitaLiquida"))
                .findFirst()
                .orElseThrow();
        assertValor("-100.00", r3.percentual());

        assertNull(financas.comparar(evento("Feira 1")).anterior());
    }

    @Test
    @DisplayName("Série: por hora num evento de 9 h, por dia num período longo, zeros onde não vendeu")
    void series() {
        var feira1 = financas.serie(evento("Feira 1"));
        assertTrue(feira1.porHora());
        assertEquals(9, feira1.pontos().size());
        Ponto nove = feira1.pontos().get(0);
        assertEquals(t("2025-03-01T09:00"), nove.inicio());
        assertValor("15.00", nove.receitaLiquida());
        assertValor("9.00", nove.lucroBruto());
        assertEquals(1, nove.vendas());
        assertValor("-6.00", feira1.pontos().get(3).lucroBruto());
        assertEquals(0, feira1.pontos().get(2).vendas());
        assertValor("40.00", feira1.pontos().get(8).receitaLiquida());
        assertValor(
                "135.00", feira1.pontos().stream().map(Ponto::receitaLiquida).reduce(BigDecimal.ZERO, BigDecimal::add));

        var mes = financas.serie(MARCO);
        assertFalse(mes.porHora());
        assertEquals(15, mes.pontos().size());
        assertValor("160.00", mes.pontos().get(0).receitaLiquida());
        assertValor("101.00", mes.pontos().get(0).lucroBruto());
        assertEquals(5, mes.pontos().get(0).vendas());
        assertValor("0.00", mes.pontos().get(1).receitaLiquida());
        assertValor("40.00", mes.pontos().get(4).receitaLiquida());
        assertValor("29.00", mes.pontos().get(7).receitaLiquida());

        var doisDias = financas.serie(FiltroFinancas.periodo(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 2)));
        assertTrue(doisDias.porHora());
        assertEquals(48, doisDias.pontos().size());
    }

    @Test
    @DisplayName("Mapa de calor: sábado e quarta, por hora")
    void mapaDeCalor() {
        var mapa = financas.mapaDeCalor(MARCO);
        assertEquals(7 * 24, mapa.size());
        var sabado11 = mapa.stream()
                .filter(x -> x.diaDaSemana() == DayOfWeek.SATURDAY && x.hora() == 11)
                .findFirst()
                .orElseThrow();
        assertEquals(1, sabado11.vendas());
        assertValor("12.00", sabado11.receitaLiquida());
        var quarta16 = mapa.stream()
                .filter(x -> x.diaDaSemana() == DayOfWeek.WEDNESDAY && x.hora() == 16)
                .findFirst()
                .orElseThrow();
        assertEquals(1, quarta16.vendas());
        assertValor("40.00", quarta16.receitaLiquida());
        assertEquals(8, mapa.stream().mapToLong(x -> x.vendas()).sum());
    }

    @Test
    @DisplayName("Ranking de produtos por receita, lucro e quantidade, com posição e selo de arquivado")
    void rankingProdutos() {
        var porReceita = financas.rankingProdutos(MARCO, Metrica.RECEITA);
        assertEquals(
                List.of(
                        "Camiseta 1",
                        "Camiseta 3",
                        "Caneca 1",
                        "Caneca 2",
                        "Caneca 3",
                        "Bóton 1",
                        "Pôster 2",
                        "Adesivo 2",
                        "Adesivo 3",
                        "Bóton 2",
                        "Bóton 3",
                        "Adesivo 1"),
                porReceita.stream().map(LinhaProduto::nome).toList());
        LinhaProduto camiseta1 = porReceita.get(0);
        assertEquals(1, camiseta1.posicao());
        assertValor("50.00", camiseta1.receitaLiquida());
        assertValor("25.00", camiseta1.lucroBruto());
        assertValor("50.00", camiseta1.margemPercentual());
        assertValor("21.83", camiseta1.participacaoPercentual());
        LinhaProduto poster2 = porReceita.get(6);
        assertTrue(poster2.arquivado());
        assertValor("13.33", poster2.receitaLiquida());
        assertValor("8.33", poster2.lucroBruto());
        LinhaProduto adesivo1 = porReceita.get(11);
        assertValor("0.00", adesivo1.receitaLiquida());
        assertValor("-6.00", adesivo1.lucroBruto());
        assertNull(adesivo1.margemPercentual());
        assertEquals(1, porReceita.get(1).unidadesSemCusto());

        var porLucro = financas.rankingProdutos(MARCO, Metrica.LUCRO);
        assertEquals("Camiseta 3", porLucro.get(0).nome());
        assertEquals("Adesivo 1", porLucro.get(porLucro.size() - 1).nome());

        var porQuantidade = financas.rankingProdutos(MARCO, Metrica.QUANTIDADE);
        assertEquals(
                List.of("Bóton 1", "Adesivo 1", "Adesivo 3"),
                porQuantidade.subList(0, 3).stream().map(LinhaProduto::nome).toList());
    }

    @Test
    @DisplayName("Categorias com drill-down dos produtos e participação dentro da categoria")
    void rankingCategorias() {
        var linhas = financas.rankingCategorias(MARCO, Metrica.RECEITA);
        assertEquals(
                List.of("Camiseta", "Caneca", "Bóton", "Adesivo", "Pôster"),
                linhas.stream().map(LinhaCategoria::categoria).toList());
        LinhaCategoria caneca = linhas.get(1);
        assertValor("81.67", caneca.receitaLiquida());
        assertValor("34.00", caneca.cmv());
        assertValor("47.67", caneca.lucroBruto());
        assertEquals(3, caneca.vendas());
        assertValor("35.66", caneca.participacaoPercentual());
        assertEquals(
                List.of("Caneca 1", "Caneca 2", "Caneca 3"),
                caneca.produtos().stream().map(LinhaProduto::nome).toList());
        assertValor("36.73", caneca.produtos().get(0).participacaoPercentual());
        assertValor("32.66", caneca.produtos().get(1).participacaoPercentual());
        assertValor("30.61", caneca.produtos().get(2).participacaoPercentual());
        LinhaCategoria boton = linhas.get(2);
        assertValor("27.00", boton.receitaLiquida());
        assertValor("18.50", boton.lucroBruto());
        assertEquals(1, boton.unidadesSemCusto());
    }

    @Test
    @DisplayName("Curva ABC por receita líquida")
    void curvaAbc() {
        var abc = financas.curvaAbc(MARCO);
        assertEquals(
                List.of(
                        ClasseAbc.A,
                        ClasseAbc.A,
                        ClasseAbc.A,
                        ClasseAbc.A,
                        ClasseAbc.A,
                        ClasseAbc.A,
                        ClasseAbc.B,
                        ClasseAbc.B,
                        ClasseAbc.B,
                        ClasseAbc.B,
                        ClasseAbc.C,
                        ClasseAbc.C),
                abc.stream().map(LinhaAbc::classe).toList());
        assertValor("74.97", abc.get(4).acumuladoPercentual());
        assertValor("81.52", abc.get(5).acumuladoPercentual());
        assertValor("94.76", abc.get(8).acumuladoPercentual());
        assertValor("97.38", abc.get(9).acumuladoPercentual());
        assertValor("100.00", abc.get(11).acumuladoPercentual());
    }

    @Test
    @DisplayName("Formas de pagamento pelas partes e promoções (a apagada aparece junta)")
    void formasEPromocoes() {
        var formas = financas.formasDePagamento(MARCO);
        Map<FormaPagamento, LinhaForma> f = new HashMap<>();
        formas.forEach(l -> f.put(l.forma(), l));
        assertValor("67.00", f.get(FormaPagamento.PIX).valor());
        assertEquals(3, f.get(FormaPagamento.PIX).vendas());
        assertValor("0.67", f.get(FormaPagamento.PIX).taxas());
        assertValor("67.00", f.get(FormaPagamento.CREDITO).valor());
        assertValor("2.01", f.get(FormaPagamento.CREDITO).taxas());
        assertValor("55.00", f.get(FormaPagamento.DINHEIRO).valor());
        assertEquals(2, f.get(FormaPagamento.DINHEIRO).vendas());
        assertValor("40.00", f.get(FormaPagamento.DEBITO).valor());
        assertValor("0.90", f.get(FormaPagamento.DEBITO).taxas());
        assertValor("0.00", f.get(FormaPagamento.OUTRO).valor());

        var promos = financas.promocoes(MARCO);
        assertEquals(2, promos.size());
        assertNull(promos.get(0).promocaoId());
        assertEquals("Promoções apagadas", promos.get(0).promocao());
        assertValor("5.00", promos.get(0).descontoTotal());
        assertValor("40.00", promos.get(0).receitaLiquida());
        assertTrue(
                promos.get(1).promocao().startsWith("A partir de: Bóton"),
                promos.get(1).promocao());
        assertEquals(1, promos.get(1).vendas());
        assertValor("3.00", promos.get(1).descontoTotal());
        assertValor("15.00", promos.get(1).receitaLiquida());
    }

    @Test
    @DisplayName("Giro, cobertura e produtos parados (cancelada e arquivado não contam)")
    void estoque() {
        var giro = estoqueFinanceiro.giro(MARCO);
        assertEquals(14, giro.size());
        Map<String, LinhaGiro> g = new HashMap<>();
        giro.forEach(l -> g.put(l.nome(), l));
        LinhaGiro boton1 = g.get("Bóton 1");
        assertEquals(3, boton1.vendidas());
        assertEquals(47, boton1.estoque());
        assertValor("0.20", boton1.mediaPorDia());
        assertValor("235.0", boton1.diasAteAcabar());
        assertValor("94.00", boton1.valorACusto());
        assertValor("282.00", boton1.valorAPreco());
        LinhaGiro boton3 = g.get("Bóton 3");
        assertValor("0.07", boton3.mediaPorDia());
        assertValor("735.0", boton3.diasAteAcabar());
        assertNull(boton3.valorACusto());
        LinhaGiro poster1 = g.get("Pôster 1");
        assertTrue(poster1.semGiro());
        assertNull(poster1.diasAteAcabar());
        assertEquals(50, poster1.estoque());

        assertEquals(
                List.of("Camiseta 2", "Pôster 1", "Pôster 3"),
                estoqueFinanceiro.parados(MARCO).stream().map(LinhaGiro::nome).toList());
        assertEquals(10, estoqueFinanceiro.parados(evento("Feira 2")).size());
    }

    @Test
    @DisplayName("Invariantes: produtos = categorias = resumo = fechamento; formas = receita; canceladas fora")
    void invariantes() {
        List<FiltroFinancas> filtros = List.of(
                MARCO,
                evento("Feira 1"),
                evento("Feira 2"),
                evento("Feira 3"),
                FiltroFinancas.periodo(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 1)),
                FiltroFinancas.periodo(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));
        for (FiltroFinancas f : filtros) {
            BigDecimal receita = financas.resumo(f).receitaLiquida();
            BigDecimal produtosSoma = financas.rankingProdutos(f, Metrica.RECEITA).stream()
                    .map(LinhaProduto::receitaLiquida)
                    .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
            BigDecimal categoriasSoma = financas.rankingCategorias(f, Metrica.RECEITA).stream()
                    .map(LinhaCategoria::receitaLiquida)
                    .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
            BigDecimal formasSoma = financas.formasDePagamento(f).stream()
                    .map(LinhaForma::valor)
                    .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
            var periodo = f.evento() != null
                    ? FiltroHistorico.doEvento(f.evento())
                    : FiltroHistorico.periodo(f.inicio(), f.fim());
            BigDecimal fechado = fechamento.consultar(periodo).total().setScale(2);
            assertEquals(receita, produtosSoma, "produtos " + f);
            assertEquals(receita, categoriasSoma, "categorias " + f);
            assertEquals(receita, fechado, "fechamento " + f);
            assertEquals(receita, formasSoma, "formas " + f);
            assertEquals(
                    financas.resumo(f).margemBruta(),
                    financas.rankingProdutos(f, Metrica.LUCRO).stream()
                            .map(LinhaProduto::lucroBruto)
                            .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add));
        }
        assertTrue(financas
                .rankingProdutos(
                        FiltroFinancas.periodo(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)), Metrica.RECEITA)
                .stream()
                .noneMatch(l -> l.nome().equals("Pôster 1")));
        assertEquals(
                8,
                financas.resumo(FiltroFinancas.periodo(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
                        .vendas());
    }

    @Autowired
    br.com.lac.caixa.servico.ConsultaHistorico historico;

    @Test
    @DisplayName(
            "Dia a dia: V6 (18:00, fora da Feira 1) e V9 (05/03); despesa sem evento; no histórico e no fechamento")
    void diaADia() {
        var agora = java.time.LocalDateTime.now();
        Resumo x = financas.resumo(FiltroFinancas.diaADia(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 15), agora));
        assertValor("65.00", x.receitaLiquida());
        assertEquals(2, x.vendas());
        assertValor("20.00", x.despesas());
        assertValor("27.00", x.cmv());
        var periodo =
                new FiltroHistorico(null, LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 15), "").comDiaADia(agora);
        assertEquals(2, historico.consultar(periodo, 0).pagina().getTotalElements());
        assertValor("65.00", fechamento.consultar(periodo).total().setScale(2));
    }

    @Test
    @DisplayName("Invariante: soma de todos os eventos + dia a dia = total do período (receita, vendas, itens, CMV)")
    void eventosMaisDiaADia() {
        var agora = java.time.LocalDateTime.now();
        Resumo total = financas.resumo(MARCO);
        Resumo dia =
                financas.resumo(FiltroFinancas.diaADia(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 15), agora));
        BigDecimal receita = dia.receitaLiquida();
        BigDecimal cmv = dia.cmv();
        long vendas = dia.vendas();
        long itens = dia.itensVendidos();
        for (String nome : List.of("Feira 1", "Feira 2", "Feira 3")) {
            Resumo r = financas.resumo(evento(nome));
            receita = receita.add(r.receitaLiquida());
            cmv = cmv.add(r.cmv());
            vendas += r.vendas();
            itens += r.itensVendidos();
        }
        assertEquals(total.receitaLiquida(), receita);
        assertEquals(total.cmv(), cmv);
        assertEquals(total.vendas(), vendas);
        assertEquals(total.itensVendidos(), itens);

        var linhas = comparativo.eventos();
        var ultimo = linhas.get(linhas.size() - 1);
        assertEquals("Dia a dia", ultimo.nome());
        assertEquals(LocalDate.of(2025, 3, 1).atStartOfDay(), ultimo.inicio());
        assertEquals(LocalDate.of(2025, 3, 16).atStartOfDay(), ultimo.fim());
        assertValor("65.00", ultimo.resumo().receitaLiquida());
    }

    @Test
    @DisplayName("Despesa: uma regra só. A ligada a evento conta no dia em que ele começa; a sem evento, pela data")
    void despesaUmaRegra() {
        assertValor(
                "0.00",
                financas.resumo(FiltroFinancas.periodo(LocalDate.of(2025, 2, 28), LocalDate.of(2025, 2, 28)))
                        .despesas());
        assertValor(
                "80.00",
                financas.resumo(FiltroFinancas.periodo(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 1)))
                        .despesas());
        var linhas = comparativo.eventos();
        BigDecimal soma =
                linhas.stream().map(l -> l.resumo().despesas()).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        var dia = linhas.get(linhas.size() - 1);
        assertEquals(
                financas.resumo(FiltroFinancas.periodo(
                                dia.inicio().toLocalDate(),
                                dia.fim().minusNanos(1).toLocalDate()))
                        .despesas(),
                soma);
        assertValor("115.50", soma);
    }

    @Test
    @DisplayName("Filtro de forma: a tabela de formas e as taxas só com as partes dessa forma")
    void filtroDeFormaRecortaAsPartes() {
        var credito = MARCO.comForma(FormaPagamento.CREDITO);
        Map<FormaPagamento, LinhaForma> f = new HashMap<>();
        financas.formasDePagamento(credito).forEach(l -> f.put(l.forma(), l));
        assertValor("67.00", f.get(FormaPagamento.CREDITO).valor());
        assertValor("0.00", f.get(FormaPagamento.DINHEIRO).valor());
        assertValor("100.00", f.get(FormaPagamento.CREDITO).participacaoPercentual());
        assertValor("2.01", financas.resumo(credito).taxas());
    }

    @Test
    @DisplayName("Filtro de categoria: sem tabela de formas, e a promoção conta só a receita dos itens da categoria")
    void filtroDeCategoria() {
        var caneca = MARCO.comCategoria(c.get("Caneca").getId());
        assertTrue(financas.formasDePagamento(caneca).isEmpty());
        var promos = financas.promocoes(caneca);
        assertEquals(1, promos.size());
        assertValor("26.67", promos.get(0).receitaLiquida());
    }

    @Test
    @DisplayName("Relatório da Feira 1: a cancelada (V4) e os ajustes manuais (V3 e V5), com o motivo")
    void relatorioDoEvento() {
        var cancelamentos = relatorio.cancelamentos(evento("Feira 1"));
        assertEquals(1, cancelamentos.size());
        assertValor("30.00", cancelamentos.get(0).valor());
        assertEquals("Ensaio", cancelamentos.get(0).motivo());
        var ajustes = relatorio.ajustesManuais(evento("Feira 1"));
        assertEquals(2, ajustes.size());
        assertValor("-16.00", ajustes.get(0).diferenca());
        assertValor("-5.00", ajustes.get(1).diferenca());
    }
}
