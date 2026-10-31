package br.com.lac.caixa.modelo.pagamento;

import br.com.lac.caixa.modelo.PagamentoVenda;
import br.com.lac.caixa.util.Dinheiro;

/**
 * Padrao Strategy: cada forma de pagamento descreve a sua parte da venda.
 * Mesma ideia do projeto de pedidos da turma.
 * - descrever: confirmacao ("R$ 10,00 via Pix");
 * - nome e resumir: historico ("Pix R$ 10,00", "Dinheiro R$ 8,00 (troco R$ 2,00)").
 * O troco e calculado em PagamentoVenda.getTroco e escrito so em {@link #troco}.
 */
public interface Pagamento {

    String descrever(PagamentoVenda parte);

    /** Nome da forma no historico: o rotulo, ou a descricao no caso do Outro. */
    default String nome(PagamentoVenda parte) {
        return parte.getForma().getRotulo();
    }

    default String resumir(PagamentoVenda parte) {
        return nome(parte) + " " + Dinheiro.reais(parte.getValor()) + troco(parte);
    }

    /** " (troco R$ 2,00)" quando o recebido em dinheiro foi informado; senao vazio. */
    static String troco(PagamentoVenda parte) {
        return parte.getTroco() == null ? "" : " (troco " + Dinheiro.reais(parte.getTroco()) + ")";
    }
}
