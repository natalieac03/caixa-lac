package br.com.lac.caixa.servico.financas;

import br.com.lac.caixa.modelo.FormaPagamento;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Os resultados das finanças. Dinheiro com 2 casas; percentuais em pontos
 * (12.50 = 12,5%) com 2 casas; null quando não se aplica (divisão por zero,
 * despesas com filtro de categoria ou forma, produto sem custo...).
 */
public final class Financas {
    private Financas() {}

    /**
     * Resumo de um período. receitaLiquida = soma dos líquidos dos itens (sem
     * filtro de categoria, igual à soma do cobrado das vendas com itens).
     * descontosPromocionais e ajustes são da venda inteira: null com filtro de
     * categoria. taxas: null com filtro de categoria. despesas, lucroLiquido e
     * lucroLiquidoPercentual: null com filtro de categoria ou de forma.
     */
    public record Resumo(
            BigDecimal receitaBruta,
            BigDecimal descontosPromocionais,
            BigDecimal ajustes,
            BigDecimal receitaLiquida,
            BigDecimal taxas,
            BigDecimal cmv,
            BigDecimal margemBruta,
            BigDecimal margemBrutaPercentual,
            BigDecimal despesas,
            BigDecimal lucroLiquido,
            BigDecimal lucroLiquidoPercentual,
            long vendas,
            long itensVendidos,
            BigDecimal ticketMedio,
            BigDecimal itensPorVenda,
            long cortesias,
            Cobertura cobertura,
            long vendasSemItens,
            BigDecimal valorVendasSemItens) {

        /** Lucro "parcial": algum item vendido sem custo, então o CMV está incompleto. */
        public boolean isLucroParcial() {
            return cobertura.parcial();
        }
    }

    /**
     * Cobertura do custo: quanto da receita líquida veio de itens com custo.
     * parcial = algum item vendido sem custo (mesmo uma cortesia, cujo CMV falta).
     */
    public record Cobertura(
            BigDecimal receitaComCusto,
            BigDecimal receitaSemCusto,
            BigDecimal percentual,
            long itensSemCusto,
            boolean parcial) {

        /** % da receita líquida vinda de itens sem custo (100 - percentual); null sem receita. */
        public BigDecimal percentualSemCusto() {
            return percentual == null
                    ? null
                    : BigDecimal.valueOf(100).setScale(2).subtract(percentual);
        }
    }

    /** Os mesmos números no período anterior de mesmo tamanho (ou no evento anterior). */
    public record Comparacao(Resumo atual, Resumo anterior, String periodoAnterior, List<Variacao> variacoes) {}

    /** diferenca = atual - anterior; percentual sobre o anterior (null se anterior zero ou ausente). */
    public record Variacao(
            String indicador, BigDecimal atual, BigDecimal anterior, BigDecimal diferenca, BigDecimal percentual) {}

    /** Um ponto da série: o início do dia (ou da hora), receita líquida, lucro bruto e vendas. */
    public record Ponto(LocalDateTime inicio, BigDecimal receitaLiquida, BigDecimal lucroBruto, long vendas) {}

    public record Serie(boolean porHora, List<Ponto> pontos) {}

    /** Mapa de calor: dia da semana x hora do dia (todas as 7 x 24 células). */
    public record Celula(DayOfWeek diaDaSemana, int hora, long vendas, BigDecimal receitaLiquida) {}

    /** Linha do ranking de produtos. lucroBruto = receita líquida - CMV; semCusto = unidades vendidas sem custo. */
    public record LinhaProduto(
            int posicao,
            Long produtoId,
            String nome,
            String categoria,
            boolean arquivado,
            long quantidade,
            BigDecimal receitaLiquida,
            BigDecimal cmv,
            BigDecimal lucroBruto,
            BigDecimal margemPercentual,
            BigDecimal participacaoPercentual,
            long unidadesSemCusto) {}

    /** Categoria com as mesmas métricas e o ranking dos seus produtos (participação dentro da categoria). */
    public record LinhaCategoria(
            int posicao,
            Long categoriaId,
            String categoria,
            long quantidade,
            long vendas,
            BigDecimal receitaLiquida,
            BigDecimal cmv,
            BigDecimal lucroBruto,
            BigDecimal margemPercentual,
            BigDecimal participacaoPercentual,
            long unidadesSemCusto,
            List<LinhaProduto> produtos) {}

    public enum ClasseAbc {
        A,
        B,
        C
    }

    /** acumuladoPercentual inclui o produto; a classe vem do acumulado ANTES dele (o primeiro é sempre A). */
    public record LinhaAbc(
            int posicao,
            Long produtoId,
            String nome,
            BigDecimal receitaLiquida,
            BigDecimal participacaoPercentual,
            BigDecimal acumuladoPercentual,
            ClasseAbc classe) {}

    public record LinhaForma(
            FormaPagamento forma, BigDecimal valor, long vendas, BigDecimal taxas, BigDecimal participacaoPercentual) {}

    /** promocaoId null = promoções já apagadas (juntas). */
    public record LinhaPromocao(
            Long promocaoId, String promocao, long vendas, BigDecimal descontoTotal, BigDecimal receitaLiquida) {}

    /**
     * Giro: vendas médias por dia no período, estoque de agora e dias até
     * acabar (null + semGiro quando não vendeu). Valor do estoque a custo
     * (null sem custo) e a preço.
     */
    public record LinhaGiro(
            Long produtoId,
            String nome,
            String categoria,
            long vendidas,
            BigDecimal mediaPorDia,
            int estoque,
            BigDecimal diasAteAcabar,
            boolean semGiro,
            BigDecimal valorACusto,
            BigDecimal valorAPreco) {}

    public enum Metrica {
        QUANTIDADE,
        RECEITA,
        LUCRO,
        MARGEM,
        PARTICIPACAO
    }

    /** Valor do estoque de agora: a custo (só produtos com custo), a preço e quantos têm estoque sem custo. */
    public record ValorEstoque(BigDecimal aCusto, BigDecimal aPreco, long produtosSemCusto) {}

    /** Linha do demonstrativo: operador "(-)", "(+/-)", "=" ou ""; valor null = não se aplica ao filtro. */
    public record LinhaDemonstrativo(
            String rotulo, String operador, BigDecimal valor, BigDecimal percentualDaReceita, boolean resultado) {}
}
