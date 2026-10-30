package br.com.lac.caixa.servico;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.OrigemPagamento;
import java.math.BigDecimal;

/**
 * Uma parte do pagamento como veio da tela. valor null só é aceito quando há
 * uma parte só: ela recebe o total cobrado (o caso comum, um toque).
 * origem e parte só importam no Pix: com MERCADO_PAGO, a cobrança da parte
 * (token + número estável da parte) precisa estar paga.
 */
public record PagamentoInformado(
        FormaPagamento forma,
        BigDecimal valor,
        String descricao,
        BigDecimal recebido,
        OrigemPagamento origem,
        int parte) {

    public PagamentoInformado(FormaPagamento forma, BigDecimal valor, String descricao, BigDecimal recebido) {
        this(forma, valor, descricao, recebido, null, 0);
    }

    public static PagamentoInformado total(FormaPagamento forma) {
        return new PagamentoInformado(forma, null, null, null);
    }
}
