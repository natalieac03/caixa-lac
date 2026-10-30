package br.com.lac.caixa.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.repositorio.CategoriaRepository;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Validação no servidor dos formulários de estoque e promoção. */
@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:estoque;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class EstoqueHttpTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    ProdutoRepository produtos;

    @Autowired
    CategoriaRepository categorias;

    Produto p;
    Categoria cat;

    @BeforeEach
    void preparar() {
        cat = categorias.save(new Categoria("Estoque " + System.nanoTime()));
        p = new Produto("Produto", new BigDecimal("6"), 5);
        p.setCategoria(cat);
        p = produtos.save(p);
    }

    /** POST logado com os campos; espera redirect com a mensagem de erro no flash. */
    void assertRecusa(String url, String mensagem, String... campos) throws Exception {
        var req = post(url).sessionAttr("autenticado", true);
        for (int i = 0; i < campos.length; i += 2) req.param(campos[i], campos[i + 1]);
        mvc.perform(req).andExpect(status().is3xxRedirection()).andExpect(flash().attribute("erro", mensagem));
    }

    int estoque() {
        return produtos.findById(p.getId()).orElseThrow().getQuantidade();
    }

    @Test
    @DisplayName("Adicionar e remover unidades exigem pelo menos 1 unidade, sem mudar o estoque")
    void adicionarERemoverExigemPeloMenosUmaUnidade() throws Exception {
        // Arrange
        String base = "/estoque/" + p.getId();

        // Act / Assert
        assertRecusa(base + "/adicionar", "Quantidade deve ser maior que zero.", "quantidade", "0");
        assertRecusa(base + "/adicionar", "Quantidade deve ser maior que zero.", "quantidade", "-3");
        assertRecusa(base + "/remover-unidades", "Quantidade deve ser maior que zero.", "quantidade", "0");
        assertEquals(5, estoque());
    }

    @Test
    @DisplayName("A validação dos parâmetros roda antes de buscar o produto ou a categoria")
    void validacaoRodaAntesDeBuscarOProduto() throws Exception {
        // Sem @Min a mensagem seria "Produto não encontrado"; sem @PositiveOrZero, "Escolha uma categoria".
        assertRecusa("/estoque/999999/adicionar", "Quantidade deve ser maior que zero.", "quantidade", "0");
        assertRecusa("/estoque/999999/remover-unidades", "Quantidade deve ser maior que zero.", "quantidade", "0");
        assertRecusa(
                "/estoque/novo",
                "Quantidade não pode ser negativa.",
                "nome",
                "Novo",
                "preco",
                "2,00",
                "quantidade",
                "-1");
    }

    @Test
    @DisplayName("Cadastro recusa quantidade negativa e preço zero, sem criar produto")
    void cadastroRecusaQuantidadeNegativaEPrecoZero() throws Exception {
        // Arrange
        long antes = produtos.count();
        String categoria = cat.getId().toString();

        // Act / Assert
        assertRecusa(
                "/estoque/novo",
                "Quantidade não pode ser negativa.",
                "nome",
                "Novo",
                "preco",
                "2,00",
                "quantidade",
                "-1",
                "categoriaId",
                categoria);
        assertRecusa(
                "/estoque/novo",
                "Preço deve ser maior que zero.",
                "nome",
                "Novo",
                "preco",
                "0,00",
                "quantidade",
                "1",
                "categoriaId",
                categoria);
        assertEquals(antes, produtos.count());
    }

    @Test
    @DisplayName("Promoção com preço zero é recusada")
    void promocaoRecusaPrecoZero() throws Exception {
        assertRecusa(
                "/descontos/novo",
                "Preço deve ser maior que zero.",
                "tipo",
                "PACOTE",
                "preco",
                "0",
                "qtdCat_" + cat.getId(),
                "2");
    }

    @Test
    @DisplayName("Cadastro válido (preço \"1.234,50\", quantidade zero) volta ao estoque com sucesso")
    void cadastroValidoRedirecionaParaEstoque() throws Exception {
        mvc.perform(post("/estoque/novo")
                        .sessionAttr("autenticado", true)
                        .param("nome", "Válido")
                        .param("preco", "1.234,50")
                        .param("quantidade", "0")
                        .param("categoriaId", cat.getId().toString()))
                .andExpect(redirectedUrl("/estoque"))
                .andExpect(flash().attributeExists("sucesso"));
    }
}
