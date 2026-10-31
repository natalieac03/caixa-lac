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

/**
 * Massa fixa das finanças, com os resultados calculados à mão abaixo. Roda no H2
 * (FinancasH2Test) e no Postgres (FinancasPostgresTest).
 *
 * <pre>
 * Categorias e produtos (estoque inicial 50 cada; custo entre parênteses; "-" = sem custo):
 *   Bóton:    Bóton 1 6,00 (2,00)   Bóton 2 6,00 (2,50)   Bóton 3 6,00 (-)
 *   Adesivo:  Adesivo 1 8,00 (3,00) Adesivo 2 8,00 (3,00) Adesivo 3 4,00 (1,00)
 *   Camiseta: Camiseta 1 50 (25)    Camiseta 2 50 (30)    Camiseta 3 45 (-)
 *   Caneca:   Caneca 1 30 (12)      Caneca 2 30 (12)      Caneca 3 25 (10)
 *   Pôster:   Pôster 1 15 (5)       Pôster 2 15 (5)       Pôster 3 10 (2)
 * Promoções: A_PARTIR_DE 3 bótons por 5,00 cada; PACOTE 1 pôster + 1 caneca por 40,00 (apagada no fim).
 * Taxas (hoje): crédito 3%; débito 2% + 0,10; Pix 1%; dinheiro sem taxa.
 * Eventos: Feira 1 01/03/2025 09:00-18:00; Feira 2 08/03 09:00-18:00; Feira 3 15/03 09:00-18:00.
 *
 * Vendas (bruto -> cobrado; CMV; taxa):
 *   V1 01/03 09:00 Bóton 1 x3, promoção: 18 -> 15, Pix; CMV 6; taxa 0,15
 *   V2 01/03 10:30 Camiseta 1 + Caneca 1: 80, dividida crédito 50 + dinheiro 30; CMV 37; taxa 1,50
 *   V3 01/03 12:15 Adesivo 1 x2: 16 -> 0, cortesia; CMV 6
 *   V4 01/03 14:00 Pôster 1 x2: 30, Pix, CANCELADA (fica fora de tudo)
 *   V5 01/03 17:59 Camiseta 3 (sem custo): 45 -> 40, ajuste manual, débito; taxa 0,90
 *   V6 01/03 18:00 Caneca 3: 25, dinheiro; CMV 10 (18:00 já é fora da Feira 1)
 *   V9 05/03 16:20 Pôster 2 + Caneca 2: 45 -> 40, pacote (rateio 1,67 + 3,33), Pix; CMV 17; taxa 0,40
 *   V7 08/03 11:00 Bóton 2 + Bóton 3 (sem custo): 12, Pix (2 bótons, sem promoção); CMV 2,50; taxa 0,12
 *   V8 08/03 15:00 Adesivo 2 + Adesivo 3 x2: 16 -> 17, ajuste para mais, crédito; CMV 5; taxa 0,51
 * Despesas: 28/02 Lona 30 (Feira 1); 01/03 Mesa 50 (Feira 1); 05/03 Uber 20 (sem evento); 08/03 Lanche 15,50 (Feira 2).
 * Depois das vendas, o Pôster 2 é arquivado.
 * </pre>
 */
abstract class MassaFinanceira {
    @Autowired
    FinancasService financas;

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

    // ------------------------------------------------------------ A: resumo

    @Test
    @DisplayName("Resumo da Feira 1: V1, V2, V3 e V5 (V4 cancelada, V6 às 18:00 fora)")
    void resumoDaFeira1() {
        Resumo x = financas.resumo(evento("Feira 1"));
        assertValor("159.00", x.receitaBruta()); // 18 + 80 + 16 + 45
        assertValor("3.00", x.descontosPromocionais()); // V1
        assertValor("-21.00", x.ajustes()); // V3 -16, V5 -5
        assertValor("135.00", x.receitaLiquida()); // 15 + 80 + 0 + 40
        assertValor("2.55", x.taxas()); // 0,15 + 1,50 + 0,90
        assertValor("49.00", x.cmv()); // 6 + 37 + 6
        assertValor("86.00", x.margemBruta());
        assertValor("63.70", x.margemBrutaPercentual()); // 86 / 135
        assertValor("80.00", x.despesas()); // Lona (véspera, ligada à feira) + Mesa
        assertValor("3.45", x.lucroLiquido()); // 135 - 2,55 - 49 - 80
        assertValor("2.56", x.lucroLiquidoPercentual()); // 3,45 / 135 = 2,5555...
        assertEquals(4, x.vendas());
        assertEquals(8, x.itensVendidos()); // 3 + 2 + 2 + 1
        assertValor("33.75", x.ticketMedio());
        assertValor("2.00", x.itensPorVenda());
        assertEquals(1, x.cortesias());
        // Cobertura: a Camiseta 3 (R$ 40) não tem custo.
        assertValor("95.00", x.cobertura().receitaComCusto());
        assertValor("40.00", x.cobertura().receitaSemCusto());
        assertValor("70.37", x.cobertura().percentual());
        assertTrue(x.isLucroParcial());
        assertEquals(0, x.vendasSemItens());
    }

    @Test
    @DisplayName("Resumo de 01 a 15/03: todas as vendas válidas, despesas pela data")
    void resumoDoMes() {
        Resumo x = financas.resumo(MARCO);
        assertValor("257.00", x.receitaBruta());
        assertValor("8.00", x.descontosPromocionais()); // 3 (V1) + 5 (pacote V9)
        assertValor("-20.00", x.ajustes()); // -16 - 5 + 1
        assertValor("229.00", x.receitaLiquida()); // 257 - 8 - 20
        assertValor("3.58", x.taxas());
        assertValor("83.50", x.cmv()); // 6 + 37 + 6 + 10 + 17 + 2,50 + 5
        assertValor("145.50", x.margemBruta());
        assertValor("63.54", x.margemBrutaPercentual());
        assertValor("85.50", x.despesas()); // Mesa 50 + Uber 20 + Lanche 15,50 (a Lona é de 28/02)
        assertValor("56.42", x.lucroLiquido()); // 229 - 3,58 - 83,50 - 85,50
        assertValor("24.64", x.lucroLiquidoPercentual());
        assertEquals(8, x.vendas());
        assertEquals(16, x.itensVendidos());
        assertValor("28.62", x.ticketMedio()); // 28,625 (meio para o par)
        assertValor("2.00", x.itensPorVenda());
        assertEquals(1, x.cortesias());
        assertValor("183.00", x.cobertura().receitaComCusto()); // sem custo: Camiseta 3 40 + Bóton 3 6
        assertValor("79.91", x.cobertura().percentual());
        assertEquals(2, x.cobertura().itensSemCusto());
    }

    @Test
    @DisplayName("Filtro de categoria e de forma: métricas da venda inteira e despesas ficam nulas")
    void filtros() {
        Resumo caneca = financas.resumo(MARCO.comCategoria(c.get("Caneca").getId()));
        assertValor("85.00", caneca.receitaBruta()); // 30 + 25 + 30
        assertValor("81.67", caneca.receitaLiquida()); // 30 + 25 + 26,67
        assertValor("34.00", caneca.cmv());
        assertValor("47.67", caneca.margemBruta());
        assertEquals(3, caneca.vendas()); // V2, V6, V9
        assertEquals(3, caneca.itensVendidos());
        assertNull(caneca.descontosPromocionais());
        assertNull(caneca.ajustes());
        assertNull(caneca.taxas());
        assertNull(caneca.despesas());
        assertNull(caneca.lucroLiquido());
        assertFalse(caneca.isLucroParcial());

        Resumo pix = financas.resumo(MARCO.comForma(FormaPagamento.PIX));
        assertEquals(3, pix.vendas()); // V1, V7, V9 (V4 cancelada)
        assertValor("67.00", pix.receitaLiquida());
        assertValor("0.67", pix.taxas());
        assertNull(pix.despesas());
        assertNull(pix.lucroLiquido());
    }

    @Test
    @DisplayName("Comparação: evento anterior, período anterior de mesmo tamanho e evento sem anterior")
    void comparacao() {
        var feira2 = financas.comparar(evento("Feira 2"));
        assertValor("29.00", feira2.atual().receitaLiquida()); // V7 12 + V8 17
        assertValor("135.00", feira2.anterior().receitaLiquida()); // Feira 1
        assertEquals("evento Feira 1", feira2.periodoAnterior());
        var receita = feira2.variacoes().stream()
                .filter(v -> v.indicador().equals("receitaLiquida"))
                .findFirst()
                .orElseThrow();
        assertValor("-106.00", receita.diferenca());
        assertValor("-78.52", receita.percentual());
        assertValor("5.37", feira2.atual().lucroLiquido()); // 29 - 0,63 - 7,50 - 15,50

        var semana = financas.comparar(FiltroFinancas.periodo(LocalDate.of(2025, 3, 8), LocalDate.of(2025, 3, 14)));
        assertEquals("01/03/2025 a 07/03/2025", semana.periodoAnterior());
        assertValor("200.00", semana.anterior().receitaLiquida()); // 01/03 (160) + 05/03 (40)
        var vendasVar = semana.variacoes().stream()
                .filter(v -> v.indicador().equals("vendas"))
                .findFirst()
                .orElseThrow();
        assertValor("-4", vendasVar.diferenca()); // 2 - 6
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

    // ------------------------------------------------------------ B: séries

    @Test
    @DisplayName("Série: por hora num evento de 9 h, por dia num período longo, zeros onde não vendeu")
    void series() {
        var feira1 = financas.serie(evento("Feira 1"));
        assertTrue(feira1.porHora());
        assertEquals(9, feira1.pontos().size()); // 09h a 17h
        Ponto nove = feira1.pontos().get(0);
        assertEquals(t("2025-03-01T09:00"), nove.inicio());
        assertValor("15.00", nove.receitaLiquida());
        assertValor("9.00", nove.lucroBruto());
        assertEquals(1, nove.vendas());
        assertValor("-6.00", feira1.pontos().get(3).lucroBruto()); // 12h: cortesia com custo 6
        assertEquals(0, feira1.pontos().get(2).vendas()); // 11h
        assertValor("40.00", feira1.pontos().get(8).receitaLiquida()); // 17h
        assertValor(
                "135.00", feira1.pontos().stream().map(Ponto::receitaLiquida).reduce(BigDecimal.ZERO, BigDecimal::add));

        var mes = financas.serie(MARCO);
        assertFalse(mes.porHora());
        assertEquals(15, mes.pontos().size());
        assertValor("160.00", mes.pontos().get(0).receitaLiquida());
        assertValor("101.00", mes.pontos().get(0).lucroBruto()); // 160 - 59
        assertEquals(5, mes.pontos().get(0).vendas());
        assertValor("0.00", mes.pontos().get(1).receitaLiquida());
        assertValor("40.00", mes.pontos().get(4).receitaLiquida()); // 05/03
        assertValor("29.00", mes.pontos().get(7).receitaLiquida()); // 08/03

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
        assertEquals(1, sabado11.vendas()); // V7 (08/03 11:00)
        assertValor("12.00", sabado11.receitaLiquida());
        var quarta16 = mapa.stream()
                .filter(x -> x.diaDaSemana() == DayOfWeek.WEDNESDAY && x.hora() == 16)
                .findFirst()
                .orElseThrow();
        assertEquals(1, quarta16.vendas());
        assertValor("40.00", quarta16.receitaLiquida());
        assertEquals(8, mapa.stream().mapToLong(x -> x.vendas()).sum());
    }

    // ------------------------------------------------------------ C: rankings

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
        assertValor("21.83", camiseta1.participacaoPercentual()); // 50 / 229
        LinhaProduto poster2 = porReceita.get(6);
        assertTrue(poster2.arquivado());
        assertValor("13.33", poster2.receitaLiquida());
        assertValor("8.33", poster2.lucroBruto());
        LinhaProduto adesivo1 = porReceita.get(11);
        assertValor("0.00", adesivo1.receitaLiquida());
        assertValor("-6.00", adesivo1.lucroBruto());
        assertNull(adesivo1.margemPercentual());
        assertEquals(1, porReceita.get(1).unidadesSemCusto()); // Camiseta 3

        var porLucro = financas.rankingProdutos(MARCO, Metrica.LUCRO);
        assertEquals("Camiseta 3", porLucro.get(0).nome()); // 40 (sem custo: lucro = receita)
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
        assertValor("35.66", caneca.participacaoPercentual()); // 81,67 / 229
        assertEquals(
                List.of("Caneca 1", "Caneca 2", "Caneca 3"),
                caneca.produtos().stream().map(LinhaProduto::nome).toList());
        assertValor("36.73", caneca.produtos().get(0).participacaoPercentual()); // 30 / 81,67
        assertValor("32.66", caneca.produtos().get(1).participacaoPercentual()); // 26,67 / 81,67
        assertValor("30.61", caneca.produtos().get(2).participacaoPercentual()); // 25 / 81,67
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
        assertValor("74.97", abc.get(4).acumuladoPercentual()); // 171,67 / 229
        assertValor("81.52", abc.get(5).acumuladoPercentual()); // Bóton 1: antes 74,97 -> A
        assertValor("94.76", abc.get(8).acumuladoPercentual());
        assertValor("97.38", abc.get(9).acumuladoPercentual()); // Bóton 2: antes 94,76 -> B
        assertValor("100.00", abc.get(11).acumuladoPercentual());
    }

    @Test
    @DisplayName("Formas de pagamento pelas partes e promoções (a apagada aparece junta)")
    void formasEPromocoes() {
        var formas = financas.formasDePagamento(MARCO);
        Map<FormaPagamento, LinhaForma> f = new HashMap<>();
        formas.forEach(l -> f.put(l.forma(), l));
        assertValor("67.00", f.get(FormaPagamento.PIX).valor()); // 15 + 12 + 40
        assertEquals(3, f.get(FormaPagamento.PIX).vendas());
        assertValor("0.67", f.get(FormaPagamento.PIX).taxas());
        assertValor("67.00", f.get(FormaPagamento.CREDITO).valor()); // 50 + 17
        assertValor("2.01", f.get(FormaPagamento.CREDITO).taxas());
        assertValor("55.00", f.get(FormaPagamento.DINHEIRO).valor()); // 30 + 25
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

    // ------------------------------------------------------------ D: estoque

    @Test
    @DisplayName("Giro, cobertura e produtos parados (cancelada e arquivado não contam)")
    void estoque() {
        var giro = financas.giro(MARCO);
        assertEquals(14, giro.size()); // Pôster 2 arquivado
        Map<String, LinhaGiro> g = new HashMap<>();
        giro.forEach(l -> g.put(l.nome(), l));
        LinhaGiro boton1 = g.get("Bóton 1");
        assertEquals(3, boton1.vendidas());
        assertEquals(47, boton1.estoque());
        assertValor("0.20", boton1.mediaPorDia()); // 3 / 15 dias
        assertValor("235.0", boton1.diasAteAcabar()); // 47 / 0,2
        assertValor("94.00", boton1.valorACusto());
        assertValor("282.00", boton1.valorAPreco());
        LinhaGiro boton3 = g.get("Bóton 3");
        assertValor("0.07", boton3.mediaPorDia());
        assertValor("735.0", boton3.diasAteAcabar());
        assertNull(boton3.valorACusto());
        LinhaGiro poster1 = g.get("Pôster 1");
        assertTrue(poster1.semGiro()); // só a venda cancelada
        assertNull(poster1.diasAteAcabar());
        assertEquals(50, poster1.estoque());

        assertEquals(
                List.of("Camiseta 2", "Pôster 1", "Pôster 3"),
                financas.parados(MARCO).stream().map(LinhaGiro::nome).toList());
        assertEquals(10, financas.parados(evento("Feira 2")).size()); // 14 ativos - 4 vendidos na Feira 2
    }

    // ------------------------------------------------------------ invariantes

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
            // Lucro bruto por produto soma a margem bruta do resumo.
            assertEquals(
                    financas.resumo(f).margemBruta(),
                    financas.rankingProdutos(f, Metrica.LUCRO).stream()
                            .map(LinhaProduto::lucroBruto)
                            .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add));
        }
        // Canceladas nunca entram: o Pôster 1 só tem a venda cancelada.
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
        assertValor("65.00", x.receitaLiquida()); // 25 + 40
        assertEquals(2, x.vendas());
        assertValor("20.00", x.despesas()); // Uber, sem evento (Lona e Mesa são da Feira 1, Lanche da Feira 2)
        assertValor("27.00", x.cmv()); // 10 + 17
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
        assertEquals(total.receitaLiquida(), receita); // 135 + 29 + 0 + 65 = 229
        assertEquals(total.cmv(), cmv);
        assertEquals(total.vendas(), vendas);
        assertEquals(total.itensVendidos(), itens);

        // O comparativo traz o dia a dia como coluna própria, no mesmo intervalo total (01/03 a 15/03)
        var comparativo = financas.comparativoDeEventos();
        var ultimo = comparativo.get(comparativo.size() - 1);
        assertEquals("Dia a dia", ultimo.nome());
        assertEquals(LocalDate.of(2025, 3, 1).atStartOfDay(), ultimo.inicio());
        assertEquals(LocalDate.of(2025, 3, 16).atStartOfDay(), ultimo.fim());
        assertValor("65.00", ultimo.resumo().receitaLiquida());
    }
}
