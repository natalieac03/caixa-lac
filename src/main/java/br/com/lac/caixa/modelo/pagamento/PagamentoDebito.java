package br.com.lac.caixa.modelo.pagamento;

import br.com.lac.caixa.util.Dinheiro;

/** Descrição da parte paga no débito. */
public class PagamentoDebito implements Pagamento {
    @Override
    public String descrever(DadosDaParte parte) {
        return Dinheiro.reais(parte.valor()) + " no débito";
    }
}
