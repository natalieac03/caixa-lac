package br.com.lac.caixa.servico.financas;

import br.com.lac.caixa.modelo.FormaPagamento;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

/** Os resultados das finanças: resumo, série, rankings, formas, giro, demonstrativo e metas. */
public final class Financas {
    private Financas() {}

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

        public boolean isLucroParcial() {
            return cobertura.parcial();
        }
    }

    public record Cobertura(
            BigDecimal receitaComCusto,
            BigDecimal receitaSemCusto,
            BigDecimal percentual,
            long itensSemCusto,
            boolean parcial) {

        public BigDecimal percentualSemCusto() {
            return percentual == null
                    ? null
                    : BigDecimal.valueOf(100).setScale(2).subtract(percentual);
        }
    }

    public record Comparacao(Resumo atual, Resumo anterior, String periodoAnterior, List<Variacao> variacoes) {}

    public record Variacao(
            String indicador, BigDecimal atual, BigDecimal anterior, BigDecimal diferenca, BigDecimal percentual) {}

    public record Ponto(LocalDateTime inicio, BigDecimal receitaLiquida, BigDecimal lucroBruto, long vendas) {}

    public enum Granularidade {
        HORA("por hora"),
        DIA("por dia"),
        SEMANA("por semana"),
        MES("por mês");

        private final String rotulo;

        Granularidade(String rotulo) {
            this.rotulo = rotulo;
        }

        public String getRotulo() {
            return rotulo;
        }
    }

    public record Serie(Granularidade granularidade, List<Ponto> pontos) {
        public boolean porHora() {
            return granularidade == Granularidade.HORA;
        }
    }

    public record Celula(DayOfWeek diaDaSemana, int hora, long vendas, BigDecimal receitaLiquida) {}

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

    public record LinhaPromocao(
            Long promocaoId, String promocao, long vendas, BigDecimal descontoTotal, BigDecimal receitaLiquida) {}

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

    public record ValorEstoque(BigDecimal aCusto, BigDecimal aPreco, long produtosSemCusto) {}

    public record LinhaDemonstrativo(
            String rotulo, String operador, BigDecimal valor, BigDecimal percentualDaReceita, boolean resultado) {}

    public record LinhaCancelamento(
            Long vendaId, LocalDateTime dataHora, BigDecimal valor, LocalDateTime canceladaEm, String motivo) {}

    public record LinhaAjuste(
            Long vendaId,
            LocalDateTime dataHora,
            BigDecimal calculado,
            BigDecimal cobrado,
            BigDecimal diferenca,
            String motivo) {}

    public record ResumoDoEvento(Long eventoId, String nome, LocalDateTime inicio, LocalDateTime fim, Resumo resumo) {}

    public record Meta(
            String id,
            String rotulo,
            BigDecimal meta,
            BigDecimal atual,
            BigDecimal percentual,
            BigDecimal barra,
            boolean atingida,
            boolean parcial) {}
}
