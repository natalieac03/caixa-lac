package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.FormaPagamento;

import java.math.BigDecimal;

public record ResumoPorForma(FormaPagamento forma, long quantidade, BigDecimal total,
                             BigDecimal descontos, BigDecimal ajustes) {
}
