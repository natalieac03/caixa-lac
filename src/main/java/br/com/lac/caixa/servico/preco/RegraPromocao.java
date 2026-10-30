package br.com.lac.caixa.servico.preco;

import br.com.lac.caixa.modelo.Promocao;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Como cada tipo de promoção entra no cálculo. As promoções chegam em ordem
 * de preço (e id no empate); cada regra registra o que vale para o carrinho.
 */
public interface RegraPromocao {

    /**
     * @param qtdPorCategoria  unidades do carrinho por categoria
     * @param precoPorCategoria preço unitário A_PARTIR_DE vencedor por categoria
     * @param pacotes          pacotes candidatos para a busca exata
     */
    void preparar(Promocao promocao,
                  Map<Long, Integer> qtdPorCategoria,
                  Map<Long, BigDecimal> precoPorCategoria,
                  List<Promocao> pacotes);
}
