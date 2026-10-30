package br.com.lac.caixa.servico.preco;

import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.modelo.PromocaoItem;
import br.com.lac.caixa.modelo.TipoPromocao;
import br.com.lac.caixa.servico.RegraDeNegocioException;
import br.com.lac.caixa.util.Dinheiro;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcula o preço de um carrinho com as promoções ativas. Classe pura: não
 * conhece Spring nem banco, e não altera os produtos recebidos.
 *
 * <p><b>Regra:</b> o cliente paga o menor total possível, e cada unidade entra
 * em no máximo uma promoção.
 * <ul>
 *   <li>A_PARTIR_DE: quando o carrinho atinge os mínimos de todas as
 *       categorias da promoção, todas as unidades dessas categorias podem sair
 *       pelo preço promocional, só nos produtos em que ele é mais barato que o
 *       normal. Essas unidades ficam "usadas" e não entram em pacote.</li>
 *   <li>PACOTE: as unidades que sobraram (preço normal) podem formar pacotes;
 *       cada pacote leva as unidades mais caras que ainda restam em cada
 *       categoria, o que maximiza a economia.</li>
 * </ul>
 *
 * <p><b>Busca exata:</b> para cada subconjunto das A_PARTIR_DE ativadas pelo
 * carrinho, aplica as do subconjunto e resolve os pacotes sobre as unidades
 * restantes por programação dinâmica (memo indexado pelas quantidades
 * restantes por categoria). Fica o menor total. Empate: vale o primeiro
 * subconjunto na ordem dos ids (o vazio primeiro, depois o de menor id) e,
 * nos pacotes, o primeiro pacote por id. Valores em centavos (long).
 *
 * <p><b>Desempenho:</b> custo ~ 2^k × (estados × pacotes), em que k é o número
 * de A_PARTIR_DE ativadas que baixam algum preço e "estados" é o produto de
 * (unidades restantes + 1) nas categorias com pacote. Um carrinho de 50
 * unidades em 5 categorias fica bem abaixo de 100 ms (há teste). Acima de
 * {@value #LIMITE_A_PARTIR_DE} A_PARTIR_DE ativadas ou {@value #LIMITE_ESTADOS}
 * estados, a venda é recusada com mensagem, em vez de travar o caixa.
 */
public class CalculadoraDePreco {

    static final int LIMITE_A_PARTIR_DE = 12;
    static final int LIMITE_ESTADOS = 2_000_000;

    private static final Comparator<Promocao> POR_ID =
            Comparator.comparing(Promocao::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    /** Uma linha do carrinho, já em centavos. */
    private record Linha(Produto produto, Long categoria, int quantidade, long normal) {}

    /** O que um subconjunto de A_PARTIR_DE produz. */
    private record Cenario(long total, long[] precoUnitario, Map<Promocao, Integer> pacotesUsados,
                           Map<Promocao, Long> economiaPorPacote) {}

    public Orcamento calcular(Carrinho carrinho, List<Promocao> ativas) {
        List<Linha> linhas = new ArrayList<>();
        Map<Long, Integer> qtdPorCat = new HashMap<>();
        for (Carrinho.Linha l : carrinho.itens()) {
            Produto p = l.produto();
            Long cat = p.getCategoria() == null ? null : p.getCategoria().getId();
            linhas.add(new Linha(p, cat, l.quantidade(), centavos(p.getPreco())));
            if (cat != null) qtdPorCat.merge(cat, l.quantidade(), Integer::sum);
        }

        // A_PARTIR_DE que o carrinho ativa e que baixam o preço de algum produto.
        List<Promocao> aPartirDe = ativas.stream()
                .filter(p -> p.getTipo() == TipoPromocao.A_PARTIR_DE)
                .filter(p -> p.combosDisponiveis(qtdPorCat) > 0)
                .filter(p -> baixaAlgumPreco(p, linhas))
                .sorted(POR_ID)
                .toList();
        List<Promocao> pacotes = ativas.stream()
                .filter(p -> p.getTipo() == TipoPromocao.PACOTE)
                .sorted(POR_ID)
                .toList();
        if (aPartirDe.size() > LIMITE_A_PARTIR_DE) {
            throw new RegraDeNegocioException("Promoções demais ao mesmo tempo para este carrinho. Divida a venda.");
        }

        Cenario melhor = null;
        for (int mascara = 0; mascara < (1 << aPartirDe.size()); mascara++) {
            Cenario c = cenario(linhas, aPartirDe, mascara, pacotes);
            if (melhor == null || c.total() < melhor.total()) melhor = c;
        }
        return orcamento(linhas, melhor);
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

    private Cenario cenario(List<Linha> linhas, List<Promocao> aPartirDe, int mascara, List<Promocao> pacotes) {
        // Menor preço A_PARTIR_DE do subconjunto em cada categoria.
        Map<Long, Long> precoPromo = new HashMap<>();
        for (int b = 0; b < aPartirDe.size(); b++) {
            if ((mascara & (1 << b)) == 0) continue;
            Promocao p = aPartirDe.get(b);
            for (PromocaoItem item : p.getItens()) {
                precoPromo.merge(item.getCategoria().getId(), centavos(p.getPreco()), Math::min);
            }
        }

        long total = 0;
        long[] precoUnitario = new long[linhas.size()];
        Map<Long, List<Long>> restantes = new LinkedHashMap<>();
        for (int i = 0; i < linhas.size(); i++) {
            Linha l = linhas.get(i);
            Long promo = l.categoria() == null ? null : precoPromo.get(l.categoria());
            boolean usada = promo != null && promo < l.normal();
            precoUnitario[i] = usada ? promo : l.normal();
            total += precoUnitario[i] * l.quantidade();
            if (!usada && l.categoria() != null) {
                List<Long> lista = restantes.computeIfAbsent(l.categoria(), k -> new ArrayList<>());
                for (int k = 0; k < l.quantidade(); k++) lista.add(l.normal());
            }
        }

        var pacote = new BuscaDePacotes(restantes, pacotes);
        long economia = pacote.melhorEconomia();
        return new Cenario(total - economia, precoUnitario, pacote.usados(), pacote.economiaPorPacote());
    }

    private Orcamento orcamento(List<Linha> linhas, Cenario c) {
        List<Orcamento.Item> itens = new ArrayList<>();
        List<Orcamento.Desconto> descontos = new ArrayList<>();
        for (int i = 0; i < linhas.size(); i++) {
            Linha l = linhas.get(i);
            itens.add(new Orcamento.Item(l.produto().getId(), l.produto().getNome(), l.produto().getNomeCategoria(),
                    l.quantidade(), reais(c.precoUnitario()[i])));
            long economia = (l.normal() - c.precoUnitario()[i]) * l.quantidade();
            if (economia != 0) descontos.add(new Orcamento.Desconto("A partir de: " + l.produto().getNome(), reais(economia)));
        }
        c.pacotesUsados().forEach((p, vezes) -> descontos.add(new Orcamento.Desconto(
                "Pacote " + p.getNomesCategorias() + " por " + Dinheiro.reais(p.getPreco()) + (vezes > 1 ? " (" + vezes + "x)" : ""),
                reais(c.economiaPorPacote().get(p)))));
        return new Orcamento(itens, descontos, reais(c.total()));
    }

    /**
     * Maior economia com pacotes sobre as unidades restantes. Estado = quantas
     * unidades restam em cada categoria que algum pacote usa; as consumidas
     * são sempre as mais caras.
     */
    private static final class BuscaDePacotes {
        private final List<Promocao> pacotes;
        private final List<Long> categorias = new ArrayList<>();
        private final long[][] acumulado;   // acumulado[c][k] = soma das k unidades mais caras
        private final int[] total;
        private final int[] base;
        private final int[][] usoPorPacote; // unidades de cada categoria por pacote (-1 = categoria sem unidades)
        private final long[] precoPacote;
        private final long[] memo;
        private final int[] escolha;

        BuscaDePacotes(Map<Long, List<Long>> restantes, List<Promocao> todos) {
            // Só pacotes cujas categorias têm unidades restantes.
            this.pacotes = todos.stream().filter(p -> p.getItens().stream()
                    .allMatch(i -> restantes.getOrDefault(i.getCategoria().getId(), List.of()).size() >= i.getQuantidade()))
                    .toList();
            for (Promocao p : pacotes) {
                for (PromocaoItem i : p.getItens()) {
                    if (!categorias.contains(i.getCategoria().getId())) categorias.add(i.getCategoria().getId());
                }
            }
            int n = categorias.size();
            acumulado = new long[n][];
            total = new int[n];
            base = new int[n];
            long estados = 1;
            for (int c = 0; c < n; c++) {
                List<Long> precos = new ArrayList<>(restantes.get(categorias.get(c)));
                precos.sort(Comparator.reverseOrder());
                total[c] = precos.size();
                acumulado[c] = new long[precos.size() + 1];
                for (int k = 0; k < precos.size(); k++) acumulado[c][k + 1] = acumulado[c][k] + precos.get(k);
                base[c] = (int) estados;
                estados *= total[c] + 1;
                if (estados > LIMITE_ESTADOS) {
                    throw new RegraDeNegocioException("Carrinho grande demais para calcular os pacotes. Divida a venda.");
                }
            }
            usoPorPacote = new int[pacotes.size()][n];
            precoPacote = new long[pacotes.size()];
            for (int p = 0; p < pacotes.size(); p++) {
                for (PromocaoItem i : pacotes.get(p).getItens()) {
                    usoPorPacote[p][categorias.indexOf(i.getCategoria().getId())] += i.getQuantidade();
                }
                precoPacote[p] = centavos(pacotes.get(p).getPreco());
            }
            memo = new long[(int) estados];
            escolha = new int[(int) estados];
            Arrays.fill(memo, -1);
        }

        private int inicial() {
            int e = 0;
            for (int c = 0; c < total.length; c++) e += total[c] * base[c];
            return e;
        }

        long melhorEconomia() {
            return pacotes.isEmpty() ? 0 : economia(inicial());
        }

        private long economia(int estado) {
            if (memo[estado] >= 0) return memo[estado];
            long melhor = 0;
            int melhorPacote = -1;
            for (int p = 0; p < pacotes.size(); p++) {
                long normal = 0;
                int proximo = estado;
                boolean cabe = true;
                for (int c = 0; c < total.length && cabe; c++) {
                    int q = usoPorPacote[p][c];
                    if (q == 0) continue;
                    int resta = (estado / base[c]) % (total[c] + 1);
                    if (resta < q) { cabe = false; break; }
                    int inicio = total[c] - resta;
                    normal += acumulado[c][inicio + q] - acumulado[c][inicio];
                    proximo -= q * base[c];
                }
                if (!cabe) continue;
                long ganho = normal - precoPacote[p];
                if (ganho <= 0) continue;
                long candidato = ganho + economia(proximo);
                if (candidato > melhor) { melhor = candidato; melhorPacote = p; }
            }
            memo[estado] = melhor;
            escolha[estado] = melhorPacote;
            return melhor;
        }

        /** Pacotes usados na melhor solução, em ordem de id, com quantas vezes. */
        Map<Promocao, Integer> usados() {
            Map<Promocao, Integer> usados = new LinkedHashMap<>();
            percorrer((p, ganho) -> usados.merge(p, 1, Integer::sum));
            Map<Promocao, Integer> ordenado = new LinkedHashMap<>();
            pacotes.stream().filter(usados::containsKey).forEach(p -> ordenado.put(p, usados.get(p)));
            return ordenado;
        }

        Map<Promocao, Long> economiaPorPacote() {
            Map<Promocao, Long> economia = new HashMap<>();
            percorrer((p, ganho) -> economia.merge(p, ganho, Long::sum));
            return economia;
        }

        private void percorrer(java.util.function.BiConsumer<Promocao, Long> visita) {
            if (pacotes.isEmpty()) return;
            int estado = inicial();
            economia(estado);
            while (escolha[estado] >= 0) {
                int p = escolha[estado];
                int proximo = estado;
                for (int c = 0; c < total.length; c++) proximo -= usoPorPacote[p][c] * base[c];
                visita.accept(pacotes.get(p), memo[estado] - memo[proximo]);
                estado = proximo;
            }
        }
    }

    private static long centavos(BigDecimal valor) {
        return valor.setScale(2).unscaledValue().longValueExact();
    }

    private static BigDecimal reais(long centavos) {
        return BigDecimal.valueOf(centavos, 2);
    }
}
