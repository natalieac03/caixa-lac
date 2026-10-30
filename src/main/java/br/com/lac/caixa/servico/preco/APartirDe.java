package br.com.lac.caixa.servico.preco;

import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.modelo.PromocaoItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Atingidos os mínimos de todas as categorias do combo, todas as unidades
 * dessas categorias passam a custar o preço promocional. Com mais de uma
 * promoção na mesma categoria, vence a de menor preço.
 */
public class APartirDe implements RegraPromocao {

    @Override
    public void preparar(Promocao promocao,
                         Map<Long, Integer> qtdPorCategoria,
                         Map<Long, BigDecimal> precoPorCategoria,
                         List<Promocao> pacotes) {
        if (promocao.combosDisponiveis(qtdPorCategoria) == 0) return;
        for (PromocaoItem item : promocao.getItens()) {
            precoPorCategoria.merge(item.getCategoria().getId(), promocao.getPreco(), BigDecimal::min);
        }
    }
}
