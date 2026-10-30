package br.com.lac.caixa.modelo;
import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.dominio.EstoqueInsuficienteException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class ProdutoTest {
    @Test void baixaEReposicaoProtegemEstoque() {
        var p=new Produto("Produto",BigDecimal.ONE,2);
        assertThrows(RegraDeNegocioException.class,()->p.baixar(0));
        assertThrows(RegraDeNegocioException.class,()->p.repor(-1));
        assertThrows(EstoqueInsuficienteException.class,()->p.baixar(3));
        assertEquals(2,p.getQuantidade()); p.baixar(2); assertEquals(0,p.getQuantidade()); p.repor(3); assertEquals(3,p.getQuantidade());
    }
    @Test void cadastroValidaPrecoEQuantidade() {
        assertThrows(RegraDeNegocioException.class,()->new Produto("P",BigDecimal.ZERO,1));
        assertThrows(RegraDeNegocioException.class,()->new Produto("P",BigDecimal.ONE,-1));
        assertThrows(RegraDeNegocioException.class,()->new Promocao(java.util.List.of(),TipoPromocao.PACOTE,BigDecimal.ZERO));
    }
}
