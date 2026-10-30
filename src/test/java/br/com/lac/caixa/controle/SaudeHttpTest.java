package br.com.lac.caixa.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:saude;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class SaudeHttpTest {
    @Autowired MockMvc mvc;

    @Test
    @DisplayName("/actuator/health responde UP sem login e sem detalhes")
    void healthSemLoginSemDetalhes() throws Exception {
        mvc.perform(get("/actuator/health"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("UP"))
           .andExpect(content().string(not(containsString("components"))))
           .andExpect(content().string(not(containsString("db"))));
    }

    @Test
    @DisplayName("Outros endpoints do actuator não existem e /actuator exige login")
    void outrosEndpointsFechados() throws Exception {
        for (String url : new String[]{"/actuator/env", "/actuator/beans", "/actuator/info", "/actuator/metrics"}) {
            mvc.perform(get(url).sessionAttr("autenticado", true)).andExpect(status().isNotFound());
        }
        // Sem a página de descoberta, /actuator cai no fluxo normal: sem login, vai para o login.
        mvc.perform(get("/actuator")).andExpect(redirectedUrl("/login"));
    }
}
