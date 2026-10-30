package br.com.lac.caixa.repositorio;

import java.math.BigDecimal;

/** Totais pela venda (não pelas partes do pagamento). Ajuste = cobrado - calculado, com sinal. */
public record TotaisDasVendas(long vendas, BigDecimal cobrado, BigDecimal descontos, BigDecimal ajustes) {
}
