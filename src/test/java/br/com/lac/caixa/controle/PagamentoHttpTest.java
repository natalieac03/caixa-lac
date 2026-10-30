package br.com.lac.caixa.controle;

import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import br.com.lac.caixa.repositorio.VendaRepository;
import br.com.lac.caixa.servico.FechamentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Pagamento dividido de ponta a ponta: POST /venda, histórico, CSV e fechamento. */
@SpringBootTest(properties={"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:pagamentos;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class PagamentoHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ProdutoRepository produtos;
    @Autowired VendaRepository vendas;
    @Autowired FechamentoService fechamento;
    @Autowired JdbcTemplate jdbc;
    Produto p;

    /** Produto de R$ 6,00: 3 unidades = R$ 18,00. */
    @BeforeEach void preparar() { p = produtos.save(new Produto("Bóton Pagamento", new BigDecimal("6"), 100)); }

    MockHttpServletRequestBuilder venda(String token, String... params) {
        var req = post("/venda").sessionAttr("autenticado", true).param("token", token).param("qtd_" + p.getId(), "3");
        for (int i = 0; i < params.length; i += 2) req.param(params[i], params[i + 1]);
        return req;
    }

    @Test void vendaDivididaPixEDinheiroComTroco() throws Exception {
        String token = UUID.randomUUID().toString();
        mvc.perform(venda(token, "pag_forma_0", "PIX", "pag_valor_0", "10,00",
                        "pag_forma_1", "DINHEIRO", "pag_valor_1", "8,00", "pag_recebido_1", "10,00"))
           .andExpect(redirectedUrl("/historico"))
           .andExpect(flash().attribute("sucesso", containsString("Recebido R$ 10,00 via Pix + R$ 8,00 em dinheiro (troco R$ 2,00).")));
        var v = vendas.findByToken(token).orElseThrow();
        long id = v.getId();
        assertEquals(2, jdbc.queryForObject("select count(*) from pagamento_venda where venda_id = ?", Integer.class, id));
        assertEquals(0, new BigDecimal("10").compareTo(jdbc.queryForObject(
                "select valor_recebido from pagamento_venda where venda_id = ? and forma = 'DINHEIRO'", BigDecimal.class, id)));

        mvc.perform(get("/historico").sessionAttr("autenticado", true))
           .andExpect(content().string(containsString("data-pagamento=\"Pix R$ 10,00 + Dinheiro R$ 8,00 (troco R$ 2,00)\"")))
           .andExpect(content().string(containsString(">Dinheiro R$ 8,00 (troco R$ 2,00)<")));
        // Filtro por forma: a venda aparece buscando por QUALQUER uma das formas.
        for (String forma : new String[]{"PIX", "DINHEIRO"}) {
            mvc.perform(get("/historico").param("forma", forma).sessionAttr("autenticado", true))
               .andExpect(content().string(containsString("Pix R$ 10,00 + Dinheiro R$ 8,00")));
        }
        mvc.perform(get("/historico").param("forma", "CREDITO").sessionAttr("autenticado", true))
           .andExpect(content().string(not(containsString("Pix R$ 10,00 + Dinheiro R$ 8,00"))));

        String csv = new String(mvc.perform(get("/historico/exportar.csv").param("forma", "DINHEIRO").sessionAttr("autenticado", true))
                .andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(csv.contains("Valor total;Pix;Débito;Crédito;Dinheiro;Outro;Descrição do Outro"), csv);
        assertTrue(csv.contains(id + ";"), csv);
        assertTrue(csv.contains(";18,00;10,00;0,00;0,00;8,00;0,00;\"\""), csv);
    }

    @Test void somaErradaERecusadaSemBaixarEstoque() throws Exception {
        long antes = vendas.count();
        mvc.perform(venda(UUID.randomUUID().toString(), "pag_forma_0", "PIX", "pag_valor_0", "10",
                        "pag_forma_1", "DINHEIRO", "pag_valor_1", "7"))
           .andExpect(redirectedUrl("/venda/nova"))
           .andExpect(flash().attribute("erro", containsString("falta R$ 1,00")));
        mvc.perform(venda(UUID.randomUUID().toString(), "pag_forma_0", "PIX", "pag_valor_0", "10",
                        "pag_forma_1", "PIX", "pag_valor_1", "9"))
           .andExpect(flash().attribute("erro", containsString("sobra R$ 1,00")));
        mvc.perform(venda(UUID.randomUUID().toString(), "pag_forma_0", "PIX", "pag_valor_0", "18", "pag_forma_1", "DINHEIRO"))
           .andExpect(flash().attribute("erro", "Informe o valor de cada parte do pagamento."));
        assertEquals(antes, vendas.count());
        assertEquals(100, produtos.findById(p.getId()).orElseThrow().getQuantidade());
    }

    @Test void outroSemDescricaoERecebidoMenorSaoRecusados() throws Exception {
        mvc.perform(venda(UUID.randomUUID().toString(), "pag_forma_0", "OUTRO"))
           .andExpect(redirectedUrl("/venda/nova")).andExpect(flash().attribute("erro", containsString("Outro precisa de descrição")));
        mvc.perform(venda(UUID.randomUUID().toString(), "pag_forma_0", "PIX", "pag_valor_0", "9",
                        "pag_forma_1", "OUTRO", "pag_valor_1", "9", "pag_descricao_1", " "))
           .andExpect(flash().attribute("erro", containsString("Outro precisa de descrição")));
        mvc.perform(venda(UUID.randomUUID().toString(), "pag_forma_0", "DINHEIRO", "pag_recebido_0", "15"))
           .andExpect(flash().attribute("erro", containsString("menor que o valor em dinheiro")));
        mvc.perform(venda(UUID.randomUUID().toString(), "pag_forma_0", "PIX", "pag_recebido_0", "20"))
           .andExpect(flash().attribute("erro", "Valor recebido só vale para pagamento em dinheiro."));
        mvc.perform(venda(UUID.randomUUID().toString(), "pag_forma_0", "CHEQUE"))
           .andExpect(flash().attribute("erro", "Forma de pagamento inválida: CHEQUE"));
        mvc.perform(venda(UUID.randomUUID().toString()))
           .andExpect(flash().attribute("erro", "Escolha a forma de pagamento."));
        assertEquals(100, produtos.findById(p.getId()).orElseThrow().getQuantidade());
    }

    @Test void umToqueComOutroETrocoNoDinheiro() throws Exception {
        mvc.perform(venda(UUID.randomUUID().toString(), "pag_forma_0", "OUTRO", "pag_descricao_0", "Vale"))
           .andExpect(flash().attribute("sucesso", containsString("Recebido R$ 18,00 via Vale.")));
        mvc.perform(venda(UUID.randomUUID().toString(), "pag_forma_0", "DINHEIRO", "pag_recebido_0", "20"))
           .andExpect(flash().attribute("sucesso", containsString("Recebido R$ 18,00 em dinheiro (troco R$ 2,00).")));
    }

    @Test void cortesiaSemPagamentoEComMotivoObrigatorio() throws Exception {
        mvc.perform(venda(UUID.randomUUID().toString(), "pag_forma_0", "PIX", "valorTotal", "0"))
           .andExpect(redirectedUrl("/venda/nova")).andExpect(flash().attribute("erro", "Informe o motivo do ajuste de valor."));
        String token = UUID.randomUUID().toString();
        mvc.perform(venda(token, "pag_forma_0", "PIX", "valorTotal", "0", "motivoAjuste", "Brinde do evento"))
           .andExpect(flash().attribute("sucesso", containsString("Cortesia: nada a receber.")));
        long id = vendas.findByToken(token).orElseThrow().getId();
        assertEquals(0, jdbc.queryForObject("select count(*) from pagamento_venda where venda_id = ?", Integer.class, id));
        mvc.perform(venda(UUID.randomUUID().toString(), "pag_forma_0", "PIX", "pag_valor_0", "1",
                        "valorTotal", "0", "motivoAjuste", "Brinde"))
           .andExpect(flash().attribute("erro", "Cortesia (valor zero) não tem pagamento."));
    }

    @Test void fechamentoMostraAlertaSeAsFormasNaoBatem() throws Exception {
        String token = UUID.randomUUID().toString();
        mvc.perform(venda(token, "pag_forma_0", "PIX", "pag_valor_0", "10", "pag_forma_1", "DINHEIRO", "pag_valor_1", "8"));
        long id = vendas.findByToken(token).orElseThrow().getId();
        String hoje = LocalDate.now().toString();
        mvc.perform(get("/fechamento").param("inicio", hoje).param("fim", hoje).sessionAttr("autenticado", true))
           .andExpect(content().string(not(containsString("id=\"alerta-fechamento\""))))
           .andExpect(content().string(containsString("Vendas com pagamento dividido")));
        assertTrue(fechamento.consultar(LocalDate.now(), LocalDate.now()).isConfere());
        // Simula dado inconsistente direto no banco: a tela tem que avisar.
        jdbc.update("update pagamento_venda set valor = 9 where venda_id = ? and forma = 'DINHEIRO'", id);
        try {
            mvc.perform(get("/fechamento").param("inicio", hoje).param("fim", hoje).sessionAttr("autenticado", true))
               .andExpect(content().string(containsString("id=\"alerta-fechamento\"")));
        } finally {
            jdbc.update("update pagamento_venda set valor = 8 where venda_id = ? and forma = 'DINHEIRO'", id);
        }
    }
}
