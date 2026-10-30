package br.com.lac.caixa.controle;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import br.com.lac.caixa.repositorio.VendaRepository;
import br.com.lac.caixa.servico.LimpezaDePix;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Sem MP_ACCESS_TOKEN: Pix automático desligado e o caixa segue com Pix manual. */
@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:pix_desligado;DB_CLOSE_DELAY=-1"
        })
@AutoConfigureMockMvc
class PixDesligadoHttpTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    ProdutoRepository produtos;

    @Autowired
    VendaRepository vendas;

    @Autowired
    LimpezaDePix limpeza;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("Tela de venda sem \"Gerar Pix\" (Pix automático desligado)")
    void telaSemPixAutomatico() throws Exception {
        mvc.perform(get("/venda/nova").sessionAttr("autenticado", true))
                .andExpect(content().string(containsString("data-pix-automatico=\"false\"")));
    }

    @Test
    @DisplayName("Gerar Pix é recusado com aviso para usar Pix manual")
    void gerarPixRecusado() throws Exception {
        mvc.perform(post("/venda/pix")
                        .sessionAttr("autenticado", true)
                        .accept(MediaType.APPLICATION_JSON)
                        .param("token", UUID.randomUUID().toString())
                        .param("parte", "0")
                        .param("valor", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Pix automático desligado. Use Pix manual."));
    }

    @Test
    @DisplayName("Venda com Pix manual funciona como antes; Pix \"do Mercado Pago\" sem cobrança é recusado")
    void vendaComPixManual() throws Exception {
        // Arrange
        var p = produtos.save(new Produto("Bóton Desligado", new BigDecimal("6"), 10));
        String token = UUID.randomUUID().toString();

        // Act / Assert: Pix manual (como sempre foi)
        mvc.perform(post("/venda")
                        .sessionAttr("autenticado", true)
                        .param("token", token)
                        .param("qtd_" + p.getId(), "1")
                        .param("pag_forma_0", "PIX"))
                .andExpect(redirectedUrl("/historico"));
        long id = vendas.findByToken(token).orElseThrow().getId();
        assertEquals(
                "MANUAL",
                jdbc.queryForObject("select origem from pagamento_venda where venda_id = ?", String.class, id));

        // Act / Assert: quem tentar dizer que é do Mercado Pago é recusado
        mvc.perform(post("/venda")
                        .sessionAttr("autenticado", true)
                        .param("token", UUID.randomUUID().toString())
                        .param("qtd_" + p.getId(), "1")
                        .param("pag_forma_0", "PIX")
                        .param("pag_origem_0", "MERCADO_PAGO"))
                .andExpect(redirectedUrl("/venda/nova"))
                .andExpect(flash().attribute("erro", containsString("Não há Pix do Mercado Pago gerado")));
    }

    @Test
    @DisplayName("Webhook sem MP_WEBHOOK_SECRET configurado: 401 (não dá para conferir a assinatura)")
    void webhookSemSegredo() throws Exception {
        mvc.perform(post("/webhooks/mercadopago")
                        .param("data.id", "ORD1")
                        .header("x-signature", "ts=1,v1=abc")
                        .header("x-request-id", "r"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("A limpeza agendada não faz nada com o recurso desligado")
    void limpezaDesligada() {
        limpeza.limpar();
    }
}
