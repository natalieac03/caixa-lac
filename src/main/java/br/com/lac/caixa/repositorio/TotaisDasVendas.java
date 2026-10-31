package br.com.lac.caixa.repositorio;

import java.math.BigDecimal;

/** Totais pela venda: quantidade, cobrado, descontos e ajustes. */
public record TotaisDasVendas(long vendas, BigDecimal cobrado, BigDecimal descontos, BigDecimal ajustes) {}
