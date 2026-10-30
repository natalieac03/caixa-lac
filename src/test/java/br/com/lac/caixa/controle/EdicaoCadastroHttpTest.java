package br.com.lac.caixa.controle;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.modelo.PromocaoItem;
import br.com.lac.caixa.modelo.TipoPromocao;
import br.com.lac.caixa.repositorio.CategoriaRepository;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import br.com.lac.caixa.repositorio.PromocaoRepository;
import br.com.lac.caixa.servico.CaixaService;
import br.com.lac.caixa.servico.CategoriaService;
import br.com.lac.caixa.servico.EstoqueService;
import br.com.lac.caixa.servico.PagamentoInformado;
import br.com.lac.caixa.servico.PromocaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:edicao;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class EdicaoCadastroHttpTest {
    @Autowired MockMvc mvc;
    @Autowired CategoriaRepository categoriasRepo;
    @Autowired ProdutoRepository produtos;
    @Autowired PromocaoRepository promocoesRepo;
    @Autowired EstoqueService estoque;
    @Autowired CategoriaService categorias;
    @Autowired PromocaoService promocoes;
    @Autowired CaixaService caixa;

    Categoria cat;
    Produto p;

    @BeforeEach
    void preparar() {
        cat = categoriasRepo.save(new Categoria("Edição " + UUID.randomUUID()));
        p = new Produto("Bóton Editável", new BigDecimal("6"), 50);
        p.setCategoria(cat);
        p = produtos.save(p);
    }

    // ---------- produto ----------

    @Test
    @DisplayName("Editar produto muda nome, preço e categoria; venda antiga continua com o preço antigo no histórico e no CSV")
    void precoNovoSoValeParaVendaNova() throws Exception {
        var antiga = caixa.registrarVenda(Map.of(p.getId(), 3), List.of(PagamentoInformado.total(FormaPagamento.PIX)),
                null, null, UUID.randomUUID().toString()).venda();

        mvc.perform(post("/estoque/" + p.getId() + "/editar").sessionAttr("autenticado", true)
                        .param("nome", "Bóton Renovado").param("preco", "9,50").param("novaCategoria", "Categoria Nova " + p.getId()))
           .andExpect(redirectedUrl("/estoque"))
           .andExpect(flash().attribute("sucesso", containsString("Bóton Renovado")));

        var editado = produtos.findById(p.getId()).orElseThrow();
        assertEquals("Bóton Renovado", editado.getNome());
        assertEquals(0, new BigDecimal("9.50").compareTo(editado.getPreco()));
        assertEquals("Categoria Nova " + p.getId(), editado.getNomeCategoria());
        mvc.perform(get("/historico").sessionAttr("autenticado", true))
           .andExpect(content().string(containsString("18,00")));
        String csv = new String(mvc.perform(get("/historico/exportar.csv").sessionAttr("autenticado", true))
                .andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(csv.contains(antiga.getId() + ";"), csv);
        assertTrue(csv.contains(";18,00;18,00;"), "venda antiga com o valor da época: " + csv);
        mvc.perform(post("/venda/previa").sessionAttr("autenticado", true).param("qtd_" + p.getId(), "2"))
           .andExpect(content().string(containsString("\"total\":19.00")));
    }

    @Test
    @DisplayName("Editar produto valida preço maior que zero e nome não vazio, sem mudar nada")
    void edicaoDeProdutoValida() throws Exception {
        String url = "/estoque/" + p.getId() + "/editar";
        mvc.perform(post(url).sessionAttr("autenticado", true).param("nome", "X").param("preco", "0").param("categoriaId", cat.getId().toString()))
           .andExpect(flash().attribute("erro", "Preço deve ser maior que zero."));
        mvc.perform(post(url).sessionAttr("autenticado", true).param("nome", "   ").param("preco", "5").param("categoriaId", cat.getId().toString()))
           .andExpect(flash().attribute("erro", "O nome do produto não pode ficar vazio."));
        mvc.perform(post(url).sessionAttr("autenticado", true).param("nome", "X").param("preco", "5"))
           .andExpect(flash().attribute("erro", "Escolha uma categoria ou crie uma nova."));

        var igual = produtos.findById(p.getId()).orElseThrow();
        assertEquals("Bóton Editável", igual.getNome());
        assertEquals(0, new BigDecimal("6").compareTo(igual.getPreco()));
    }

    @Test
    @DisplayName("Tela de estoque tem o formulário de edição preenchido e a lista de categorias")
    void telaDeEstoqueComEdicao() throws Exception {
        mvc.perform(get("/estoque").sessionAttr("autenticado", true))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("/estoque/" + p.getId() + "/editar")))
           .andExpect(content().string(containsString("value=\"6,00\"")))
           .andExpect(content().string(containsString("/estoque/categorias/" + cat.getId() + "/renomear")));
    }

    // ---------- categoria ----------

    @Test
    @DisplayName("Renomear categoria funciona e recusa nome que já existe em outra (mesmo nome padronizado)")
    void renomearCategoria() throws Exception {
        var outra = categoriasRepo.save(new Categoria("Adesivo Repetido " + p.getId()));

        mvc.perform(post("/estoque/categorias/" + cat.getId() + "/renomear").sessionAttr("autenticado", true)
                        .param("nome", "  ADESIVO   repetido " + p.getId()))
           .andExpect(redirectedUrl("/estoque"))
           .andExpect(flash().attribute("erro", "Já existe a categoria \"" + outra.getNome() + "\". Escolha outro nome."));
        mvc.perform(post("/estoque/categorias/" + cat.getId() + "/renomear").sessionAttr("autenticado", true).param("nome", " "))
           .andExpect(flash().attribute("erro", "Nome de categoria vazio."));
        mvc.perform(post("/estoque/categorias/" + cat.getId() + "/renomear").sessionAttr("autenticado", true)
                        .param("nome", "botões grandes " + p.getId()))
           .andExpect(flash().attribute("sucesso", containsString("Botões Grandes " + p.getId())));

        assertEquals("Botões Grandes " + p.getId(), categorias.buscar(cat.getId()).getNome());
        // Renomear para o próprio nome (outra caixa) é permitido.
        categorias.renomear(cat.getId(), "BOTÕES GRANDES " + p.getId());
    }

    // ---------- promoção ----------

    @Test
    @DisplayName("Editar promoção troca preço e quantidades por categoria e mantém o tipo")
    void editarPromocao() throws Exception {
        var outra = categoriasRepo.save(new Categoria("Combo " + p.getId()));
        var promo = promocoesRepo.save(new Promocao(List.of(new PromocaoItem(cat, 2)), TipoPromocao.PACOTE, new BigDecimal("10")));

        mvc.perform(get("/descontos/" + promo.getId() + "/editar").sessionAttr("autenticado", true))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Editar promoção")))
           .andExpect(content().string(containsString("/descontos/" + promo.getId() + "/editar")))
           .andExpect(content().string(containsString("value=\"10,00\"")));
        mvc.perform(post("/descontos/" + promo.getId() + "/editar").sessionAttr("autenticado", true)
                        .param("preco", "12,50").param("qtdCat_" + cat.getId(), "3").param("qtdCat_" + outra.getId(), "1"))
           .andExpect(redirectedUrl("/descontos"))
           .andExpect(flash().attribute("sucesso", containsString("atualizada")));

        var editada = promocoes.buscar(promo.getId());
        assertEquals(0, new BigDecimal("12.50").compareTo(editada.getPreco()));
        assertEquals(Map.of(cat.getId(), 3, outra.getId(), 1), editada.getQuantidadePorCategoria());
        assertEquals(TipoPromocao.PACOTE, editada.getTipo());
    }

    @Test
    @DisplayName("Editar promoção valida preço maior que zero e ao menos uma categoria")
    void edicaoDePromocaoValida() throws Exception {
        var promo = promocoesRepo.save(new Promocao(List.of(new PromocaoItem(cat, 2)), TipoPromocao.A_PARTIR_DE, new BigDecimal("5")));

        mvc.perform(post("/descontos/" + promo.getId() + "/editar").sessionAttr("autenticado", true)
                        .param("preco", "0").param("qtdCat_" + cat.getId(), "2"))
           .andExpect(flash().attribute("erro", "Preço deve ser maior que zero."));
        assertThrows(RegraDeNegocioException.class, () -> promocoes.editar(promo.getId(), Map.of(), new BigDecimal("5")));

        assertEquals(0, new BigDecimal("5").compareTo(promocoes.buscar(promo.getId()).getPreco()));
    }

    @Test
    @DisplayName("Serviço de estoque: editar produto inexistente é regra de negócio com mensagem")
    void editarInexistente() {
        var erro = assertThrows(RegraDeNegocioException.class,
                () -> estoque.editar(999_999L, "Nome", BigDecimal.ONE, cat));
        assertEquals("Produto não encontrado: 999999", erro.getMessage());
    }
}
