package br.com.lac.caixa.controle;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Lê as quantidades digitadas nos formulários. */
final class Quantidades {

    private Quantidades() {}

    static final int MAXIMO_POR_PRODUTO = 9999;

    static int inteiro(String texto) {
        if (texto == null || texto.isBlank()) return 0;
        String t = texto.trim();
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException ex) {
            if (t.matches("\\d+"))
                throw new RegraDeNegocioException("Quantidade máxima por produto: " + MAXIMO_POR_PRODUTO);
            throw new RegraDeNegocioException("Quantidade inválida: " + t);
        }
    }

    static Map<Long, Integer> porProduto(Map<String, String> params) {
        Map<Long, Integer> resultado = new LinkedHashMap<>();
        params.forEach((chave, valor) -> {
            if (!chave.startsWith("qtd_")) return;
            long id;
            try {
                id = Long.parseLong(chave.substring(4));
            } catch (NumberFormatException ex) {
                throw new RegraDeNegocioException("Produto inválido no carrinho.");
            }
            int qtd = inteiro(valor);
            if (qtd < 0) throw new RegraDeNegocioException("Quantidade inválida: " + qtd);
            if (qtd > MAXIMO_POR_PRODUTO)
                throw new RegraDeNegocioException("Quantidade máxima por produto: " + MAXIMO_POR_PRODUTO);
            if (qtd > 0) resultado.put(id, qtd);
        });
        return resultado;
    }
}
