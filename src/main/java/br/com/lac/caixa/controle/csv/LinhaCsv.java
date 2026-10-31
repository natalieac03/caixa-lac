package br.com.lac.caixa.controle.csv;

import br.com.lac.caixa.util.Dinheiro;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Uma linha de CSV: junta os campos com ';', texto entre aspas, dinheiro com vírgula decimal. */
public final class LinhaCsv {

    record Cru(String texto) {}

    private LinhaCsv() {}

    public static Object cru(String texto) {
        return new Cru(texto);
    }

    public static String de(Object... campos) {
        return Arrays.stream(campos).map(LinhaCsv::formatar).collect(Collectors.joining(";")) + "\n";
    }

    private static String formatar(Object campo) {
        if (campo == null) return "";
        if (campo instanceof Cru c) return c.texto();
        if (campo instanceof String s) return ExportadorCSV.campo(s);
        if (campo instanceof BigDecimal v) return Dinheiro.numero(v);
        return String.valueOf(campo);
    }
}
