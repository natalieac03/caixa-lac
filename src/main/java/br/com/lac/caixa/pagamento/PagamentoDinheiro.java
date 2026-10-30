package br.com.lac.caixa.pagamento;

import java.math.BigDecimal;

public class PagamentoDinheiro implements Pagamento {
    @Override
    public String pagar(BigDecimal valor) {
        return "Recebido R$ " + valor + " em dinheiro.";
    }
}
