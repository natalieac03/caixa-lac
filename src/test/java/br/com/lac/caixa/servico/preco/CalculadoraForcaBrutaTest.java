package br.com.lac.caixa.servico.preco;

import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.modelo.PromocaoItem;
import br.com.lac.caixa.modelo.TipoPromocao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Teste de propriedade: em carrinhos pequenos e aleatórios (semente fixa), a
 * calculadora dá o mesmo total que uma força bruta escrita de forma independente.
 * A força bruta tenta todo subconjunto de A_PARTIR_DE e, nos pacotes, toda escolha
 * de QUAIS unidades entram (não supõe que as mais caras entram primeiro).
 */
class CalculadoraForcaBrutaTest {

    static final int CASOS = 500;
    static final long SEMENTE = 20261030L;

    /** Uma unidade do carrinho: categoria e preço normal em centavos. */
    record Unidade(long categoria, long preco) {}

    @Test
    @DisplayName("500 carrinhos aleatórios: mesmo total que a força bruta, sem aproximação")
    void mesmoTotalQueAForcaBruta() {
        var aleatorio = new Random(SEMENTE);
        int conferidos = 0;
        while (conferidos < CASOS) {
            var caso = sortear(aleatorio);
            if (caso.carrinho().itens().isEmpty()) continue;

            Orcamento orcamento = new CalculadoraDePreco().calcular(caso.carrinho(), caso.promocoes());
            long esperado = forcaBruta(caso);

            assertEquals(esperado, centavos(orcamento.total()), () -> "caso " + caso);
            assertFalse(orcamento.aproximado(), "carrinho pequeno nunca é aproximado");
            conferidos++;
        }
    }

    // ---------- sorteio ----------

    static CenariosDeDesempenho.Cenario sortear(Random r) {
        int quantasCategorias = 1 + r.nextInt(3);
        Categoria[] cats = new Categoria[quantasCategorias];
        for (int c = 0; c < quantasCategorias; c++) cats[c] = CenariosDeDesempenho.categoria(c + 1);

        var linhas = new ArrayList<Carrinho.Linha>();
        int unidades = 0, maximo = 1 + r.nextInt(8);
        long idProduto = 1;
        for (Categoria cat : cats) {
            for (int k = 0; k < 2 && unidades < maximo; k++) {
                int q = Math.min(r.nextInt(4), maximo - unidades);
                if (q == 0) continue;
                String preco = BigDecimal.valueOf(150 + 50L * r.nextInt(14), 2).toPlainString();
                linhas.add(new Carrinho.Linha(CenariosDeDesempenho.produto(idProduto++, preco, cat), q));
                unidades += q;
            }
        }

        var promocoes = new ArrayList<Promocao>();
        int quantasPromocoes = r.nextInt(5);
        for (long id = 1; id <= quantasPromocoes; id++) {
            var tipo = r.nextBoolean() ? TipoPromocao.A_PARTIR_DE : TipoPromocao.PACOTE;
            var args = new ArrayList<Object>();
            int partes = 1 + r.nextInt(Math.min(2, quantasCategorias));
            var usadas = new ArrayList<Categoria>();
            while (usadas.size() < partes) {
                Categoria c = cats[r.nextInt(quantasCategorias)];
                if (!usadas.contains(c)) usadas.add(c);
            }
            for (Categoria c : usadas) { args.add(c); args.add(1 + r.nextInt(3)); }
            long precoCentavos = tipo == TipoPromocao.A_PARTIR_DE ? 100 + 50L * r.nextInt(10) : 150 + 50L * r.nextInt(20);
            String preco = BigDecimal.valueOf(precoCentavos, 2).toPlainString();
            promocoes.add(CenariosDeDesempenho.promocao(id, tipo, preco, args.toArray()));
        }
        return new CenariosDeDesempenho.Cenario(new Carrinho(linhas), promocoes);
    }

    // ---------- força bruta ----------

    static long forcaBruta(CenariosDeDesempenho.Cenario caso) {
        Map<Long, Integer> porCategoria = new HashMap<>();
        for (var l : caso.carrinho().itens()) {
            porCategoria.merge(l.produto().getCategoria().getId(), l.quantidade(), Integer::sum);
        }
        List<Promocao> aPartirDe = caso.promocoes().stream()
                .filter(p -> p.getTipo() == TipoPromocao.A_PARTIR_DE).toList();
        List<Promocao> pacotes = caso.promocoes().stream()
                .filter(p -> p.getTipo() == TipoPromocao.PACOTE).toList();

        long melhor = Long.MAX_VALUE;
        for (int mascara = 0; mascara < (1 << aPartirDe.size()); mascara++) {
            Map<Long, Long> precoPromo = new HashMap<>();
            boolean valido = true;
            for (int b = 0; b < aPartirDe.size(); b++) {
                if ((mascara & (1 << b)) == 0) continue;
                Promocao p = aPartirDe.get(b);
                if (p.combosDisponiveis(porCategoria) == 0) { valido = false; break; }
                for (PromocaoItem i : p.getItens()) precoPromo.merge(i.getCategoria().getId(), centavos(p.getPreco()), Math::min);
            }
            if (!valido) continue;

            long total = 0;
            var restantes = new ArrayList<Unidade>();
            for (var l : caso.carrinho().itens()) {
                long cat = l.produto().getCategoria().getId();
                long normal = centavos(l.produto().getPreco());
                Long promo = precoPromo.get(cat);
                for (int k = 0; k < l.quantidade(); k++) {
                    if (promo != null && promo < normal) total += promo;
                    else { total += normal; restantes.add(new Unidade(cat, normal)); }
                }
            }
            melhor = Math.min(melhor, total - economiaMaxima(restantes, pacotes, new HashMap<>()));
        }
        return melhor;
    }

    /** Maior economia com pacotes, tentando toda escolha de unidades (memo pelo multiconjunto restante). */
    static long economiaMaxima(List<Unidade> restantes, List<Promocao> pacotes, Map<List<Unidade>, Long> memo) {
        var chave = restantes.stream().sorted((a, b) -> a.categoria() != b.categoria()
                ? Long.compare(a.categoria(), b.categoria()) : Long.compare(a.preco(), b.preco())).toList();
        Long conhecido = memo.get(chave);
        if (conhecido != null) return conhecido;
        long melhor = 0;
        for (Promocao pacote : pacotes) {
            for (List<Integer> escolha : escolhas(restantes, pacote)) {
                long normal = 0;
                for (int i : escolha) normal += restantes.get(i).preco();
                long ganho = normal - centavos(pacote.getPreco());
                if (ganho <= 0) continue;
                var sobra = new ArrayList<Unidade>();
                for (int i = 0; i < restantes.size(); i++) if (!escolha.contains(i)) sobra.add(restantes.get(i));
                melhor = Math.max(melhor, ganho + economiaMaxima(sobra, pacotes, memo));
            }
        }
        memo.put(chave, melhor);
        return melhor;
    }

    /** Todas as escolhas de índices de unidades que formam um pacote. */
    static List<List<Integer>> escolhas(List<Unidade> restantes, Promocao pacote) {
        List<List<Integer>> resultado = new ArrayList<>();
        resultado.add(new ArrayList<>());
        for (PromocaoItem item : pacote.getItens()) {
            var indices = new ArrayList<Integer>();
            for (int i = 0; i < restantes.size(); i++) {
                if (restantes.get(i).categoria() == item.getCategoria().getId()) indices.add(i);
            }
            var combinacoes = new ArrayList<List<Integer>>();
            combinar(indices, item.getQuantidade(), 0, new ArrayList<>(), combinacoes);
            var novo = new ArrayList<List<Integer>>();
            for (var parcial : resultado) {
                for (var c : combinacoes) {
                    var junto = new ArrayList<>(parcial);
                    junto.addAll(c);
                    novo.add(junto);
                }
            }
            resultado = novo;
        }
        return resultado;
    }

    static void combinar(List<Integer> indices, int k, int inicio, List<Integer> atual, List<List<Integer>> saida) {
        if (atual.size() == k) { saida.add(new ArrayList<>(atual)); return; }
        for (int i = inicio; i < indices.size(); i++) {
            atual.add(indices.get(i));
            combinar(indices, k, i + 1, atual, saida);
            atual.remove(atual.size() - 1);
        }
    }

    static long centavos(BigDecimal valor) {
        return valor.movePointRight(2).longValueExact();
    }
}
