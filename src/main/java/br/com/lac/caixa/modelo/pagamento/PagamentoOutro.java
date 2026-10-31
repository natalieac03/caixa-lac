package br.com.lac.caixa.modelo.pagamento;

import br.com.lac.caixa.util.Dinheiro;

/** Descrição da parte paga em Outro, pela descrição digitada. */
public class PagamentoOutro implements Pagamento {
    @Override
    public String descrever(DadosDaParte parte) {
        String descricao = parte.descricao() == null || parte.descricao().isBlank() ? "outro meio" : parte.descricao();
        return Dinheiro.reais(parte.valor()) + " via " + descricao;
    }

    @Override
    public String nome(DadosDaParte parte) {
        return parte.descricao() == null || parte.descricao().isBlank()
                ? parte.forma().getRotulo()
                : parte.descricao();
    }
}
