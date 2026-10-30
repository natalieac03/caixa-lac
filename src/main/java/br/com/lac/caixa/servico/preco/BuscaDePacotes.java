package br.com.lac.caixa.servico.preco;

import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.modelo.PromocaoItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleBiFunction;

/**
 * Escolhe pacotes sobre as unidades que sobraram (sem A_PARTIR_DE), em centavos.
 * Cada pacote leva as unidades mais caras que ainda restam em cada categoria, o
 * que maximiza a economia para uma mesma quantidade de unidades consumidas.
 *
 * <p>{@link #exata()}: programação dinâmica; estado = quantas unidades restam em
 * cada categoria usada por algum pacote, memo em array indexado por base mista.
 * Empate: o primeiro pacote na ordem recebida (por id).
 * <p>{@link #gulosa()}: para carrinhos grandes demais; o melhor de duas
 * heurísticas (maior economia por pacote; maior economia por unidade).
 */
public final class BuscaDePacotes {

    /** Economia total e quais pacotes entraram (quantas vezes e quanto economizou cada um). */
    public record Resultado(long economia, Map<Promocao, Integer> usados, Map<Promocao, Long> economiaPorPacote) {
        static final Resultado NENHUM = new Resultado(0, Map.of(), Map.of());
    }

    private final List<Promocao> pacotes;
    private final List<Long> categorias = new ArrayList<>();
    private final long[][] acumulado; // acumulado[c][k] = soma das k unidades mais caras
    private final int[] total;
    private final long[] base;
    private final int[][] usoPorPacote;
    private final long[] precoPacote;
    private final long estados;

    /**
     * @param restantes unidades restantes por categoria (preços em centavos, em qualquer ordem)
     * @param todos     pacotes ativos, na ordem de desempate (por id)
     */
    public BuscaDePacotes(Map<Long, List<Long>> restantes, List<Promocao> todos) {
        this.pacotes = todos.stream()
                .filter(p -> p.getItens().stream()
                        .allMatch(i -> restantes
                                        .getOrDefault(i.getCategoria().getId(), List.of())
                                        .size()
                                >= i.getQuantidade()))
                .toList();
        for (Promocao p : pacotes) {
            for (PromocaoItem i : p.getItens()) {
                if (!categorias.contains(i.getCategoria().getId()))
                    categorias.add(i.getCategoria().getId());
            }
        }
        int n = categorias.size();
        acumulado = new long[n][];
        total = new int[n];
        base = new long[n];
        long produto = 1;
        for (int c = 0; c < n; c++) {
            List<Long> precos = new ArrayList<>(restantes.get(categorias.get(c)));
            precos.sort(Comparator.reverseOrder());
            total[c] = precos.size();
            acumulado[c] = new long[precos.size() + 1];
            for (int k = 0; k < precos.size(); k++) acumulado[c][k + 1] = acumulado[c][k] + precos.get(k);
            base[c] = produto;
            produto = produto > Long.MAX_VALUE / (total[c] + 1) ? Long.MAX_VALUE : produto * (total[c] + 1);
        }
        this.estados = pacotes.isEmpty() ? 1 : produto;
        usoPorPacote = new int[pacotes.size()][n];
        precoPacote = new long[pacotes.size()];
        for (int p = 0; p < pacotes.size(); p++) {
            for (PromocaoItem i : pacotes.get(p).getItens()) {
                usoPorPacote[p][categorias.indexOf(i.getCategoria().getId())] += i.getQuantidade();
            }
            precoPacote[p] = CalculadoraDePreco.centavos(pacotes.get(p).getPreco());
        }
    }

    /** Tamanho do espaço de estados da busca exata (custo de {@link #exata()}). */
    public long estados() {
        return estados;
    }

    // ---------- busca exata ----------

    public Resultado exata() {
        if (pacotes.isEmpty()) return Resultado.NENHUM;
        if (estados > Integer.MAX_VALUE)
            throw new IllegalStateException("Estados demais para a busca exata: " + estados);
        long[] memo = new long[(int) estados];
        int[] escolha = new int[(int) estados];
        Arrays.fill(memo, -1);
        int inicial = inicial();
        economia(inicial, memo, escolha);

        int[] vezes = new int[pacotes.size()];
        long[] ganhos = new long[pacotes.size()];
        int estado = inicial;
        while (escolha[estado] >= 0) {
            int p = escolha[estado];
            int proximo = estado;
            for (int c = 0; c < total.length; c++) proximo -= usoPorPacote[p][c] * (int) base[c];
            vezes[p]++;
            ganhos[p] += memo[estado] - memo[proximo];
            estado = proximo;
        }
        return resultado(memo[inicial], vezes, ganhos);
    }

    private int inicial() {
        int e = 0;
        for (int c = 0; c < total.length; c++) e += total[c] * (int) base[c];
        return e;
    }

    private long economia(int estado, long[] memo, int[] escolha) {
        if (memo[estado] >= 0) return memo[estado];
        long melhor = 0;
        int melhorPacote = -1;
        for (int p = 0; p < pacotes.size(); p++) {
            long normal = 0;
            int proximo = estado;
            boolean cabe = true;
            for (int c = 0; c < total.length; c++) {
                int q = usoPorPacote[p][c];
                if (q == 0) continue;
                int resta = (int) ((estado / base[c]) % (total[c] + 1));
                if (resta < q) {
                    cabe = false;
                    break;
                }
                int inicio = total[c] - resta;
                normal += acumulado[c][inicio + q] - acumulado[c][inicio];
                proximo -= q * (int) base[c];
            }
            if (!cabe) continue;
            long ganho = normal - precoPacote[p];
            if (ganho <= 0) continue;
            long candidato = ganho + economia(proximo, memo, escolha);
            if (candidato > melhor) {
                melhor = candidato;
                melhorPacote = p;
            }
        }
        memo[estado] = melhor;
        escolha[estado] = melhorPacote;
        return melhor;
    }

    // ---------- heurísticas ----------

    /** O melhor entre "maior economia por pacote" e "maior economia por unidade". */
    public Resultado gulosa() {
        if (pacotes.isEmpty()) return Resultado.NENHUM;
        Resultado porPacote = gulosa((ganho, unidades) -> ganho);
        Resultado porUnidade = gulosa((ganho, unidades) -> (double) ganho / unidades);
        return porUnidade.economia() > porPacote.economia() ? porUnidade : porPacote;
    }

    private Resultado gulosa(ToDoubleBiFunction<Long, Integer> nota) {
        int[] resta = total.clone();
        int[] vezes = new int[pacotes.size()];
        long[] ganhos = new long[pacotes.size()];
        long economia = 0;
        while (true) {
            int melhor = -1;
            double melhorNota = 0;
            long melhorGanho = 0;
            for (int p = 0; p < pacotes.size(); p++) {
                long normal = 0;
                int unidades = 0;
                boolean cabe = true;
                for (int c = 0; c < total.length; c++) {
                    int q = usoPorPacote[p][c];
                    if (q == 0) continue;
                    if (resta[c] < q) {
                        cabe = false;
                        break;
                    }
                    int inicio = total[c] - resta[c];
                    normal += acumulado[c][inicio + q] - acumulado[c][inicio];
                    unidades += q;
                }
                if (!cabe) continue;
                long ganho = normal - precoPacote[p];
                if (ganho <= 0) continue;
                double n = nota.applyAsDouble(ganho, unidades);
                if (melhor < 0 || n > melhorNota) {
                    melhor = p;
                    melhorNota = n;
                    melhorGanho = ganho;
                }
            }
            if (melhor < 0) break;
            for (int c = 0; c < total.length; c++) resta[c] -= usoPorPacote[melhor][c];
            vezes[melhor]++;
            ganhos[melhor] += melhorGanho;
            economia += melhorGanho;
        }
        return resultado(economia, vezes, ganhos);
    }

    private Resultado resultado(long economia, int[] vezes, long[] ganhos) {
        Map<Promocao, Integer> usados = new LinkedHashMap<>();
        Map<Promocao, Long> porPacote = new HashMap<>();
        for (int p = 0; p < pacotes.size(); p++) {
            if (vezes[p] == 0) continue;
            usados.put(pacotes.get(p), vezes[p]);
            porPacote.put(pacotes.get(p), ganhos[p]);
        }
        return new Resultado(economia, usados, porPacote);
    }
}
