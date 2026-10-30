package br.com.lac.caixa.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RateioTest {

    static long[] c(long... centavos) {
        return centavos;
    }

    @Test
    @DisplayName("1 item: recebe o desconto inteiro")
    void umItem() {
        assertArrayEquals(c(250), Rateio.ratear(250, c(1800), null));
    }

    @Test
    @DisplayName("2 itens: proporcional ao bruto (R$ 6 e R$ 12, desconto R$ 3 -> 1,00 e 2,00)")
    void doisItens() {
        assertArrayEquals(c(100, 200), Rateio.ratear(300, c(600, 1200), null));
    }

    @Test
    @DisplayName("7 itens: soma exata e cada parte a menos de 1 centavo da proporção")
    void seteItens() {
        long[] brutos = c(600, 600, 800, 1200, 350, 990, 1);
        long[] partes = Rateio.ratear(777, brutos, null);
        assertEquals(777, Arrays.stream(partes).sum());
        conferirProporcao(777, brutos, partes);
    }

    @Test
    @DisplayName("Dízima: R$ 10,00 em 3 itens iguais -> 3,34 + 3,33 + 3,33 (o centavo vai para o primeiro)")
    void dizima() {
        assertArrayEquals(c(334, 333, 333), Rateio.ratear(1000, c(600, 600, 600), null));
        // Maior resto: 100 x (1, 1, 1) / 3 = 33,33... cada; o resto empata e o primeiro leva.
        assertArrayEquals(c(34, 33, 33), Rateio.ratear(100, c(1, 1, 1), null));
        // Restos diferentes: quem tem o maior resto leva o centavo, não o primeiro.
        assertArrayEquals(c(33, 67), Rateio.ratear(100, c(100, 200), null));
        assertArrayEquals(c(1, 1, 0), Rateio.ratear(2, c(300, 400, 300), null));
    }

    @Test
    @DisplayName("Ajuste para mais (desconto negativo) sai negativo; para menos soma ao desconto")
    void ajusteComSinal() {
        // Calculado 18,00, cobrado 20,00: desconto de -2,00.
        assertArrayEquals(c(-67, -133), Rateio.ratear(-200, c(600, 1200), null));
        assertArrayEquals(c(-34, -33, -33), Rateio.ratear(-100, c(1, 1, 1), null));
        // Calculado 18,00, cobrado 17,00: desconto de 1,00.
        assertArrayEquals(c(33, 67), Rateio.ratear(100, c(600, 1200), null));
    }

    @Test
    @DisplayName("Cortesia: o desconto é o bruto inteiro e cada item fica com líquido zero")
    void cortesia() {
        long[] brutos = c(600, 800, 333);
        long[] partes = Rateio.ratear(Arrays.stream(brutos).sum(), brutos, null);
        assertArrayEquals(brutos, partes);
    }

    @Test
    @DisplayName("Desconto zero: tudo zero; brutos todos zero: rateia pela reserva (quantidade)")
    void casosDeBorda() {
        assertArrayEquals(c(0, 0), Rateio.ratear(0, c(600, 1200), null));
        assertArrayEquals(c(-25, -75), Rateio.ratear(-100, c(0, 0), c(1, 3)));
    }

    @Test
    @DisplayName("Propriedade: 500 rateios aleatórios (semente fixa), soma exata e parte a < 1 centavo da proporção")
    void propriedade() {
        Random aleatorio = new Random(12345L);
        for (int caso = 0; caso < 500; caso++) {
            int n = 1 + aleatorio.nextInt(12);
            long[] brutos = new long[n];
            for (int i = 0; i < n; i++) brutos[i] = 1 + aleatorio.nextInt(20_000);
            long soma = Arrays.stream(brutos).sum();
            // De ajuste para mais (até -50%) até cortesia (o bruto inteiro).
            long valor = -soma / 2 + (long) (aleatorio.nextDouble() * (soma + soma / 2 + 1));
            valor = Math.min(valor, soma);

            long[] partes = Rateio.ratear(valor, brutos, null);

            assertEquals(valor, Arrays.stream(partes).sum(), "caso " + caso);
            conferirProporcao(valor, brutos, partes);
            for (int i = 0; i < n; i++) assertTrue(brutos[i] - partes[i] >= 0, "líquido nunca negativo");
        }
    }

    /** |parte - valor x bruto / soma| < 1 centavo. */
    static void conferirProporcao(long valor, long[] brutos, long[] partes) {
        BigDecimal soma = BigDecimal.valueOf(Arrays.stream(brutos).sum());
        for (int i = 0; i < brutos.length; i++) {
            BigDecimal exato = BigDecimal.valueOf(valor)
                    .multiply(BigDecimal.valueOf(brutos[i]))
                    .divide(soma, 10, RoundingMode.HALF_EVEN);
            BigDecimal erro = exato.subtract(BigDecimal.valueOf(partes[i])).abs();
            assertTrue(erro.compareTo(BigDecimal.ONE) < 0, "item " + i + ": " + partes[i] + " vs " + exato);
        }
    }
}
