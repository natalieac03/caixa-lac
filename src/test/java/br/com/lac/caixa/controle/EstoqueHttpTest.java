package br.com.lac.caixa.controle;

import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.repositorio.CategoriaRepository;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Validação no servidor dos formulários de estoque e promoção. */
@SpringBootTest(properties={"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:estoque;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class EstoqueHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ProdutoRepository produtos;
    @Autowired CategoriaRepository categorias;
    Produto p;
    Categoria cat;

    @BeforeEach void preparar() {
        cat=categorias.save(new Categoria("Estoque " + System.nanoTime()));
        p=new Produto("Produto",new BigDecimal("6"),5); p.setCategoria(cat); p=produtos.save(p);
    }
    void recusa(String url, String mensagem, String... params) throws Exception {
        var req=post(url).sessionAttr("autenticado",true);
        for (int i=0;i<params.length;i+=2) req.param(params[i],params[i+1]);
        mvc.perform(req).andExpect(status().is3xxRedirection()).andExpect(flash().attribute("erro",mensagem));
    }
    @Test void adicionarERemoverExigemPeloMenosUmaUnidade() throws Exception {
        recusa("/estoque/"+p.getId()+"/adicionar","Quantidade deve ser maior que zero.","quantidade","0");
        recusa("/estoque/"+p.getId()+"/adicionar","Quantidade deve ser maior que zero.","quantidade","-3");
        recusa("/estoque/"+p.getId()+"/remover-unidades","Quantidade deve ser maior que zero.","quantidade","0");
        assertEquals(5,produtos.findById(p.getId()).orElseThrow().getQuantidade());
    }
    @Test void validacaoRodaAntesDeBuscarOProduto() throws Exception {
        // Sem @Min a mensagem seria "Produto não encontrado"; sem @PositiveOrZero, "Escolha uma categoria".
        recusa("/estoque/999999/adicionar","Quantidade deve ser maior que zero.","quantidade","0");
        recusa("/estoque/999999/remover-unidades","Quantidade deve ser maior que zero.","quantidade","0");
        recusa("/estoque/novo","Quantidade não pode ser negativa.","nome","Novo","preco","2,00","quantidade","-1");
    }
    @Test void cadastroRecusaQuantidadeNegativaEPrecoZero() throws Exception {
        long antes=produtos.count();
        recusa("/estoque/novo","Quantidade não pode ser negativa.","nome","Novo","preco","2,00","quantidade","-1","categoriaId",cat.getId().toString());
        recusa("/estoque/novo","Preço deve ser maior que zero.","nome","Novo","preco","0,00","quantidade","1","categoriaId",cat.getId().toString());
        assertEquals(antes,produtos.count());
    }
    @Test void promocaoRecusaPrecoZero() throws Exception {
        recusa("/descontos/novo","Preço deve ser maior que zero.","tipo","PACOTE","preco","0","qtdCat_"+cat.getId(),"2");
    }
    @Test void cadastroValidoRedirecionaParaEstoque() throws Exception {
        mvc.perform(post("/estoque/novo").sessionAttr("autenticado",true).param("nome","Válido").param("preco","1.234,50").param("quantidade","0").param("categoriaId",cat.getId().toString()))
            .andExpect(redirectedUrl("/estoque")).andExpect(flash().attributeExists("sucesso"));
    }
}
