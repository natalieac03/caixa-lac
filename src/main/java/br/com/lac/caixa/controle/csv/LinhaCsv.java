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
        return switch (campo) {
            case null -> "";
            case Cru c -> c.texto();
            case String s -> ExportadorCSV.campo(s);
            case BigDecimal v -> Dinheiro.numero(v);
            default -> String.valueOf(campo);
        };
    }
}
