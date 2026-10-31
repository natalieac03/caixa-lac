package br.com.lac.caixa.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CampoCsvTest {

    @Test
    @DisplayName("Campo de texto: aspas dobradas e apóstrofo antes de = + - @ tab e CR")
    void campo() {
        assertEquals("\"'=HYPERLINK(\"\"x\"\")\"", ExportadorCSV.campo("=HYPERLINK(\"x\")"));
        assertEquals("\"'+1\"", ExportadorCSV.campo("+1"));
        assertEquals("\"'-2+3\"", ExportadorCSV.campo("-2+3"));
        assertEquals("\"'@SUM(A1)\"", ExportadorCSV.campo("@SUM(A1)"));
        assertEquals("\"'\t=1\"", ExportadorCSV.campo("\t=1"));
        assertEquals("\"'\r=1\"", ExportadorCSV.campo("\r=1"));
        assertEquals("\"Bóton = 6\"", ExportadorCSV.campo("Bóton = 6"));
        assertEquals("\"\"", ExportadorCSV.campo(""));
        assertEquals("", ExportadorCSV.campo(null));
    }
}
