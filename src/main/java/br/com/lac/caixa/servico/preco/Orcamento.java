package br.com.lac.caixa.servico.preco;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado da calculadora. aproximado = true quando o carrinho era grande
 * demais para a busca exata e os pacotes foram escolhidos por heurística
 * (o total pode ficar um pouco acima do menor possível; nunca abaixo).
 */
public record Orcamento(List<Item> itens, List<Desconto> descontos, BigDecimal total, boolean aproximado) {
    public Orcamento {
        itens = List.copyOf(itens);
        descontos = List.copyOf(descontos);
    }

    public record Item(Long produtoId, String nome, String categoria, int quantidade, BigDecimal precoUnitario) {}

    /** promocaoId: a promoção que deu o desconto (a A_PARTIR_DE vencedora da categoria, ou o pacote). */
    public record Desconto(String descricao, BigDecimal valor, Long promocaoId) {}
}
