package br.com.lac.caixa.modelo.pagamento;

import br.com.lac.caixa.modelo.FormaPagamento;
import java.math.BigDecimal;

/** O que a estratégia precisa de uma parte do pagamento: forma, valor, descrição do Outro e troco. */
public record DadosDaParte(FormaPagamento forma, BigDecimal valor, String descricao, BigDecimal troco) {}
