package br.com.lac.caixa.modelo.pagamento;

import br.com.lac.caixa.modelo.PagamentoVenda;
import br.com.lac.caixa.util.Dinheiro;

/** Descrição da parte paga em Outro, pela descrição digitada. */
public class PagamentoOutro implements Pagamento {
    @Override
    public String descrever(PagamentoVenda parte) {
        String descricao =
                parte.getDescricao() == null || parte.getDescricao().isBlank() ? "outro meio" : parte.getDescricao();
        return Dinheiro.reais(parte.getValor()) + " via " + descricao;
    }

    @Override
    public String nome(PagamentoVenda parte) {
        return parte.getDescricao() == null || parte.getDescricao().isBlank()
                ? parte.getForma().getRotulo()
                : parte.getDescricao();
    }
}
