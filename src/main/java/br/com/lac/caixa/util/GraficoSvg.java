package br.com.lac.caixa.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/** Gráficos em SVG gerados no servidor para o relatório impresso. */
public final class GraficoSvg {
    private static final String ROXO = "#8a00f4";
    private static final String LARANJA = "#f48d00";
    private static final String TINTA = "#231a38";
    private static final String SUAVE = "#625878";

    private GraficoSvg() {}

    static String esc(String texto) {
        if (texto == null) return "";
        return texto.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String n(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static double proporcao(BigDecimal valor, BigDecimal maximo) {
        if (maximo.signum() == 0) return 0;
        return valor.divide(maximo, 6, RoundingMode.HALF_EVEN).doubleValue();
    }

    public static String barrasHorizontais(
            String titulo, List<String> rotulos, List<BigDecimal> valores, List<String> textos) {
        int linha = 26;
        int rotulo = 180;
        int area = 330;
        int largura = rotulo + area + 110;
        int altura = 30 + linha * rotulos.size();
        BigDecimal maximo = valores.stream().map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::max);
        StringBuilder svg = abrir(titulo, largura, altura);
        for (int i = 0; i < rotulos.size(); i++) {
            double y = 24 + i * linha;
            double w = Math.max(0, proporcao(valores.get(i).max(BigDecimal.ZERO), maximo)) * area;
            svg.append("<text x=\"")
                    .append(rotulo - 8)
                    .append("\" y=\"")
                    .append(n(y + 13))
                    .append("\" text-anchor=\"end\" font-size=\"12\" fill=\"")
                    .append(TINTA)
                    .append("\">")
                    .append(esc(rotulos.get(i)))
                    .append("</text>")
                    .append("<rect x=\"")
                    .append(rotulo)
                    .append("\" y=\"")
                    .append(n(y))
                    .append("\" width=\"")
                    .append(n(w))
                    .append("\" height=\"18\" fill=\"")
                    .append(ROXO)
                    .append("\"/>")
                    .append("<text x=\"")
                    .append(n(rotulo + w + 6))
                    .append("\" y=\"")
                    .append(n(y + 13))
                    .append("\" font-size=\"12\" fill=\"")
                    .append(TINTA)
                    .append("\">")
                    .append(esc(textos.get(i)))
                    .append("</text>");
        }
        return svg.append("</svg>").toString();
    }

    public static String colunasAgrupadas(
            String titulo,
            List<String> grupos,
            String nomeA,
            List<BigDecimal> a,
            List<String> textosA,
            String nomeB,
            List<BigDecimal> b,
            List<String> textosB) {
        int grupo = Math.max(110, 560 / Math.max(1, grupos.size()));
        int largura = 40 + grupo * grupos.size();
        int topo = 60;
        int area = 220;
        BigDecimal max = BigDecimal.ZERO;
        BigDecimal min = BigDecimal.ZERO;
        for (BigDecimal v : a) {
            max = max.max(v);
            min = min.min(v);
        }
        for (BigDecimal v : b) {
            max = max.max(v);
            min = min.min(v);
        }
        BigDecimal faixa = max.subtract(min);
        double zero = topo + area * proporcao(max, faixa.signum() == 0 ? BigDecimal.ONE : faixa);
        int altura = topo + area + 50;
        StringBuilder svg = abrir(titulo, largura, altura);
        svg.append(
                        "<defs><pattern id=\"listras\" width=\"6\" height=\"6\" patternUnits=\"userSpaceOnUse\" patternTransform=\"rotate(45)\">")
                .append("<rect width=\"6\" height=\"6\" fill=\"")
                .append(LARANJA)
                .append("\"/>")
                .append(
                        "<line x1=\"0\" y1=\"0\" x2=\"0\" y2=\"6\" stroke=\"#ffffff\" stroke-width=\"2\"/></pattern></defs>");
        svg.append("<rect x=\"20\" y=\"28\" width=\"14\" height=\"14\" fill=\"")
                .append(ROXO)
                .append("\"/>")
                .append("<text x=\"40\" y=\"40\" font-size=\"12\" fill=\"")
                .append(TINTA)
                .append("\">")
                .append(esc(nomeA))
                .append("</text>")
                .append("<rect x=\"170\" y=\"28\" width=\"14\" height=\"14\" fill=\"url(#listras)\"/>")
                .append("<text x=\"190\" y=\"40\" font-size=\"12\" fill=\"")
                .append(TINTA)
                .append("\">")
                .append(esc(nomeB))
                .append("</text>");
        svg.append("<line x1=\"20\" x2=\"")
                .append(largura - 10)
                .append("\" y1=\"")
                .append(n(zero))
                .append("\" y2=\"")
                .append(n(zero))
                .append("\" stroke=\"")
                .append(SUAVE)
                .append("\"/>");
        double escala = faixa.signum() == 0 ? 0 : area / faixa.doubleValue();
        for (int i = 0; i < grupos.size(); i++) {
            double x = 30 + i * grupo;
            double coluna = (grupo - 30) / 2.0;
            coluna(svg, x, coluna, zero, a.get(i).doubleValue() * escala, ROXO, textosA.get(i));
            coluna(svg, x + coluna + 4, coluna, zero, b.get(i).doubleValue() * escala, "url(#listras)", textosB.get(i));
            svg.append("<text x=\"")
                    .append(n(x + coluna))
                    .append("\" y=\"")
                    .append(topo + area + 36)
                    .append("\" text-anchor=\"middle\" font-size=\"12\" fill=\"")
                    .append(TINTA)
                    .append("\">")
                    .append(esc(grupos.get(i)))
                    .append("</text>");
        }
        return svg.append("</svg>").toString();
    }

    private static void coluna(StringBuilder svg, double x, double w, double zero, double h, String cor, String texto) {
        double y = h >= 0 ? zero - h : zero;
        svg.append("<rect x=\"")
                .append(n(x))
                .append("\" y=\"")
                .append(n(y))
                .append("\" width=\"")
                .append(n(w))
                .append("\" height=\"")
                .append(n(Math.abs(h)))
                .append("\" fill=\"")
                .append(cor)
                .append("\"/>")
                .append("<text x=\"")
                .append(n(x + w / 2))
                .append("\" y=\"")
                .append(n(h >= 0 ? y - 4 : y + Math.abs(h) + 12))
                .append("\" text-anchor=\"middle\" font-size=\"10\" fill=\"")
                .append(TINTA)
                .append("\">")
                .append(esc(texto))
                .append("</text>");
    }

    private static StringBuilder abrir(String titulo, int largura, int altura) {
        return new StringBuilder(
                        "<svg xmlns=\"http://www.w3.org/2000/svg\" role=\"img\" class=\"grafico-svg\" viewBox=\"0 0 ")
                .append(largura)
                .append(' ')
                .append(altura)
                .append("\" width=\"100%\" aria-label=\"")
                .append(esc(titulo))
                .append("\"><title>")
                .append(esc(titulo))
                .append("</title>")
                .append("<text x=\"10\" y=\"16\" font-size=\"13\" font-weight=\"700\" fill=\"")
                .append(TINTA)
                .append("\">")
                .append(esc(titulo))
                .append("</text>");
    }
}
