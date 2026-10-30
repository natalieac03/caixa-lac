package br.com.lac.caixa.controle;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Lê as quantidades digitadas nos formulários (campos vazios valem zero). */
final class Quantidades {

    private Quantidades() {
    }

    static int inteiro(String texto) {
        if (texto == null || texto.isBlank()) return 0;
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException ex) {
            throw new RegraDeNegocioException("Quantidade inválida: " + texto.trim());
        }
    }

    /** Campos qtd_{produtoId} -> mapa produtoId -> quantidade (só as maiores que zero). */
    static Map<Long, Integer> porProduto(Map<String, String> params) {
        Map<Long, Integer> resultado = new LinkedHashMap<>();
        params.forEach((chave, valor) -> {
            if (!chave.startsWith("qtd_")) return;
            int qtd = inteiro(valor);
            if (qtd < 0) throw new RegraDeNegocioException("Quantidade inválida: " + qtd);
            if (qtd > 0) resultado.put(Long.valueOf(chave.substring(4)), qtd);
        });
        return resultado;
    }
}
