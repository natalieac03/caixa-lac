package br.com.lac.caixa.util;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formata e lê valores em reais, para tela, mensagens e CSV. */
public final class Dinheiro {

    private static final DecimalFormatSymbols PT_BR = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR"));
    private static final String FORMATO_VALIDO =
            "-?(?:[0-9]+(?:[.,][0-9]{1,2})?|[0-9]{1,3}(?:\\.[0-9]{3})+,[0-9]{1,2})";

    private Dinheiro() {}

    public static BigDecimal parse(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String valor = texto.trim();
        if (!valor.matches(FORMATO_VALIDO)) {
            throw new RegraDeNegocioException("Valor monetário inválido: " + valor);
        }
        if (valor.contains(",")) valor = valor.replace(".", "").replace(",", ".");
        return new BigDecimal(valor);
    }

    public static String numero(BigDecimal valor) {
        return new DecimalFormat("0.00", PT_BR).format(valor);
    }

    public static String reais(BigDecimal valor) {
        return "R$ " + new DecimalFormat("#,##0.00", PT_BR).format(valor);
    }
}
