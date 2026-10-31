package br.com.lac.caixa.controle;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.ItemVenda;
import br.com.lac.caixa.modelo.PagamentoVenda;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.modelo.PromocaoItem;
import br.com.lac.caixa.modelo.TipoPromocao;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.repositorio.CategoriaRepository;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import br.com.lac.caixa.repositorio.PromocaoRepository;
import br.com.lac.caixa.repositorio.VendaRepository;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.RollbackException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Registro de venda pela tela: valor cobrado, estoque, envio duplicado e prévia. */
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
    EntityManagerFactory emf;

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

    MockHttpServletRequestBuilder venda(String token, String... campos) {
        var req = post("/venda").sessionAttr("autenticado", true).param("token", token);
        for (int i = 0; i < campos.length; i += 2) req.param(campos[i], campos[i + 1]);
        return req;
    }

    static String novoToken() {
        return UUID.randomUUID().toString();
    }

    int estoque(Produto produto) {
        return produtos.findById(produto.getId()).orElseThrow().getQuantidade();
    }

    static PagamentoVenda pix(String valor) {
        return new PagamentoVenda(FormaPagamento.PIX, new BigDecimal(valor), null, null, null, null);
    }

    @Test
    @DisplayName("Valor diferente do calculado sem motivo é recusado, sem venda nem baixa")
    void adulteracaoSemMotivoRecusada() throws Exception {
        long antes = vendas.count();

        mvc.perform(venda(novoToken(), "qtd_" + p.getId(), "3", "pag_forma_0", "PIX", "valorTotal", "1"))
                .andExpect(status().isConflict())
                .andExpect(model().attributeExists("erro"));
        assertEquals(antes, vendas.count());
        assertEquals(10, estoque(p));
    }

    @Test
    @DisplayName("Ajuste com motivo grava o valor calculado e o cobrado")
    void ajusteComMotivoGuardaDoisValores() throws Exception {
        String token = novoToken();

        mvc.perform(venda(
                        token,
                        "qtd_" + p.getId(),
                        "3",
                        "pag_forma_0",
                        "PIX",
                        "valorTotal",
                        "1.234,56",
                        "motivoAjuste",
                        "Combinado"))
                .andExpect(redirectedUrl("/historico"));

        var v = vendas.findByToken(token).orElseThrow();
        assertEquals(0, new BigDecimal("18").compareTo(v.getValorCalculado()));
        assertEquals(0, new BigDecimal("1234.56").compareTo(v.getValorCobrado()));
        assertEquals("Combinado", v.getMotivoAjuste());
    }

    @Test
    @DisplayName("Sem ajuste, o valor cobrado é o total calculado pelo servidor")
    void semAjusteGravaTotalCalculado() throws Exception {
        String token = novoToken();

        mvc.perform(venda(token, "qtd_" + p.getId(), "3", "pag_forma_0", "PIX")).andExpect(redirectedUrl("/historico"));

        var v = vendas.findByToken(token).orElseThrow();
        assertEquals(v.getValorCalculado(), v.getValorCobrado());
        assertEquals(0, new BigDecimal("18").compareTo(v.getValorCobrado()));
    }

    @Test
    @DisplayName("Valor ajustado negativo é recusado")
    void valorNegativoRecusado() throws Exception {
        mvc.perform(venda(
                        novoToken(),
                        "qtd_" + p.getId(),
                        "1",
                        "pag_forma_0",
                        "PIX",
                        "valorTotal",
                        "-1",
                        "motivoAjuste",
                        "Teste"))
                .andExpect(status().isConflict())
                .andExpect(model().attributeExists("erro"));
    }

    @Test
    @DisplayName("Um item sem estoque desfaz a venda inteira, sem baixar os outros")
    void itemSemEstoqueDesfazAVendaInteira() throws Exception {
        var pouco = produtos.save(new Produto("Pouco", new BigDecimal("3"), 1));
        long antes = vendas.count();

        mvc.perform(venda(novoToken(), "qtd_" + p.getId(), "4", "qtd_" + pouco.getId(), "2", "pag_forma_0", "PIX"))
                .andExpect(status().isConflict())
                .andExpect(model().attribute("erro", containsString("Estoque insuficiente de Pouco")));
        assertEquals(antes, vendas.count());
        assertEquals(10, estoque(p));
        assertEquals(1, estoque(pouco));
    }

    @Test
    @DisplayName("Quantidade não numérica vira mensagem clara")
    void quantidadeNaoNumericaViraMensagem() throws Exception {
        mvc.perform(venda(novoToken(), "qtd_" + p.getId(), "abc", "pag_forma_0", "PIX"))
                .andExpect(status().isConflict())
                .andExpect(model().attribute("erro", "Quantidade inválida: abc"));
    }

    @Test
    @DisplayName("O mesmo token enviado duas vezes cria uma venda só e baixa o estoque uma vez")
    void tokenRepetidoNaoDuplicaVendaNemBaixa() throws Exception {
        String token = novoToken();
        long antes = vendas.count();

        for (int i = 0; i < 2; i++) {
            mvc.perform(venda(token, "qtd_" + p.getId(), "2", "pag_forma_0", "PIX"))
                    .andExpect(redirectedUrl("/historico"));
        }

        assertEquals(antes + 1, vendas.count());
        assertEquals(8, estoque(p));
    }

    @Test
    @DisplayName("@Version barra a segunda baixa concorrente do mesmo produto")
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

            assertThrows(RollbackException.class, () -> b.getTransaction().commit());
            assertEquals(3, estoque(p));
        } finally {
            if (a.getTransaction().isActive()) a.getTransaction().rollback();
            if (b.getTransaction().isActive()) b.getTransaction().rollback();
            a.close();
            b.close();
        }
    }

    @Test
    @DisplayName("A prévia é calculada no servidor")
    void previaCalculadaNoServidor() throws Exception {
        mvc.perform(post("/venda/previa").sessionAttr("autenticado", true).param("qtd_" + p.getId(), "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(18));
    }

    @Test
    @DisplayName("Prévia inválida responde 400 com o erro em JSON")
    void previaInvalidaRetornaErroJson() throws Exception {
        mvc.perform(post("/venda/previa").sessionAttr("autenticado", true).param("qtd_" + p.getId(), "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    @DisplayName("Tela de venda tem o token e o histórico mostra o valor cobrado")
    void telasRenderizamEHistoricoExibeValorLegado() throws Exception {
        var antiga = Venda.nova(LocalDateTime.now());
        antiga.registrarValores(new BigDecimal("9.50"), BigDecimal.ZERO, new BigDecimal("9.50"), null);
        antiga.registrarPagamentos(List.of(pix("9.50")));
        vendas.save(antiga);

        mvc.perform(get("/venda/nova").sessionAttr("autenticado", true))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"token\"")));
        mvc.perform(get("/historico").sessionAttr("autenticado", true))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("9,50")));
    }

    @Test
    @DisplayName("Telas com promoções carregam com open-in-view desligado")
    void telasCarregamPromocoesComOsivDesligado() throws Exception {
        var cat = categorias.save(new Categoria("Categoria HTTP"));
        promocoes.save(new Promocao(List.of(new PromocaoItem(cat, 2)), TipoPromocao.A_PARTIR_DE, new BigDecimal("5")));

        mvc.perform(get("/descontos").sessionAttr("autenticado", true)).andExpect(status().isOk());
        mvc.perform(get("/estoque").sessionAttr("autenticado", true)).andExpect(status().isOk());
        mvc.perform(get("/historico")
                        .sessionAttr("autenticado", true)
                        .param("inicio", "2020-01-01")
                        .param("fim", "2040-12-31"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Cancelamento por HTTP risca a venda; fechamento e CSV do fechamento respondem")
    void fechamentoECancelamentoFuncionamPorHttp() throws Exception {
        var v = Venda.nova(LocalDateTime.now());
        v.adicionarItem(new ItemVenda(p, 1));
        v.registrarValores(new BigDecimal("6"), BigDecimal.ZERO, new BigDecimal("6"), null);
        v.registrarPagamentos(List.of(pix("6")));
        vendas.saveAndFlush(v);

        mvc.perform(post("/historico/" + v.getId() + "/cancelar")
                        .sessionAttr("autenticado", true)
                        .param("motivo", "Desistência"))
                .andExpect(redirectedUrl("/historico"));

        assertTrue(vendas.findById(v.getId()).orElseThrow().isCancelada());
        mvc.perform(get("/historico").sessionAttr("autenticado", true))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("venda-cancelada")));
        mvc.perform(get("/fechamento").sessionAttr("autenticado", true)).andExpect(status().isOk());
        mvc.perform(get("/fechamento/exportar.csv").sessionAttr("autenticado", true))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"fechamento.csv\""));
    }
}
