package br.com.lac.caixa.util;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

/**
 * Divide um valor em centavos entre itens, proporcional a um peso (o valor
 * bruto de cada item), pelo método do maior resto: cada item recebe o piso da
 * sua parte e os centavos que sobram vão, um a um, para os maiores restos
 * (empate: o item que vem primeiro). A soma das partes é exatamente o valor.
 *
 * <p>O valor pode ser negativo (ajuste para mais): divide-se o módulo e as
 * partes saem negativas. Com todos os pesos zero, divide pelos pesos
 * alternativos (a quantidade de cada item). Classe pura, sem Spring nem banco.
 * A V12 repete este cálculo em SQL para as vendas antigas.
 */
public final class Rateio {
    private Rateio() {}

    /** Partes de {@code valor} proporcionais a {@code pesos}; se todos forem zero, a {@code reserva}. */
    public static long[] ratear(long valor, long[] pesos, long[] reserva) {
        long soma = Arrays.stream(pesos).sum();
        if (soma == 0 && valor != 0 && reserva != null) return ratear(valor, reserva, null);
        long[] partes = new long[pesos.length];
        if (valor == 0) return partes;
        if (soma <= 0 || Arrays.stream(pesos).anyMatch(p -> p < 0)) {
            throw new IllegalArgumentException("Pesos do rateio precisam ser positivos.");
        }
        long modulo = Math.abs(valor);
        long[] restos = new long[pesos.length];
        long distribuido = 0;
        for (int i = 0; i < pesos.length; i++) {
            long produto = Math.multiplyExact(modulo, pesos[i]);
            partes[i] = produto / soma;
            restos[i] = produto % soma;
            distribuido += partes[i];
        }
        long sobra = modulo - distribuido;
        Integer[] ordem = IntStream.range(0, pesos.length).boxed().toArray(Integer[]::new);
        Arrays.sort(ordem, Comparator.comparingLong((Integer i) -> -restos[i]).thenComparingInt(i -> i));
        for (int k = 0; k < sobra; k++) partes[ordem[k]]++;
        if (valor < 0) for (int i = 0; i < partes.length; i++) partes[i] = -partes[i];
        return partes;
    }

    public static long centavos(BigDecimal valor) {
        return valor.setScale(2).unscaledValue().longValueExact();
    }

    public static BigDecimal reais(long centavos) {
        return BigDecimal.valueOf(centavos, 2);
    }
}
