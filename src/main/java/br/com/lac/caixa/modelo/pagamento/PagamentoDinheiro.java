package br.com.lac.caixa.modelo.pagamento;

import br.com.lac.caixa.util.Dinheiro;

/** Descrição da parte paga em dinheiro, com o troco. */
public class PagamentoDinheiro implements Pagamento {
    @Override
    public String descrever(DadosDaParte parte) {
        return Dinheiro.reais(parte.valor()) + " em dinheiro" + Pagamento.troco(parte);
    }
}
