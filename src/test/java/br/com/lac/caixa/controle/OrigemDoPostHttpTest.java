package br.com.lac.caixa.controle;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** POST vindo de outro site é recusado com 403. */
@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:origem_post;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class OrigemDoPostHttpTest {

    @Autowired
    MockMvc mvc;

    MockHttpServletRequestBuilder cancelar() {
        return post("/historico/999999/cancelar")
                .sessionAttr("autenticado", true)
                .param("motivo", "Teste");
    }

    @Test
    @DisplayName("Sec-Fetch-Site cross-site: 403")
    void crossSite() throws Exception {
        mvc.perform(cancelar().header("Sec-Fetch-Site", "cross-site")).andExpect(status().isForbidden());
        mvc.perform(post("/login").param("senha", "x").header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/logout").header("Sec-Fetch-Site", "cross-site")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Origin de outro host (ou \"null\"): 403")
    void origemDeOutroHost() throws Exception {
        mvc.perform(cancelar().header("Origin", "https://malicioso.example")).andExpect(status().isForbidden());
        mvc.perform(cancelar().header("Origin", "http://localhost.malicioso.example"))
                .andExpect(status().isForbidden());
        mvc.perform(cancelar().header("Origin", "null")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Mesmo site, sem cabeçalhos (navegador antigo, curl) e GET de fora: passam")
    void doProprioSite() throws Exception {
        mvc.perform(cancelar().header("Sec-Fetch-Site", "same-origin").header("Origin", "http://localhost"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(cancelar().header("Origin", "http://LOCALHOST:8080")).andExpect(status().is3xxRedirection());
        mvc.perform(cancelar().header("Sec-Fetch-Site", "none")).andExpect(status().is3xxRedirection());
        mvc.perform(cancelar()).andExpect(status().is3xxRedirection());
        mvc.perform(get("/historico").sessionAttr("autenticado", true).header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("O webhook do Mercado Pago não passa pelo filtro (cai na assinatura: 401)")
    void webhookDeFora() throws Exception {
        mvc.perform(post("/webhooks/mercadopago")
                        .param("data.id", "1")
                        .header("Sec-Fetch-Site", "cross-site")
                        .header("Origin", "https://www.mercadopago.com.br"))
                .andExpect(status().isUnauthorized());
    }
}
