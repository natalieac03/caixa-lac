package br.com.lac.caixa.pagamento;

import java.math.BigDecimal;

public class PagamentoOutro implements Pagamento {
    private final String descricao;

    public PagamentoOutro(String descricao) {
        this.descricao = (descricao == null || descricao.isBlank()) ? "outro meio" : descricao;
    }

    @Override
    public String pagar(BigDecimal valor) {
        return "Recebido " + br.com.lac.caixa.util.Dinheiro.reais(valor) + " via " + descricao + ".";
    }
}
