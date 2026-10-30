package br.com.lac.caixa.util;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
public final class Dinheiro {
    private Dinheiro() {}
    public static BigDecimal parse(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String valor = texto.trim();
        if (!valor.matches("-?(?:[0-9]+(?:[.,][0-9]{1,2})?|[0-9]{1,3}(?:\\.[0-9]{3})+,[0-9]{1,2})")) throw new IllegalArgumentException("Valor monetário inválido.");
        if (valor.contains(",")) valor = valor.replace(".", "").replace(",", ".");
        return new BigDecimal(valor);
    }
    public static String numero(BigDecimal valor) { return new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR"))).format(valor); }
    public static String reais(BigDecimal valor) { return "R$ " + numero(valor); }
}
