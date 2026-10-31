package br.com.lac.caixa.controle;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lac.caixa.SenhaDeTeste;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:seguranca;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class SegurancaHttpTest {
    static final Set<String> PUBLICAS = Set.of("/login", "/logout", "/error", "/webhooks/mercadopago");

    @Autowired
    MockMvc mvc;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapeamentos;

    MockHttpServletRequestBuilder doIp(MockHttpServletRequestBuilder req, String ip) {
        return req.with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
    }

    /** Percorre todas as rotas registradas: uma rota nova sem login quebra este teste. */
    @Test
    void todaRotaExigeLogin() throws Exception {
        int conferidas = 0;
        for (var info : mapeamentos.getHandlerMethods().keySet()) {
            for (String padrao : info.getPatternValues()) {
                if (PUBLICAS.contains(padrao)) continue;
                String url = padrao.replaceAll("\\{[^}]+}", "1");
                var metodos = info.getMethodsCondition().getMethods();
                var metodo = metodos.isEmpty()
                        ? RequestMethod.GET
                        : metodos.iterator().next();
                var req = metodo == RequestMethod.POST ? post(url) : get(url);
                if (url.equals("/venda/previa") || url.startsWith("/venda/pix")) {
                    // Chamada do JS: 401 em JSON em vez de redirect (veja SessaoEErrosHttpTest).
                    mvc.perform(req).andExpect(status().isUnauthorized());
                } else {
                    mvc.perform(req).andExpect(redirectedUrl("/login"));
                }
                conferidas++;
            }
        }
        // O webhook é público, mas sem assinatura válida responde 401 (e sem data.id, 400).
        mvc.perform(post("/webhooks/mercadopago").param("data.id", "1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/webhooks/mercadopago")).andExpect(status().isBadRequest());
        assertTrue(conferidas >= 15, "rotas conferidas: " + conferidas);
    }

    @Test
    void logoutSoPorPostEEncerraASessao() throws Exception {
        var sessao = new MockHttpSession();
        mvc.perform(doIp(post("/login").param("senha", SenhaDeTeste.VALOR), "198.51.100.9")
                        .session(sessao))
                .andExpect(redirectedUrl("/"));
        mvc.perform(get("/historico").session(sessao)).andExpect(status().isOk());
        mvc.perform(get("/logout").session(sessao)).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/historico").session(sessao)).andExpect(status().isOk());
        mvc.perform(post("/logout").session(sessao)).andExpect(redirectedUrl("/login"));
        assertTrue(sessao.isInvalid());
    }
}
