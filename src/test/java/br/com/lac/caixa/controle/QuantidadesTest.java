package br.com.lac.caixa.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Leitura das quantidades: teto por produto e chave inválida. */
class QuantidadesTest {

    @Test
    @DisplayName("Teto de 9999 por produto: 9999 passa, 10000 é recusado com mensagem")
    void teto() {
        assertEquals(Map.of(7L, 9999), Quantidades.porProduto(Map.of("qtd_7", "9999")));
        var erro = assertThrows(RegraDeNegocioException.class, () -> Quantidades.porProduto(Map.of("qtd_7", "10000")));
        assertEquals("Quantidade máxima por produto: 9999", erro.getMessage());
        assertThrows(RegraDeNegocioException.class, () -> Quantidades.porProduto(Map.of("qtd_7", "99999999999")));
    }

    @Test
    @DisplayName("Chave qtd_ que não é número vira erro de negócio (não erro 500)")
    void chaveNaoNumerica() {
        var erro = assertThrows(RegraDeNegocioException.class, () -> Quantidades.porProduto(Map.of("qtd_abc", "1")));
        assertEquals("Produto inválido no carrinho.", erro.getMessage());
        assertThrows(RegraDeNegocioException.class, () -> Quantidades.porProduto(Map.of("qtd_", "1")));
    }
}
