package br.com.lac.caixa.servico.preco;

import br.com.lac.caixa.modelo.Promocao;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Pacotes não mudam preço unitário: viram candidatos da busca exata da calculadora. */
public class Pacote implements RegraPromocao {

    @Override
    public void preparar(Promocao promocao,
                         Map<Long, Integer> qtdPorCategoria,
                         Map<Long, BigDecimal> precoPorCategoria,
                         List<Promocao> pacotes) {
        pacotes.add(promocao);
    }
}
