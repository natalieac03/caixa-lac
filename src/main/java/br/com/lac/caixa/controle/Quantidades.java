package br.com.lac.caixa.controle;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Lê as quantidades digitadas nos formulários (campos vazios valem zero). */
final class Quantidades {

    private Quantidades() {}

    /** Teto por linha do carrinho (contas em centavos e a busca de pacotes ficam com tamanho conhecido). */
    static final int MAXIMO_POR_PRODUTO = 9999;

    static int inteiro(String texto) {
        if (texto == null || texto.isBlank()) return 0;
        String t = texto.trim();
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException ex) {
            // Número inteiro grande demais para int também passa do teto.
            if (t.matches("\\d+"))
                throw new RegraDeNegocioException("Quantidade máxima por produto: " + MAXIMO_POR_PRODUTO);
            throw new RegraDeNegocioException("Quantidade inválida: " + t);
        }
    }

    /** Campos qtd_{produtoId} -> mapa produtoId -> quantidade (só as maiores que zero). */
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
