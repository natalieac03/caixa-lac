package br.com.lac.caixa.controle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:estaticos;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class EstaticosHttpTest {
    @Autowired MockMvc mvc;

    @Test void estaticosTemHashNaUrlECacheLongo() throws Exception {
        String pagina = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var css = Pattern.compile("/css/estilo-[0-9a-f]{32}\\.css").matcher(pagina);
        var logo = Pattern.compile("/img/logo-[0-9a-f]{32}\\.png").matcher(pagina);
        assertTrue(css.find(), "CSS sem hash na página de login");
        assertTrue(logo.find(), "logo sem hash na página de login");
        for (String url : new String[]{css.group(), logo.group()}) {
            mvc.perform(get(url)).andExpect(status().isOk())
               .andExpect(header().string("Cache-Control", "max-age=31536000, public"));
        }
    }
}
