package br.com.lac.caixa.pagamento;

import br.com.lac.caixa.modelo.PagamentoVenda;

/**
 * Padrao Strategy: cada forma de pagamento descreve a sua parte da
 * confirmacao ("R$ 10,00 via Pix", "R$ 8,00 em dinheiro (troco R$ 2,00)").
 * Mesma ideia do projeto de pedidos da turma.
 */
public interface Pagamento {
    String descrever(PagamentoVenda parte);
}
