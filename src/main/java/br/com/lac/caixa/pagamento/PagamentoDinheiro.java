package br.com.lac.caixa.pagamento;

import br.com.lac.caixa.util.Dinheiro;

import java.math.BigDecimal;

public class PagamentoDinheiro implements Pagamento {
    @Override
    public String pagar(BigDecimal valor) {
        return "Recebido " + Dinheiro.reais(valor) + " em dinheiro.";
    }
}
