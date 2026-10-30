package br.com.lac.caixa.pagamento;

import java.math.BigDecimal;

public class PagamentoDebito implements Pagamento {
    @Override
    public String pagar(BigDecimal valor) {
        return "Recebido " + br.com.lac.caixa.util.Dinheiro.reais(valor) + " no débito.";
    }
}
