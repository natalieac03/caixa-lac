package br.com.lac.caixa.pagamento;

import java.math.BigDecimal;

public class PagamentoPix implements Pagamento {
    @Override
    public String pagar(BigDecimal valor) {
        return "Recebido R$ " + valor + " via Pix.";
    }
}
