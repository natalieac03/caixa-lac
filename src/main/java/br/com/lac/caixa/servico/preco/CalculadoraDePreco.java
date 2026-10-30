package br.com.lac.caixa.servico.preco;

import br.com.lac.caixa.modelo.ItemVenda;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.modelo.PromocaoItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcula o preço de um carrinho com as promoções ativas. Classe pura: não
 * conhece Spring nem banco, e não altera os produtos recebidos.
 *
 * Regras a confirmar com a LAC (mantidas como estavam):
 * - os itens mais caros da categoria entram primeiro no pacote;
 * - A_PARTIR_DE e PACOTE podem se somar na mesma categoria (o pacote usa
 *   os preços já reduzidos pelo A_PARTIR_DE).
 */
public class CalculadoraDePreco {

    public Orcamento calcular(Carrinho carrinho, List<Promocao> ativas) {
        Map<Long, Integer> qtdPorCat = new HashMap<>();
        for (Carrinho.Linha linha : carrinho.itens()) {
            Produto p = linha.produto();
            if (p.getCategoria() != null) {
                qtdPorCat.merge(p.getCategoria().getId(), linha.quantidade(), Integer::sum);
            }
        }

        // -- Passo 1: cada regra registra o que vale para este carrinho --
        Map<Long, BigDecimal> precoAPartirDe = new HashMap<>();
        List<Promocao> pacotes = new ArrayList<>();
        ativas.stream()
                .sorted(Comparator.comparing(Promocao::getPreco)
                        .thenComparing(Promocao::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(p -> p.getTipo().regra().preparar(p, qtdPorCat, precoAPartirDe, pacotes));

        // -- Passo 2: preço unitário; A_PARTIR_DE só vale se baratear o item --
        List<ItemVenda> itens = new ArrayList<>();
        for (Carrinho.Linha linha : carrinho.itens()) {
            Produto p = linha.produto();
            BigDecimal precoUnit = p.getPreco();
            if (p.getCategoria() != null && precoAPartirDe.containsKey(p.getCategoria().getId())) {
                precoUnit = precoAPartirDe.get(p.getCategoria().getId()).min(p.getPreco());
            }
            itens.add(new ItemVenda(p, linha.quantidade(), precoUnit));
        }

        // -- Passo 3: desconto dos pacotes --
        // Preços das unidades por categoria, do mais caro para o mais barato.
        Map<Long, List<BigDecimal>> precosPorCat = new HashMap<>();
        for (ItemVenda iv : itens) {
            if (iv.getProduto().getCategoria() == null) continue;
            List<BigDecimal> lista = precosPorCat.computeIfAbsent(
                    iv.getProduto().getCategoria().getId(), k -> new ArrayList<>());
            for (int k = 0; k < iv.getQuantidade(); k++) {
                lista.add(iv.getPrecoUnitario());
            }
        }
        precosPorCat.values().forEach(l -> l.sort(Comparator.reverseOrder()));
        BigDecimal descontoPacote = melhorDesconto(qtdPorCat, qtdPorCat, precosPorCat, pacotes, new HashMap<>());

        List<Orcamento.Desconto> descontos = new ArrayList<>();
        for (ItemVenda iv : itens) {
            BigDecimal economia = iv.getProduto().getPreco().subtract(iv.getPrecoUnitario())
                    .multiply(BigDecimal.valueOf(iv.getQuantidade()));
            if (economia.signum() != 0) {
                descontos.add(new Orcamento.Desconto("A partir de: " + iv.getProduto().getNome(), economia));
            }
        }
        if (descontoPacote.signum() > 0) {
            descontos.add(new Orcamento.Desconto("Pacotes", descontoPacote));
        }

        BigDecimal total = itens.stream()
                .map(ItemVenda::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(descontoPacote);
        List<Orcamento.Item> itensOrcamento = itens.stream()
                .map(i -> new Orcamento.Item(i.getProduto().getId(), i.getProduto().getNome(),
                        i.getProduto().getNomeCategoria(), i.getQuantidade(), i.getPrecoUnitario()))
                .toList();
        return new Orcamento(itensOrcamento, descontos, total);
    }

    /**
     * Maior economia possível com os pacotes, por busca exata sobre as
     * quantidades restantes por categoria (com memoização), incluindo a opção
     * de não usar pacote nenhum. Cada pacote consome as unidades mais caras
     * que ainda restam na categoria (regra a confirmar com a LAC).
     */
    private BigDecimal melhorDesconto(Map<Long, Integer> total,
                                      Map<Long, Integer> restante,
                                      Map<Long, List<BigDecimal>> precos,
                                      List<Promocao> pacotes,
                                      Map<Map<Long, Integer>, BigDecimal> memo) {
        BigDecimal conhecido = memo.get(restante);
        if (conhecido != null) return conhecido;

        BigDecimal melhor = BigDecimal.ZERO;
        for (Promocao pacote : pacotes) {
            if (pacote.combosDisponiveis(restante) == 0) continue;
            Map<Long, Integer> proximo = new HashMap<>(restante);
            BigDecimal normal = BigDecimal.ZERO;
            for (PromocaoItem item : pacote.getItens()) {
                Long cat = item.getCategoria().getId();
                int inicio = total.get(cat) - restante.get(cat);
                for (int k = inicio; k < inicio + item.getQuantidade(); k++) {
                    normal = normal.add(precos.get(cat).get(k));
                }
                proximo.merge(cat, -item.getQuantidade(), Integer::sum);
            }
            BigDecimal economia = normal.subtract(pacote.getPreco());
            if (economia.signum() > 0) {
                melhor = melhor.max(economia.add(melhorDesconto(total, proximo, precos, pacotes, memo)));
            }
        }
        memo.put(Map.copyOf(restante), melhor);
        return melhor;
    }
}
