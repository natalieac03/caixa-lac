package br.com.lac.caixa.modelo;
import br.com.lac.caixa.servico.EstoqueInsuficienteException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class ProdutoTest {
    @Test void baixaEReposicaoProtegemEstoque() {
        var p=new Produto("Produto",BigDecimal.ONE,2);
        assertThrows(IllegalArgumentException.class,()->p.baixar(0));
        assertThrows(IllegalArgumentException.class,()->p.repor(-1));
        assertThrows(EstoqueInsuficienteException.class,()->p.baixar(3));
        assertEquals(2,p.getQuantidade()); p.baixar(2); assertEquals(0,p.getQuantidade()); p.repor(3); assertEquals(3,p.getQuantidade());
    }
    @Test void cadastroValidaPrecoEQuantidade() {
        assertThrows(IllegalArgumentException.class,()->new Produto("P",BigDecimal.ZERO,1));
        assertThrows(IllegalArgumentException.class,()->new Produto("P",BigDecimal.ONE,-1));
        assertThrows(IllegalArgumentException.class,()->new Promocao(java.util.List.of(),TipoPromocao.PACOTE,BigDecimal.ZERO));
    }
}
