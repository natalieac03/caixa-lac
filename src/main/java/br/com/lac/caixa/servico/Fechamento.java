package br.com.lac.caixa.servico;

import br.com.lac.caixa.modelo.FormaPagamento;
import java.math.BigDecimal;
import java.util.List;

/** Ajustes são assinados: cobrado menos calculado; legados sem cálculo contam zero. */
public record Fechamento(List<Linha> formas, long quantidade, BigDecimal total, BigDecimal descontos, BigDecimal ajustes) {
    public Fechamento { formas = List.copyOf(formas); }
    public record Linha(FormaPagamento forma, long quantidade, BigDecimal total, BigDecimal descontos, BigDecimal ajustes) {}
}
