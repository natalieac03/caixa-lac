package br.com.lac.caixa.controle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** O cartão principal da tela inicial tem contraste WCAG AA nos dois temas. */
class ContrasteDoTemaTest {

    static String css() throws Exception {
        try (var in = ContrasteDoTemaTest.class.getResourceAsStream("/static/css/estilo.css")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static Map<String, String> tokens(String css, String seletor) {
        int i = css.indexOf(seletor + " {");
        assertTrue(i >= 0, "sem o bloco " + seletor);
        String bloco = css.substring(i, css.indexOf('}', i));
        Map<String, String> t = new HashMap<>();
        Matcher m = Pattern.compile("--([a-z-]+):\\s*(#[0-9a-fA-F]{6})").matcher(bloco);
        while (m.find()) t.put(m.group(1), m.group(2));
        return t;
    }

    static double luminancia(String hex) {
        double[] c = new double[3];
        for (int k = 0; k < 3; k++) {
            double v = Integer.parseInt(hex.substring(1 + 2 * k, 3 + 2 * k), 16) / 255.0;
            c[k] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    static double contraste(String a, String b) {
        double la = luminancia(a), lb = luminancia(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    @ParameterizedTest(name = "{0}: {1} sobre {2}")
    @DisplayName("Nova venda: texto e ícone com contraste AA nos temas claro e escuro, também no hover")
    @CsvSource({
        ":root, roxo, on-roxo",
        ":root, roxo-forte, on-roxo",
        ":root[data-tema=\"escuro\"], roxo, on-roxo",
        ":root[data-tema=\"escuro\"], roxo-forte, on-roxo"
    })
    void contrasteAA(String seletor, String fundo, String texto) throws Exception {
        var t = tokens(css(), seletor);
        double c = contraste(t.get(fundo), t.get(texto));
        assertTrue(c >= 4.5, seletor + " " + fundo + "/" + texto + " = " + c);
    }
}
