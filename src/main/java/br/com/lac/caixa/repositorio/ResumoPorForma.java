package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.FormaPagamento;

import java.math.BigDecimal;

/** Soma das partes de pagamento numa forma; vendas = vendas com alguma parte nela. */
public record ResumoPorForma(FormaPagamento forma, long vendas, BigDecimal total) {
}
