package br.com.lac.caixa.pagamento;

import br.com.lac.caixa.modelo.PagamentoVenda;
import br.com.lac.caixa.util.Dinheiro;

public class PagamentoPix implements Pagamento {
    @Override
    public String descrever(PagamentoVenda parte) {
        return Dinheiro.reais(parte.getValor()) + " via Pix";
    }
}
