package br.com.lac.caixa.servico.preco;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.modelo.TipoPromocao;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class CalculadoraDesempenhoTest {

    static final long LIMITE_MS = 300;

    long medir(CenariosDeDesempenho.Cenario c, Orcamento[] saida) {
        long inicio = System.nanoTime();
        saida[0] = new CalculadoraDePreco().calcular(c.carrinho(), c.promocoes());
        return (System.nanoTime() - inicio) / 1_000_000;
    }

    @Test
    @DisplayName("6 categorias x 10 unidades + 6 A_PARTIR_DE em outras categorias: exato e em menos de 300 ms")
    void seisPorDezComAPartirDe() {
        var cenario = CenariosDeDesempenho.seisPorDezComAPartirDe();
        var orcamento = new Orcamento[1];

        long ms = medir(cenario, orcamento);

        assertTrue(ms < LIMITE_MS, "levou " + ms + " ms");
        assertFalse(orcamento[0].aproximado());
        assertEquals(0, new BigDecimal("414.00").compareTo(orcamento[0].total()));
    }

    @Test
    @DisplayName("7 categorias x 7 unidades: calculado (sem recusar) em menos de 300 ms")
    void setePorSete() {
        var cenario = CenariosDeDesempenho.setePorSete();
        var orcamento = new Orcamento[1];

        long ms = medir(cenario, orcamento);

        assertTrue(ms < LIMITE_MS, "levou " + ms + " ms");
        assertFalse(orcamento[0].aproximado(), "8^7 estados cabem no orçamento de trabalho");
        assertEquals(0, new BigDecimal("304.50").compareTo(orcamento[0].total()));
    }

    @Test
    @DisplayName("Acima do orçamento de trabalho: não recusa, usa heurística, marca aproximado e avisa no log")
    void carrinhoEnormeViraAproximado(CapturedOutput log) {
        var linhas = new ArrayList<Carrinho.Linha>();
        var promocoes = new ArrayList<Promocao>();
        Categoria[] cats = new Categoria[9];
        long normal = 0;
        for (int c = 0; c < 9; c++) {
            cats[c] = CenariosDeDesempenho.categoria(c + 1);
            linhas.add(new Carrinho.Linha(CenariosDeDesempenho.produto(c + 1, "6.00", cats[c]), 9));
            normal += 9 * 600;
            promocoes.add(CenariosDeDesempenho.promocao(c + 1, TipoPromocao.PACOTE, "15.00", cats[c], 3));
        }
        promocoes.add(CenariosDeDesempenho.promocao(20, TipoPromocao.PACOTE, "10.00", cats[0], 1, cats[8], 1));
        long inicio = System.nanoTime();

        var orcamento = new CalculadoraDePreco().calcular(new Carrinho(linhas), promocoes);

        long ms = (System.nanoTime() - inicio) / 1_000_000;
        assertTrue(orcamento.aproximado());
        assertTrue(ms < LIMITE_MS, "levou " + ms + " ms");
        // 9 categorias x 3 pacotes de 15 = 405,00 (a heurística acha o ótimo aqui).
        assertEquals(0, new BigDecimal("405.00").compareTo(orcamento.total()));
        assertTrue(orcamento.total().movePointRight(2).longValue() < normal);
        assertTrue(log.getAll().contains("orçamento marcado como aproximado"));
    }

    @Test
    @DisplayName("Mais de 12 A_PARTIR_DE ligadas a pacotes: tenta alguns subconjuntos e marca aproximado")
    void aPartirDeDemais() {
        var cat = CenariosDeDesempenho.categoria(1);
        var linhas = List.of(new Carrinho.Linha(CenariosDeDesempenho.produto(1, "10.00", cat), 6));
        var promocoes = new ArrayList<Promocao>();
        for (int i = 0; i < 14; i++) {
            promocoes.add(CenariosDeDesempenho.promocao(i + 1, TipoPromocao.A_PARTIR_DE, (9 - i % 3) + ".00", cat, 2));
        }
        promocoes.add(CenariosDeDesempenho.promocao(99, TipoPromocao.PACOTE, "50.00", cat, 6));

        var orcamento = new CalculadoraDePreco().calcular(new Carrinho(linhas), promocoes);

        assertTrue(orcamento.aproximado());
        // Melhor A_PARTIR_DE: 6 x 7,00 = 42,00 (contra o pacote de 50,00).
        assertEquals(0, new BigDecimal("42.00").compareTo(orcamento.total()));
    }
}
