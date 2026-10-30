package br.com.lac.caixa.servico;

import br.com.lac.caixa.modelo.FormaPagamento;

import java.math.BigDecimal;

/**
 * Uma parte do pagamento como veio da tela. valor null só é aceito quando há
 * uma parte só: ela recebe o total cobrado (o caso comum, um toque).
 */
public record PagamentoInformado(FormaPagamento forma, BigDecimal valor, String descricao, BigDecimal recebido) {

    public static PagamentoInformado total(FormaPagamento forma) {
        return new PagamentoInformado(forma, null, null, null);
    }
}
