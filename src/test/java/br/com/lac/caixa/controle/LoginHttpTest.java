package br.com.lac.caixa.controle;

import br.com.lac.caixa.RelogioDeTeste;
import br.com.lac.caixa.SenhaDeTeste;
import br.com.lac.caixa.config.LimiteLogin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:login;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class LoginHttpTest {
    @TestConfiguration
    static class Relogio {
        @Bean RelogioDeTeste relogioDeTeste() { return new RelogioDeTeste(); }
        @Bean @Primary LimiteLogin limiteComRelogio(RelogioDeTeste relogio) { return new LimiteLogin(relogio); }
    }

    @Autowired MockMvc mvc;
    @Autowired RelogioDeTeste relogio;

    ResultActions entrar(String ip, String senha) throws Exception {
        return mvc.perform(post("/login").param("senha", senha).with(r -> { r.setRemoteAddr(ip); return r; }));
    }

    @Test void erroMostraQuantosSegundosFaltam() throws Exception {
        var flash = entrar("10.1.0.1", "errada").andExpect(redirectedUrl("/login?erro"))
                .andExpect(flash().attribute("segundos", 1L)).andReturn().getFlashMap();
        mvc.perform(get("/login").param("erro", "").flashAttrs(flash))
                .andExpect(content().string(containsString("Senha incorreta.")))
                .andExpect(content().string(containsString("Tente de novo em")))
                .andExpect(content().string(containsString("data-segundos=\"1\"")));
    }

    @Test void senhaCertaDuranteAEsperaERecusadaEDepoisEntra() throws Exception {
        String ip = "10.1.0.2";
        entrar(ip, "errada").andExpect(flash().attribute("segundos", 1L));
        relogio.avancar(1);
        entrar(ip, "errada").andExpect(flash().attribute("segundos", 2L));
        entrar(ip, SenhaDeTeste.VALOR).andExpect(redirectedUrl("/login?espera")).andExpect(flash().attribute("segundos", 2L));
        relogio.avancar(2);
        entrar(ip, SenhaDeTeste.VALOR).andExpect(redirectedUrl("/"));
        // O acerto zerou o contador: o próximo erro volta a 1 s.
        entrar(ip, "errada").andExpect(flash().attribute("segundos", 1L));
    }

    @Test void vinteErrosBloqueiamOIpSemAfetarOutro() throws Exception {
        String ip = "10.1.0.3";
        for (int i = 1; i < 20; i++) {
            long espera = (Long) entrar(ip, "errada").andExpect(redirectedUrl("/login?erro")).andReturn().getFlashMap().get("segundos");
            relogio.avancar(espera);
        }
        entrar(ip, "errada").andExpect(redirectedUrl("/login?bloqueado")).andExpect(flash().attribute("segundos", 600L));
        var flash = entrar(ip, SenhaDeTeste.VALOR).andExpect(redirectedUrl("/login?bloqueado")).andReturn().getFlashMap();
        mvc.perform(get("/login").param("bloqueado", "").flashAttrs(flash))
                .andExpect(content().string(containsString("Muitas tentativas erradas")))
                .andExpect(content().string(containsString("data-segundos=\"600\"")));
        entrar("10.1.0.4", SenhaDeTeste.VALOR).andExpect(redirectedUrl("/"));
    }
}
