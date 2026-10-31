package br.com.lac.caixa.modelo.pagamento;

import br.com.lac.caixa.util.Dinheiro;

/** Descrição da parte paga em Pix. */
public class PagamentoPix implements Pagamento {
    @Override
    public String descrever(DadosDaParte parte) {
        return Dinheiro.reais(parte.valor()) + " via Pix";
    }
}
