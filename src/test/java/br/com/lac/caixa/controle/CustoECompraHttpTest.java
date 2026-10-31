package br.com.lac.caixa.controle;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.repositorio.CategoriaRepository;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import br.com.lac.caixa.repositorio.VendaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Custo no cadastro e na edição, entrada por compra e custo congelado na venda. */
@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:custo;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class CustoECompraHttpTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    ProdutoRepository produtos;

    @Autowired
    CategoriaRepository categorias;

    @Autowired
    VendaRepository vendas;

    @Autowired
    JdbcTemplate jdbc;

    Categoria cat;

    @BeforeEach
    void preparar() {
        cat = categorias.save(new Categoria("Custo " + UUID.randomUUID()));
    }

    Produto produto(String nome) {
        return produtos.findAll().stream()
                .filter(p -> p.getNome().equals(nome))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("Cadastro com custo, sem custo (selo no estoque) e custo negativo recusado")
    void cadastroComESemCusto() throws Exception {
        String base = "Cad " + UUID.randomUUID();
        mvc.perform(post("/estoque/novo")
                        .sessionAttr("autenticado", true)
                        .param("nome", base + " A")
                        .param("preco", "6")
                        .param("quantidade", "1")
                        .param("categoriaId", cat.getId().toString())
                        .param("custo", "2,50"))
                .andExpect(redirectedUrl("/estoque"));
        mvc.perform(post("/estoque/novo")
                        .sessionAttr("autenticado", true)
                        .param("nome", base + " B")
                        .param("preco", "6")
                        .param("quantidade", "1")
                        .param("categoriaId", cat.getId().toString()))
                .andExpect(redirectedUrl("/estoque"));
        mvc.perform(post("/estoque/novo")
                        .sessionAttr("autenticado", true)
                        .param("nome", base + " C")
                        .param("preco", "6")
                        .param("quantidade", "1")
                        .param("categoriaId", cat.getId().toString())
                        .param("custo", "-1"))
                .andExpect(flash().attribute("erro", "O custo não pode ser negativo."));

        assertEquals(0, new BigDecimal("2.50").compareTo(produto(base + " A").getCustoUnitario()));
        assertNull(produto(base + " B").getCustoUnitario());
        mvc.perform(get("/estoque").sessionAttr("autenticado", true))
                .andExpect(content().string(containsString("sem custo")))
                .andExpect(content().string(containsString("Entrada de estoque (compra)")));
    }

    @Test
    @DisplayName("Edição: custo vazio mantém o atual (4 casas), novo valor troca, \"sem custo\" apaga")
    void edicaoDoCusto() throws Exception {
        var p = new Produto("Editar Custo " + UUID.randomUUID(), new BigDecimal("6"), 3);
        p.setCategoria(cat);
        p.setCustoUnitario(new BigDecimal("2.3333"));
        p = produtos.save(p);
        String url = "/estoque/" + p.getId() + "/editar";

        mvc.perform(post(url)
                .sessionAttr("autenticado", true)
                .param("nome", p.getNome())
                .param("preco", "6")
                .param("categoriaId", cat.getId().toString())
                .param("custo", ""));
        assertEquals(
                new BigDecimal("2.3333"),
                produtos.findById(p.getId()).orElseThrow().getCustoUnitario());
        mvc.perform(post(url)
                .sessionAttr("autenticado", true)
                .param("nome", p.getNome())
                .param("preco", "6")
                .param("categoriaId", cat.getId().toString())
                .param("custo", "3,10"));
        assertEquals(
                new BigDecimal("3.1000"),
                produtos.findById(p.getId()).orElseThrow().getCustoUnitario());
        mvc.perform(post(url)
                .sessionAttr("autenticado", true)
                .param("nome", p.getNome())
                .param("preco", "6")
                .param("categoriaId", cat.getId().toString())
                .param("removerCusto", "true"));
        assertNull(produtos.findById(p.getId()).orElseThrow().getCustoUnitario());
    }

    @Test
    @DisplayName("Entrada de estoque: soma ao estoque, recalcula o custo médio e guarda a compra")
    void entradaDeEstoque() throws Exception {
        var p = new Produto("Compra " + UUID.randomUUID(), new BigDecimal("6"), 10);
        p.setCustoUnitario(new BigDecimal("2"));
        p = produtos.save(p);

        mvc.perform(post("/estoque/compra")
                        .sessionAttr("autenticado", true)
                        .param("produtoId", p.getId().toString())
                        .param("quantidade", "5")
                        .param("custoTotal", "15,00")
                        .param("data", LocalDate.now().toString())
                        .param("observacao", "Nota 123"))
                .andExpect(redirectedUrl("/estoque"))
                .andExpect(flash().attribute("sucesso", containsString("Custo médio: R$ 2,33")));

        var depois = produtos.findById(p.getId()).orElseThrow();
        assertEquals(15, depois.getQuantidade());
        assertEquals(new BigDecimal("2.3333"), depois.getCustoUnitario());
        assertEquals(
                "Nota 123",
                jdbc.queryForObject(
                        "select observacao from compra_estoque where produto_id = ?", String.class, p.getId()));
        assertEquals(
                0,
                new BigDecimal("15.00")
                        .compareTo(jdbc.queryForObject(
                                "select custo_total from compra_estoque where produto_id = ?",
                                BigDecimal.class,
                                p.getId())));
    }

    @Test
    @DisplayName("Entrada de estoque recusa data futura, custo vazio e quantidade zero, sem mexer no estoque")
    void entradaDeEstoqueValida() throws Exception {
        var p = produtos.save(new Produto("Compra Inválida " + UUID.randomUUID(), new BigDecimal("6"), 2));
        String id = p.getId().toString();

        mvc.perform(post("/estoque/compra")
                        .sessionAttr("autenticado", true)
                        .param("produtoId", id)
                        .param("quantidade", "1")
                        .param("custoTotal", "1")
                        .param("data", LocalDate.now().plusDays(1).toString()))
                .andExpect(flash().attribute("erro", "A data da compra não pode ser futura."));
        mvc.perform(post("/estoque/compra")
                        .sessionAttr("autenticado", true)
                        .param("produtoId", id)
                        .param("quantidade", "1")
                        .param("custoTotal", ""))
                .andExpect(flash().attribute("erro", "Informe o custo total da compra."));
        mvc.perform(post("/estoque/compra")
                        .sessionAttr("autenticado", true)
                        .param("produtoId", id)
                        .param("quantidade", "0")
                        .param("custoTotal", "1"))
                .andExpect(flash().attribute("erro", "Quantidade deve ser maior que zero."));

        assertEquals(2, produtos.findById(p.getId()).orElseThrow().getQuantidade());
    }

    @Test
    @DisplayName("A venda congela o custo do produto no item; mudar o custo depois não mexe na venda")
    void vendaCongelaCusto() throws Exception {
        var p = new Produto("Venda Custo " + UUID.randomUUID(), new BigDecimal("6"), 10);
        p.setCustoUnitario(new BigDecimal("2.5"));
        p = produtos.save(p);
        String token = UUID.randomUUID().toString();

        mvc.perform(post("/venda")
                        .sessionAttr("autenticado", true)
                        .param("token", token)
                        .param("qtd_" + p.getId(), "2")
                        .param("pag_forma_0", "PIX"))
                .andExpect(redirectedUrl("/historico"));
        var atual = produtos.findById(p.getId()).orElseThrow();
        atual.setCustoUnitario(new BigDecimal("9"));
        produtos.save(atual);

        long venda = vendas.findByToken(token).orElseThrow().getId();
        assertEquals(
                0,
                new BigDecimal("2.5")
                        .compareTo(jdbc.queryForObject(
                                "select custo_unitario from item_venda where venda_id = ?", BigDecimal.class, venda)));
    }
}
