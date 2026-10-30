package br.com.lac.caixa.pagamento;

import br.com.lac.caixa.modelo.PagamentoVenda;
import br.com.lac.caixa.util.Dinheiro;

public class PagamentoOutro implements Pagamento {
    @Override
    public String descrever(PagamentoVenda parte) {
        String descricao = parte.getDescricao() == null || parte.getDescricao().isBlank()
                ? "outro meio" : parte.getDescricao();
        return Dinheiro.reais(parte.getValor()) + " via " + descricao;
    }
}
