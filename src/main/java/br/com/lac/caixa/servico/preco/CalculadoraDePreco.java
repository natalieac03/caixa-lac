package br.com.lac.caixa.servico.preco;

import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.modelo.PromocaoItem;
import br.com.lac.caixa.modelo.TipoPromocao;
import br.com.lac.caixa.util.Dinheiro;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Calcula o menor preço de um carrinho com as promoções ativas. */
public class CalculadoraDePreco {

    static final long ORCAMENTO_DE_ESTADOS = 3_000_000;
    static final int MAXIMO_A_PARTIR_DE_ENUMERADAS = 12;

    private static final Logger LOG = LoggerFactory.getLogger(CalculadoraDePreco.class);
    private static final Comparator<Promocao> POR_ID =
            Comparator.comparing(Promocao::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private record Linha(Produto produto, Long categoria, int quantidade, long normal) {}

    private record Preparo(
            long total, long[] precoUnitario, Promocao[] promocaoUsada, TreeMap<Long, List<Long>> restantes) {}

    private record Cenario(long total, Preparo preparo, BuscaDePacotes.Resultado pacotes) {}

    public Orcamento calcular(Carrinho carrinho, List<Promocao> ativas) {
        List<Linha> linhas = new ArrayList<>();
        Map<Long, Integer> qtdPorCat = new HashMap<>();
        for (Carrinho.Linha l : carrinho.itens()) {
            Produto p = l.produto();
            Long cat = p.getCategoria() == null ? null : p.getCategoria().getId();
            linhas.add(new Linha(p, cat, l.quantidade(), centavos(p.getPreco())));
            if (cat != null) qtdPorCat.merge(cat, l.quantidade(), Integer::sum);
        }

        List<Promocao> pacotes = ativas.stream()
                .filter(p -> p.getTipo() == TipoPromocao.PACOTE)
                .sorted(POR_ID)
                .toList();
        Set<Long> categoriasDePacote = new HashSet<>();
        pacotes.forEach(p -> p.getItens()
                .forEach(i -> categoriasDePacote.add(i.getCategoria().getId())));

        List<Promocao> aPartirDe = ativas.stream()
                .filter(p -> p.getTipo() == TipoPromocao.A_PARTIR_DE)
                .filter(p -> p.combosDisponiveis(qtdPorCat) > 0)
                .filter(p -> baixaAlgumPreco(p, linhas))
                .sorted(POR_ID)
                .toList();
        List<Promocao> diretas = aPartirDe.stream()
                .filter(p -> p.getItens().stream()
                        .noneMatch(i ->
                                categoriasDePacote.contains(i.getCategoria().getId())))
                .toList();
        List<Promocao> enumeradas =
                aPartirDe.stream().filter(p -> !diretas.contains(p)).toList();

        boolean aproximado = enumeradas.size() > MAXIMO_A_PARTIR_DE_ENUMERADAS;
        List<BitSet> subconjuntos =
                aproximado ? algunsSubconjuntos(enumeradas.size()) : todosSubconjuntos(enumeradas.size());

        Map<TreeMap<Long, List<Long>>, BuscaDePacotes> buscas = new HashMap<>();
        List<Preparo> preparos = new ArrayList<>();
        for (BitSet s : subconjuntos) {
            Preparo preparo = preparar(linhas, diretas, enumeradas, s, categoriasDePacote);
            preparos.add(preparo);
            buscas.computeIfAbsent(preparo.restantes(), r -> new BuscaDePacotes(r, pacotes));
        }
        long trabalho = 0;
        for (BuscaDePacotes b : buscas.values()) trabalho = Math.min(Long.MAX_VALUE / 2, trabalho + b.estados());
        if (trabalho > ORCAMENTO_DE_ESTADOS) aproximado = true;
        if (aproximado) {
            LOG.warn(
                    "Carrinho grande para a busca exata ({} unidades, {} A_PARTIR_DE enumeráveis, {} estados): "
                            + "pacotes escolhidos por heurística; orçamento marcado como aproximado.",
                    qtdPorCat.values().stream().mapToInt(Integer::intValue).sum(),
                    enumeradas.size(),
                    trabalho);
        }

        Map<TreeMap<Long, List<Long>>, BuscaDePacotes.Resultado> resolvidas = new HashMap<>();
        Cenario melhor = null;
        for (Preparo preparo : preparos) {
            final boolean heuristica = aproximado;
            var pac = resolvidas.computeIfAbsent(
                    preparo.restantes(),
                    r -> heuristica ? buscas.get(r).gulosa() : buscas.get(r).exata());
            long total = preparo.total() - pac.economia();
            if (melhor == null || total < melhor.total()) melhor = new Cenario(total, preparo, pac);
        }
        return orcamento(linhas, melhor, aproximado);
    }

    private static boolean baixaAlgumPreco(Promocao promo, List<Linha> linhas) {
        long preco = centavos(promo.getPreco());
        for (PromocaoItem item : promo.getItens()) {
            for (Linha l : linhas) {
                if (item.getCategoria().getId().equals(l.categoria()) && preco < l.normal()) return true;
            }
        }
        return false;
    }

    private static List<BitSet> todosSubconjuntos(int n) {
        List<BitSet> todos = new ArrayList<>();
        for (int mascara = 0; mascara < (1 << n); mascara++) todos.add(BitSet.valueOf(new long[] {mascara}));
        return todos;
    }

    private static List<BitSet> algunsSubconjuntos(int n) {
        List<BitSet> alguns = new ArrayList<>();
        alguns.add(new BitSet());
        BitSet todas = new BitSet();
        todas.set(0, n);
        alguns.add(todas);
        for (int i = 0; i < n; i++) {
            BitSet uma = new BitSet();
            uma.set(i);
            alguns.add(uma);
        }
        return alguns;
    }

    private Preparo preparar(
            List<Linha> linhas,
            List<Promocao> diretas,
            List<Promocao> enumeradas,
            BitSet subconjunto,
            Set<Long> categoriasDePacote) {
        Map<Long, Long> precoPromo = new HashMap<>();
        Map<Long, Promocao> vencedora = new HashMap<>();
        for (Promocao p : diretas) aplicar(p, precoPromo, vencedora);
        for (int b = subconjunto.nextSetBit(0); b >= 0; b = subconjunto.nextSetBit(b + 1))
            aplicar(enumeradas.get(b), precoPromo, vencedora);

        long total = 0;
        long[] precoUnitario = new long[linhas.size()];
        Promocao[] promocaoUsada = new Promocao[linhas.size()];
        TreeMap<Long, List<Long>> restantes = new TreeMap<>();
        for (int i = 0; i < linhas.size(); i++) {
            Linha l = linhas.get(i);
            Long promo = l.categoria() == null ? null : precoPromo.get(l.categoria());
            boolean usada = promo != null && promo < l.normal();
            precoUnitario[i] = usada ? promo : l.normal();
            if (usada) promocaoUsada[i] = vencedora.get(l.categoria());
            total += precoUnitario[i] * l.quantidade();
            if (!usada && l.categoria() != null && categoriasDePacote.contains(l.categoria())) {
                List<Long> lista = restantes.computeIfAbsent(l.categoria(), k -> new ArrayList<>());
                for (int k = 0; k < l.quantidade(); k++) lista.add(l.normal());
            }
        }
        restantes.values().forEach(lista -> lista.sort(Comparator.reverseOrder()));
        return new Preparo(total, precoUnitario, promocaoUsada, restantes);
    }

    private static void aplicar(Promocao p, Map<Long, Long> precoPromo, Map<Long, Promocao> vencedora) {
        long preco = centavos(p.getPreco());
        for (PromocaoItem item : p.getItens()) {
            Long categoria = item.getCategoria().getId();
            Long atual = precoPromo.get(categoria);
            if (atual == null || preco < atual) {
                precoPromo.put(categoria, preco);
                vencedora.put(categoria, p);
            }
        }
    }

    private Orcamento orcamento(List<Linha> linhas, Cenario c, boolean aproximado) {
        List<Orcamento.Item> itens = new ArrayList<>();
        List<Orcamento.Desconto> descontos = new ArrayList<>();
        for (int i = 0; i < linhas.size(); i++) {
            Linha l = linhas.get(i);
            itens.add(new Orcamento.Item(
                    l.produto().getId(),
                    l.produto().getNome(),
                    l.produto().getNomeCategoria(),
                    l.quantidade(),
                    reais(c.preparo().precoUnitario()[i])));
            long economia = (l.normal() - c.preparo().precoUnitario()[i]) * l.quantidade();
            if (economia != 0)
                descontos.add(new Orcamento.Desconto(
                        "A partir de: " + l.produto().getNome(),
                        reais(economia),
                        c.preparo().promocaoUsada()[i].getId()));
        }
        c.pacotes()
                .usados()
                .forEach((p, vezes) -> descontos.add(new Orcamento.Desconto(
                        "Pacote " + p.getNomesCategorias() + " por " + Dinheiro.reais(p.getPreco())
                                + (vezes > 1 ? " (" + vezes + "x)" : ""),
                        reais(c.pacotes().economiaPorPacote().get(p)),
                        p.getId())));
        return new Orcamento(itens, descontos, reais(c.total()), aproximado);
    }

    static long centavos(BigDecimal valor) {
        return valor.setScale(2).unscaledValue().longValueExact();
    }

    private static BigDecimal reais(long centavos) {
        return BigDecimal.valueOf(centavos, 2);
    }
}
