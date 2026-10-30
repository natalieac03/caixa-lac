package br.com.lac.caixa.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.ItemVenda;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.modelo.PromocaoItem;
import br.com.lac.caixa.modelo.TipoPromocao;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.repositorio.CategoriaRepository;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import br.com.lac.caixa.repositorio.PromocaoRepository;
import br.com.lac.caixa.repositorio.VendaRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        properties = {
            "spring.profiles.active=test",
            "spring.datasource.url=jdbc:h2:mem:vendas;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=validate"
        })
@AutoConfigureMockMvc
class VendaHttpTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    jakarta.persistence.EntityManagerFactory emf;

    @Autowired
    ProdutoRepository produtos;

    @Autowired
    VendaRepository vendas;

    @Autowired
    PromocaoRepository promocoes;

    @Autowired
    CategoriaRepository categorias;

    Produto p;

    @BeforeEach
    void preparar() {
        p = produtos.save(new Produto("Teste", new BigDecimal("6"), 10));
    }

    @Test
    void adulteracaoSemMotivoRecusada() throws Exception {
        long antes = vendas.count();
        mvc.perform(post("/venda")
                        .param("token", java.util.UUID.randomUUID().toString())
                        .sessionAttr("autenticado", true)
                        .param("qtd_" + p.getId(), "3")
                        .param("pag_forma_0", "PIX")
                        .param("valorTotal", "1"))
                .andExpect(redirectedUrl("/venda/nova"))
                .andExpect(flash().attributeExists("erro"));
        assertEquals(antes, vendas.count());
        assertEquals(10, produtos.findById(p.getId()).orElseThrow().getQuantidade());
    }

    @Test
    void ajusteComMotivoGuardaDoisValores() throws Exception {
        String token = java.util.UUID.randomUUID().toString();
        mvc.perform(post("/venda")
                        .param("token", token)
                        .sessionAttr("autenticado", true)
                        .param("qtd_" + p.getId(), "3")
                        .param("pag_forma_0", "PIX")
                        .param("valorTotal", "1.234,56")
                        .param("motivoAjuste", "Combinado"))
                .andExpect(redirectedUrl("/historico"));
        var v = vendas.findByToken(token).orElseThrow();
        assertEquals(0, new BigDecimal("18").compareTo(v.getValorCalculado()));
        assertEquals(0, new BigDecimal("1234.56").compareTo(v.getValorCobrado()));
        assertEquals("Combinado", v.getMotivoAjuste());
    }

    @Test
    void itemSemEstoqueDesfazAVendaInteira() throws Exception {
        var pouco = produtos.save(new Produto("Pouco", new BigDecimal("3"), 1));
        long antes = vendas.count();
        mvc.perform(post("/venda")
                        .sessionAttr("autenticado", true)
                        .param("token", java.util.UUID.randomUUID().toString())
                        .param("qtd_" + p.getId(), "4")
                        .param("qtd_" + pouco.getId(), "2")
                        .param("pag_forma_0", "PIX"))
                .andExpect(redirectedUrl("/venda/nova"))
                .andExpect(flash().attribute(
                                "erro", org.hamcrest.Matchers.containsString("Estoque insuficiente de Pouco")));
        assertEquals(antes, vendas.count());
        assertEquals(10, produtos.findById(p.getId()).orElseThrow().getQuantidade());
        assertEquals(1, produtos.findById(pouco.getId()).orElseThrow().getQuantidade());
    }

    @Test
    void quantidadeNaoNumericaViraMensagem() throws Exception {
        mvc.perform(post("/venda")
                        .sessionAttr("autenticado", true)
                        .param("token", java.util.UUID.randomUUID().toString())
                        .param("qtd_" + p.getId(), "abc")
                        .param("pag_forma_0", "PIX"))
                .andExpect(redirectedUrl("/venda/nova"))
                .andExpect(flash().attribute("erro", "Quantidade inválida: abc"));
    }

    @Test
    void previaCalculadaNoServidor() throws Exception {
        mvc.perform(post("/venda/previa").sessionAttr("autenticado", true).param("qtd_" + p.getId(), "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(18));
    }

    @Test
    void valorNegativoRecusado() throws Exception {
        mvc.perform(post("/venda")
                        .param("token", java.util.UUID.randomUUID().toString())
                        .sessionAttr("autenticado", true)
                        .param("qtd_" + p.getId(), "1")
                        .param("pag_forma_0", "PIX")
                        .param("valorTotal", "-1")
                        .param("motivoAjuste", "Teste"))
                .andExpect(redirectedUrl("/venda/nova"))
                .andExpect(flash().attributeExists("erro"));
    }

    @Test
    void vendaAntigaUsaTotalLegado() {
        var v = new Venda();
        v.setValorTotal(new BigDecimal("9.50"));
        assertEquals(new BigDecimal("9.50"), v.getValorCobrado());
    }

    @Test
    void tokenRepetidoNaoDuplicaVendaNemBaixa() throws Exception {
        String token = java.util.UUID.randomUUID().toString();
        long antes = vendas.count();
        for (int i = 0; i < 2; i++)
            mvc.perform(post("/venda")
                            .sessionAttr("autenticado", true)
                            .param("token", token)
                            .param("qtd_" + p.getId(), "2")
                            .param("pag_forma_0", "PIX"))
                    .andExpect(redirectedUrl("/historico"));
        assertEquals(antes + 1, vendas.count());
        assertEquals(8, produtos.findById(p.getId()).orElseThrow().getQuantidade());
    }

    @Test
    void versaoImpedeDuasBaixasConcorrentes() {
        var a = emf.createEntityManager();
        var b = emf.createEntityManager();
        try {
            a.getTransaction().begin();
            b.getTransaction().begin();
            var primeiro = a.find(Produto.class, p.getId());
            var segundo = b.find(Produto.class, p.getId());
            primeiro.baixar(7);
            segundo.baixar(7);
            a.getTransaction().commit();
            assertThrows(
                    jakarta.persistence.RollbackException.class,
                    () -> b.getTransaction().commit());
            assertEquals(3, produtos.findById(p.getId()).orElseThrow().getQuantidade());
        } finally {
            if (a.getTransaction().isActive()) a.getTransaction().rollback();
            if (b.getTransaction().isActive()) b.getTransaction().rollback();
            a.close();
            b.close();
        }
    }

    @Test
    void previaInvalidaRetornaErroJson() throws Exception {
        mvc.perform(post("/venda/previa").sessionAttr("autenticado", true).param("qtd_" + p.getId(), "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void telasRenderizamEHistoricoExibeValorLegado() throws Exception {
        var antiga = Venda.nova();
        antiga.setValorTotal(new BigDecimal("9.50"));
        antiga.registrarPagamentos(java.util.List.of(new br.com.lac.caixa.modelo.PagamentoVenda(
                br.com.lac.caixa.modelo.FormaPagamento.PIX, new BigDecimal("9.50"), null, null)));
        vendas.save(antiga);
        mvc.perform(get("/venda/nova").sessionAttr("autenticado", true))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"token\"")));
        mvc.perform(get("/historico").sessionAttr("autenticado", true))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("9,50")));
    }

    @Test
    void semAjusteGravaTotalCalculado() throws Exception {
        String token = java.util.UUID.randomUUID().toString();
        mvc.perform(post("/venda")
                        .sessionAttr("autenticado", true)
                        .param("token", token)
                        .param("qtd_" + p.getId(), "3")
                        .param("pag_forma_0", "PIX"))
                .andExpect(redirectedUrl("/historico"));
        var v = vendas.findByToken(token).orElseThrow();
        assertEquals(v.getValorCalculado(), v.getValorCobrado());
        assertEquals(0, new BigDecimal("18").compareTo(v.getValorCobrado()));
    }

    @Test
    void telasCarregamPromocoesComOsivDesligado() throws Exception {
        var cat = categorias.save(new Categoria("Categoria HTTP"));
        promocoes.save(new Promocao(
                java.util.List.of(new PromocaoItem(cat, 2)), TipoPromocao.A_PARTIR_DE, new BigDecimal("5")));
        mvc.perform(get("/descontos").sessionAttr("autenticado", true)).andExpect(status().isOk());
        mvc.perform(get("/estoque").sessionAttr("autenticado", true)).andExpect(status().isOk());
        mvc.perform(get("/historico")
                        .sessionAttr("autenticado", true)
                        .param("inicio", "2020-01-01")
                        .param("fim", "2040-12-31"))
                .andExpect(status().isOk());
    }

    @Test
    void fechamentoECancelamentoFuncionamPorHttp() throws Exception {
        var v = Venda.nova();
        v.adicionarItem(new ItemVenda(p, 1));
        v.registrarValores(new BigDecimal("6"), java.math.BigDecimal.ZERO, new BigDecimal("6"), null);
        v.registrarPagamentos(java.util.List.of(new br.com.lac.caixa.modelo.PagamentoVenda(
                br.com.lac.caixa.modelo.FormaPagamento.PIX, new BigDecimal("6"), null, null)));
        vendas.saveAndFlush(v);
        mvc.perform(post("/historico/" + v.getId() + "/cancelar")
                        .sessionAttr("autenticado", true)
                        .param("motivo", "Desistência"))
                .andExpect(redirectedUrl("/historico"));
        assertTrue(vendas.findById(v.getId()).orElseThrow().isCancelada());
        mvc.perform(get("/historico").sessionAttr("autenticado", true))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("venda-cancelada")));
        mvc.perform(get("/fechamento").sessionAttr("autenticado", true)).andExpect(status().isOk());
        mvc.perform(get("/fechamento/exportar.csv").sessionAttr("autenticado", true))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"fechamento.csv\""));
    }
}
