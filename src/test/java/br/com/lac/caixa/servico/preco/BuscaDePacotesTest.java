package br.com.lac.caixa.servico.preco;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.modelo.TipoPromocao;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BuscaDePacotesTest {
    final Categoria a = CenariosDeDesempenho.categoria(1);
    final Categoria b = CenariosDeDesempenho.categoria(2);

    Promocao pacote(long id, String preco, Object... partes) {
        return CenariosDeDesempenho.promocao(id, TipoPromocao.PACOTE, preco, partes);
    }

    @Test
    @DisplayName("Busca exata acha a combinação ótima que a gulosa por pacote erra")
    void exataBateGulosa() {
        // 4 unidades de R$ 2,00: "3 por 4,00" economiza 2,00 de uma vez, mas dois "2 por 2,80" economizam 2,40.
        var restantes = Map.of(1L, List.of(200L, 200L, 200L, 200L));
        var tresPor4 = pacote(1, "4.00", a, 3);
        var doisPor280 = pacote(2, "2.80", a, 2);
        var busca = new BuscaDePacotes(restantes, List.of(tresPor4, doisPor280));

        var exata = busca.exata();
        var gulosa = busca.gulosa();

        assertEquals(240, exata.economia());
        assertEquals(Map.of(doisPor280, 2), exata.usados());
        assertTrue(gulosa.economia() <= exata.economia());
    }

    @Test
    @DisplayName("Pacote leva as unidades mais caras e combo entre categorias conta as duas")
    void maisCarasECombo() {
        var restantes = Map.of(1L, List.of(300L, 1000L), 2L, List.of(800L));
        var busca = new BuscaDePacotes(restantes, List.of(pacote(1, "10.00", a, 1, b, 1)));

        var r = busca.exata();

        assertEquals(1000 + 800 - 1000, r.economia());
        assertEquals(3 * 2, busca.estados());
    }

    @Test
    @DisplayName("Empate de economia: fica o pacote de menor id (o primeiro da lista)")
    void empatePorOrdem() {
        var restantes = Map.of(1L, List.of(600L, 600L));
        var primeiro = pacote(3, "10.00", a, 2);
        var segundo = pacote(5, "10.00", a, 2);

        var r = new BuscaDePacotes(restantes, List.of(primeiro, segundo)).exata();

        assertEquals(Map.of(primeiro, 1), r.usados());
    }

    @Test
    @DisplayName("Sem unidades suficientes ou sem pacote: economia zero e um estado só")
    void semPacoteQueCaiba() {
        var busca = new BuscaDePacotes(Map.of(1L, List.of(600L)), List.of(pacote(1, "5.00", a, 2)));

        assertEquals(0, busca.exata().economia());
        assertEquals(0, busca.gulosa().economia());
        assertEquals(1, busca.estados());
    }

    @Test
    @DisplayName("Heurísticas nunca prometem mais economia que a busca exata")
    void gulosaNuncaPassaDaExata() {
        var r = new Random(7);
        for (int caso = 0; caso < 300; caso++) {
            var precosA = List.of(100L + r.nextInt(5) * 100, 100L + r.nextInt(5) * 100, 100L + r.nextInt(5) * 100);
            var precosB = List.of(100L + r.nextInt(5) * 100, 100L + r.nextInt(5) * 100);
            var pacotes = List.of(
                    pacote(1, (1 + r.nextInt(6)) + ".00", a, 1 + r.nextInt(2)),
                    pacote(2, (1 + r.nextInt(8)) + ".00", a, 1, b, 1),
                    pacote(3, (1 + r.nextInt(6)) + ".00", b, 2));
            var busca = new BuscaDePacotes(Map.of(1L, precosA, 2L, precosB), pacotes);

            assertTrue(busca.gulosa().economia() <= busca.exata().economia(), "caso " + caso);
        }
    }
}
