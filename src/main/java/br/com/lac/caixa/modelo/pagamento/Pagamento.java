package br.com.lac.caixa.modelo.pagamento;

import br.com.lac.caixa.modelo.PagamentoVenda;
import br.com.lac.caixa.util.Dinheiro;

/** Strategy de pagamento: como cada forma descreve a sua parte da venda. */
public interface Pagamento {

    String descrever(PagamentoVenda parte);

    default String nome(PagamentoVenda parte) {
        return parte.getForma().getRotulo();
    }

    default String resumir(PagamentoVenda parte) {
        return nome(parte) + " " + Dinheiro.reais(parte.getValor()) + troco(parte);
    }

    static String troco(PagamentoVenda parte) {
        return parte.getTroco() == null ? "" : " (troco " + Dinheiro.reais(parte.getTroco()) + ")";
    }
}
