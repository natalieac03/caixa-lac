package br.com.lac.caixa.servico;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.OrigemPagamento;
import java.math.BigDecimal;

/** Uma parte do pagamento como veio da tela. */
public record PagamentoInformado(
        FormaPagamento forma,
        BigDecimal valor,
        String descricao,
        BigDecimal recebido,
        OrigemPagamento origem,
        int parte) {

    public static PagamentoInformado total(FormaPagamento forma) {
        return new PagamentoInformado(forma, null, null, null, null, 0);
    }
}
