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
    @Test void beanValidationDasEntidades() {
        var validador=jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        var p=new Produto("P",BigDecimal.ONE,1);
        org.springframework.test.util.ReflectionTestUtils.setField(p,"preco",BigDecimal.ZERO);
        org.springframework.test.util.ReflectionTestUtils.setField(p,"quantidade",-1);
        assertEquals(2,validador.validate(p).size());
        var promo=new Promocao(java.util.List.of(),TipoPromocao.PACOTE,BigDecimal.ONE);
        org.springframework.test.util.ReflectionTestUtils.setField(promo,"preco",new BigDecimal("-1"));
        assertEquals(1,validador.validate(promo).size());
        var item=new PromocaoItem(new Categoria("C"),1);
        org.springframework.test.util.ReflectionTestUtils.setField(item,"quantidade",0);
        assertEquals(1,validador.validate(item).size());
    }
    @Test void cadastroValidaPrecoEQuantidade() {
        assertThrows(RegraDeNegocioException.class,()->new Produto("P",BigDecimal.ZERO,1));
        assertThrows(RegraDeNegocioException.class,()->new Produto("P",BigDecimal.ONE,-1));
        assertThrows(RegraDeNegocioException.class,()->new Promocao(java.util.List.of(),TipoPromocao.PACOTE,BigDecimal.ZERO));
    }
}
