package br.com.lac.caixa.modelo.pagamento;

import br.com.lac.caixa.util.Dinheiro;

/** Strategy de pagamento: como cada forma descreve a sua parte da venda. */
public interface Pagamento {

    String descrever(DadosDaParte parte);

    default String nome(DadosDaParte parte) {
        return parte.forma().getRotulo();
    }

    default String resumir(DadosDaParte parte) {
        return nome(parte) + " " + Dinheiro.reais(parte.valor()) + troco(parte);
    }

    static String troco(DadosDaParte parte) {
        return parte.troco() == null ? "" : " (troco " + Dinheiro.reais(parte.troco()) + ")";
    }
}
