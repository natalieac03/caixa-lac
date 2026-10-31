package br.com.lac.caixa.modelo.pagamento;

import br.com.lac.caixa.modelo.PagamentoVenda;
import br.com.lac.caixa.util.Dinheiro;

/** Descrição da parte paga no crédito. */
public class PagamentoCredito implements Pagamento {
    @Override
    public String descrever(PagamentoVenda parte) {
        return Dinheiro.reais(parte.getValor()) + " no crédito";
    }
}
