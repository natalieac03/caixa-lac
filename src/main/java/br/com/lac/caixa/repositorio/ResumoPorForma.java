package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.FormaPagamento;
import java.math.BigDecimal;

public interface ResumoPorForma {
    FormaPagamento getForma();
    long getQuantidade();
    BigDecimal getTotal();
    BigDecimal getDescontos();
    BigDecimal getAjustes();
}
