package br.com.lac.caixa.servico.preco;
import br.com.lac.caixa.modelo.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CalculadoraDePrecoTest {
    Categoria cat(long id) { var c = new Categoria("Categoria " + id); ReflectionTestUtils.setField(c,"id",id); return c; }
    Produto produto(long id, String preco, Categoria c) { var p = new Produto("Produto " + id,new BigDecimal(preco),100); p.setCategoria(c); ReflectionTestUtils.setField(p,"id",id); return p; }
    Promocao promo(TipoPromocao tipo, String preco, Categoria c, int qtd) { return new Promocao(List.of(new PromocaoItem(c,qtd)),tipo,new BigDecimal(preco)); }
    Orcamento calcular(Produto p, int qtd, Promocao... promos) { return new CalculadoraDePreco().calcular(new Carrinho(List.of(new Carrinho.Linha(p,qtd))),List.of(promos)); }
    void total(String valor, Orcamento o) { assertEquals(0,new BigDecimal(valor).compareTo(o.total())); }
    @Test void semPromocao() { total("18",calcular(produto(1,"6",cat(1)),3)); }
    @Test void aPartirDeAbaixoENoMinimo() { var c=cat(1); var p=produto(1,"6",c); var promo=promo(TipoPromocao.A_PARTIR_DE,"5",c,2); total("6",calcular(p,1,promo)); total("10",calcular(p,2,promo)); }
    @Test void pacoteComSobra() { var c=cat(1); total("16",calcular(produto(1,"6",c),3,promo(TipoPromocao.PACOTE,"10",c,2))); }
    @Test void comboDuasCategorias() { var a=cat(1); var b=cat(2); var promo=new Promocao(List.of(new PromocaoItem(a,2),new PromocaoItem(b,1)),TipoPromocao.PACOTE,new BigDecimal("10")); total("10",new CalculadoraDePreco().calcular(new Carrinho(List.of(new Carrinho.Linha(produto(1,"6",a),2),new Carrinho.Linha(produto(2,"4",b),1))),List.of(promo))); }
    @Test void produtoSemCategoria() { total("12",calcular(produto(1,"6",null),2,promo(TipoPromocao.A_PARTIR_DE,"1",cat(1),1))); }
    @Test void orcamentoImutavel() { var o=calcular(produto(1,"6",null),1); assertThrows(UnsupportedOperationException.class,()->o.itens().clear()); assertThrows(UnsupportedOperationException.class,()->o.descontos().clear()); }
    @Test void promocaoNaoAumentaPreco() { var c=cat(1); total("18",calcular(produto(1,"6",c),3,promo(TipoPromocao.A_PARTIR_DE,"7",c,2))); }
    @Test void menorPrecoIndependeDaOrdem() { var c=cat(1); var p=produto(1,"6",c); var a=promo(TipoPromocao.A_PARTIR_DE,"5",c,2); var b=promo(TipoPromocao.A_PARTIR_DE,"4",c,2); total("12",calcular(p,3,a,b)); total("12",calcular(p,3,b,a)); }
    @Test void combinacaoExataDePacotes() { var c=cat(1); total("5.60",calcular(produto(1,"2",c),4,promo(TipoPromocao.PACOTE,"4",c,3),promo(TipoPromocao.PACOTE,"2.80",c,2))); }
    @Test void pacotesSomamComAPartirDeEUsamMaisCaros() { var c=cat(1); var carrinho=new Carrinho(List.of(new Carrinho.Linha(produto(1,"10",c),1),new Carrinho.Linha(produto(2,"3",c),1))); total("7",new CalculadoraDePreco().calcular(carrinho,List.of(promo(TipoPromocao.A_PARTIR_DE,"8",c,2),promo(TipoPromocao.PACOTE,"4",c,1)))); }
}
