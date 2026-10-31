package br.com.lac.caixa.controle;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.modelo.PromocaoItem;
import br.com.lac.caixa.modelo.TipoPromocao;
import br.com.lac.caixa.repositorio.CategoriaRepository;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import br.com.lac.caixa.repositorio.PromocaoRepository;
import br.com.lac.caixa.repositorio.VendaRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Venda com carrinho grande e pacotes é aceita pela tela. */
@SpringBootTest(
        properties = {
            "spring.profiles.active=test",
            "spring.datasource.url=jdbc:h2:mem:carrinho_grande;DB_CLOSE_DELAY=-1"
        })
@AutoConfigureMockMvc
class CarrinhoGrandeHttpTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    CategoriaRepository categorias;

    @Autowired
    ProdutoRepository produtos;

    @Autowired
    PromocaoRepository promocoes;

    @Autowired
    VendaRepository vendas;

    @Test
    @DisplayName("Venda de 7 categorias x 7 unidades com pacotes é aceita (antes: \"Divida a venda\")")
    void setePorSeteEAceita() throws Exception {
        var req = post("/venda").sessionAttr("autenticado", true).param("pag_forma_0", "PIX");
        Categoria anterior = null;
        for (int c = 0; c < 7; c++) {
            var cat = categorias.save(new Categoria("Grande " + c));
            var p = new Produto("Grande " + c, new BigDecimal("6"), 100);
            p.setCategoria(cat);
            p = produtos.save(p);
            promocoes.save(new Promocao(List.of(new PromocaoItem(cat, 3)), TipoPromocao.PACOTE, new BigDecimal("15")));
            if (anterior != null) {
                promocoes.save(new Promocao(
                        List.of(new PromocaoItem(anterior, 1), new PromocaoItem(cat, 1)),
                        TipoPromocao.PACOTE,
                        new BigDecimal("11")));
            }
            anterior = cat;
            req.param("qtd_" + p.getId(), "7");
        }
        String token = UUID.randomUUID().toString();

        mvc.perform(req.param("token", token))
                .andExpect(redirectedUrl("/historico"))
                .andExpect(flash().attributeExists("sucesso"));

        assertTrue(vendas.findByToken(token).isPresent());
    }
}
