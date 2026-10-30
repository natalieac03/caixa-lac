package br.com.lac.caixa.servico.preco;
import br.com.lac.caixa.modelo.*;
import java.math.BigDecimal;
import java.util.*;
public class CalculadoraDePreco {
    public Orcamento calcular(Carrinho carrinho, List<Promocao> ativas) {
        Map<Long, Produto> selecionados = new LinkedHashMap<>();
        Map<Long, Integer> quantidades = new LinkedHashMap<>();
        Map<Long, Integer> qtdPorCat = new HashMap<>();
        List<ItemVenda> itens = new ArrayList<>();
        for (var linha : carrinho.itens()) {
            Produto p = linha.produto();
            selecionados.put(p.getId(), p);
            quantidades.put(p.getId(), linha.quantidade());
            if (p.getCategoria() != null) qtdPorCat.merge(p.getCategoria().getId(), linha.quantidade(), Integer::sum);
        }
        Map<Long, BigDecimal> precoAPartirDe = new HashMap<>();
        List<Promocao> pacotes = new ArrayList<>();
        ativas.stream().sorted(Comparator.comparing(Promocao::getPreco).thenComparing(Promocao::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(p -> p.getTipo().regra().preparar(p, qtdPorCat, precoAPartirDe, pacotes));

        // Monta itens sem alterar os produtos recebidos.
        for (Map.Entry<Long, Produto> e : selecionados.entrySet()) {
            Produto p = e.getValue();
            int qtd = quantidades.get(e.getKey());


            BigDecimal precoUnit = (p.getCategoria() != null
                    && precoAPartirDe.containsKey(p.getCategoria().getId()))
                    ? precoAPartirDe.get(p.getCategoria().getId()).min(p.getPreco())
                    : p.getPreco();

            itens.add(new ItemVenda(p, qtd, precoUnit));
        }

        // -- Passo 4: calcula desconto dos combos PACOTE --
        // Preços dos itens por categoria (ordenados decrescente para
        // maximizar o desconto: os mais caros entram primeiro no combo).
        Map<Long, List<BigDecimal>> precosPorCat = new HashMap<>();
        for (ItemVenda iv : itens) {
            if (iv.getProduto().getCategoria() == null) continue;
            Long catId = iv.getProduto().getCategoria().getId();
            List<BigDecimal> lista = precosPorCat.computeIfAbsent(catId, k -> new ArrayList<>());
            for (int k = 0; k < iv.getQuantidade(); k++) {
                lista.add(iv.getPrecoUnitario());
            }
        }
        precosPorCat.values().forEach(l -> l.sort(Comparator.reverseOrder()));

        BigDecimal descontoPacote = melhorDesconto(qtdPorCat, qtdPorCat, precosPorCat, pacotes, new HashMap<>());

        List<Orcamento.Desconto> descontos = new ArrayList<>();
        for (ItemVenda iv : itens) {
            BigDecimal economia = iv.getProduto().getPreco().subtract(iv.getPrecoUnitario()).multiply(BigDecimal.valueOf(iv.getQuantidade()));
            if (economia.signum() != 0) descontos.add(new Orcamento.Desconto("A partir de: " + iv.getProduto().getNome(), economia));
        }
        if (descontoPacote.signum() > 0) descontos.add(new Orcamento.Desconto("Pacotes", descontoPacote));
        BigDecimal total = itens.stream().map(ItemVenda::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add).subtract(descontoPacote);
        return new Orcamento(itens.stream().map(i -> new Orcamento.Item(i.getProduto().getId(), i.getProduto().getNome(), i.getProduto().getNomeCategoria(), i.getQuantidade(), i.getPrecoUnitario())).toList(), descontos, total);
    }
    /**
     * Os itens mais caros da categoria entram primeiro no pacote: a confirmar com a LAC.
     * A_PARTIR_DE e PACOTE podem se somar: a confirmar com a LAC.
     * Busca exata com memoização das quantidades restantes, incluindo não usar pacote.
     */
    private BigDecimal melhorDesconto(Map<Long,Integer> total, Map<Long,Integer> restante,
            Map<Long,List<BigDecimal>> precos, List<Promocao> pacotes, Map<Map<Long,Integer>,BigDecimal> memo) {
        if (memo.containsKey(restante)) return memo.get(restante);
        BigDecimal melhor = BigDecimal.ZERO;
        for (Promocao p : pacotes) {
            if (p.combosDisponiveis(restante) == 0) continue;
            Map<Long,Integer> proximo = new HashMap<>(restante);
            BigDecimal normal = BigDecimal.ZERO;
            for (var item : p.getItens()) {
                Long cat = item.getCategoria().getId();
                int inicio = total.get(cat) - restante.get(cat);
                for (int k = inicio; k < inicio + item.getQuantidade(); k++) normal = normal.add(precos.get(cat).get(k));
                proximo.merge(cat, -item.getQuantidade(), Integer::sum);
            }
            BigDecimal economia = normal.subtract(p.getPreco());
            if (economia.signum() > 0) melhor = melhor.max(economia.add(melhorDesconto(total, proximo, precos, pacotes, memo)));
        }
        memo.put(Map.copyOf(restante), melhor);
        return melhor;
    }
}
