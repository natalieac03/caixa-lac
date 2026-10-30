package br.com.lac.caixa.pagamento;

import java.math.BigDecimal;

public class PagamentoCredito implements Pagamento {
    @Override
    public String pagar(BigDecimal valor) {
        return "Recebido R$ " + valor + " no crédito.";
    }
}
