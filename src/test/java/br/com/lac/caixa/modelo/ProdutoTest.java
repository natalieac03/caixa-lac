package br.com.lac.caixa.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.lac.caixa.dominio.EstoqueInsuficienteException;
import br.com.lac.caixa.dominio.RegraDeNegocioException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Baixa e reposição do estoque do produto. */
class ProdutoTest {

    @Test
    @DisplayName("Baixa e reposição recusam quantidade menor que 1 e nunca deixam o estoque negativo")
    void baixaEReposicaoProtegemEstoque() {
        var p = new Produto("Produto", BigDecimal.ONE, 2);

        assertThrows(RegraDeNegocioException.class, () -> p.baixar(0));
        assertThrows(RegraDeNegocioException.class, () -> p.repor(-1));
        assertThrows(EstoqueInsuficienteException.class, () -> p.baixar(3));
        assertEquals(2, p.getQuantidade());

        p.baixar(2);
        assertEquals(0, p.getQuantidade());
        p.repor(3);
        assertEquals(3, p.getQuantidade());
    }

    @Test
    @DisplayName("Cadastro recusa preço zero, quantidade negativa e promoção de preço zero")
    void cadastroValidaPrecoEQuantidade() {
        assertThrows(RegraDeNegocioException.class, () -> new Produto("P", BigDecimal.ZERO, 1));
        assertThrows(RegraDeNegocioException.class, () -> new Produto("P", BigDecimal.ONE, -1));
        assertThrows(
                RegraDeNegocioException.class, () -> new Promocao(List.of(), TipoPromocao.PACOTE, BigDecimal.ZERO));
    }
}
