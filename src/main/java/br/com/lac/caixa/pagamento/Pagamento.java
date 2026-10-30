package br.com.lac.caixa.pagamento;

import java.math.BigDecimal;

/**
 * Padrao Strategy: cada forma de pagamento implementa a sua propria
 * mensagem de confirmacao. Mesma ideia do projeto de pedidos da turma.
 */
public interface Pagamento {
    String pagar(BigDecimal valor);
}
