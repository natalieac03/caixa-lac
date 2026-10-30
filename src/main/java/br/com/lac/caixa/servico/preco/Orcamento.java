package br.com.lac.caixa.servico.preco;
import java.math.BigDecimal;
import java.util.List;
public record Orcamento(List<Item> itens, List<Desconto> descontos, BigDecimal total) {
    public Orcamento { itens = List.copyOf(itens); descontos = List.copyOf(descontos); }
    public record Item(Long produtoId, String nome, String categoria, int quantidade, BigDecimal precoUnitario) {}
    public record Desconto(String descricao, BigDecimal valor) {}
}
