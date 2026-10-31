package br.com.lac.caixa.modelo.pagamento;

import br.com.lac.caixa.modelo.PagamentoVenda;
import br.com.lac.caixa.util.Dinheiro;

public class PagamentoDebito implements Pagamento {
    @Override
    public String descrever(PagamentoVenda parte) {
        return Dinheiro.reais(parte.getValor()) + " no débito";
    }
}
