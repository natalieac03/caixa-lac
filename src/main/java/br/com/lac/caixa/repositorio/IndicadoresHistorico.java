package br.com.lac.caixa.repositorio;

import java.math.BigDecimal;

/** Indicadores do topo do histórico: somam o filtro inteiro, não só a página. */
public record IndicadoresHistorico(long vendas, long itens, BigDecimal total) {
    public long getVendas() { return vendas; }
    public long getItens() { return itens; }
    public BigDecimal getTotal() { return total; }
}
