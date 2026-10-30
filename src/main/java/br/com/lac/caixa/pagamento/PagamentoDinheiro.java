package br.com.lac.caixa.pagamento;

import java.math.BigDecimal;

public class PagamentoDinheiro implements Pagamento {
    @Override
    public String pagar(BigDecimal valor) {
        return "Recebido " + br.com.lac.caixa.util.Dinheiro.reais(valor) + " em dinheiro.";
    }
}
