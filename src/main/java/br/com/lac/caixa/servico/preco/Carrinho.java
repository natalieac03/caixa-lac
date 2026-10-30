package br.com.lac.caixa.servico.preco;
import br.com.lac.caixa.modelo.Produto;
import java.util.List;
public record Carrinho(List<Linha> itens) {
    public Carrinho { itens = List.copyOf(itens); }
    public record Linha(Produto produto, int quantidade) {}
}
