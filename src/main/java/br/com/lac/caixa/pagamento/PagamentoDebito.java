package br.com.lac.caixa.pagamento;

import java.math.BigDecimal;

public class PagamentoDebito implements Pagamento {
    @Override
    public String pagar(BigDecimal valor) {
        return "Recebido R$ " + valor + " no débito.";
    }
}
