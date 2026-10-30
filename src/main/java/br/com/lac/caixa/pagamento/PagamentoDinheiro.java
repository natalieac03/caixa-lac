package br.com.lac.caixa.pagamento;

import br.com.lac.caixa.modelo.PagamentoVenda;
import br.com.lac.caixa.util.Dinheiro;

/** Com o valor recebido informado, mostra o troco. */
public class PagamentoDinheiro implements Pagamento {
    @Override
    public String descrever(PagamentoVenda parte) {
        String texto = Dinheiro.reais(parte.getValor()) + " em dinheiro";
        return parte.getTroco() == null ? texto : texto + " (troco " + Dinheiro.reais(parte.getTroco()) + ")";
    }
}
