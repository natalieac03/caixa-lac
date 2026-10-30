package br.com.lac.caixa.repositorio;

import java.math.BigDecimal;

public interface IndicadoresHistorico {
    long getVendas();
    long getItens();
    BigDecimal getTotal();
}
