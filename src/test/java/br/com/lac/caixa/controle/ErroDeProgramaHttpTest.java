package br.com.lac.caixa.controle;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lac.caixa.servico.CaixaService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Erro de programa: pilha no log e "Algo deu errado" na tela. */
@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:erro_programa;DB_CLOSE_DELAY=-1"
        })
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ErroDeProgramaHttpTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    CaixaService caixa;

    @Test
    @DisplayName("Exceção inesperada na venda e na prévia: pilha no log, mensagem genérica na tela e no JSON")
    void erroDeProgramaVaiParaOLog(CapturedOutput log) throws Exception {
        when(caixa.registrarVenda(any(), any(), any(), any(), anyString()))
                .thenThrow(new IllegalArgumentException("bug de teste"));
        when(caixa.previa(any())).thenThrow(new IllegalArgumentException("bug de teste"));

        mvc.perform(post("/venda")
                        .sessionAttr("autenticado", true)
                        .param("token", UUID.randomUUID().toString())
                        .param("pag_forma_0", "PIX"))
                .andExpect(redirectedUrl("/venda/nova"))
                .andExpect(flash().attribute("erro", "Algo deu errado. Tente de novo."));
        assertTrue(log.getAll().contains("Erro inesperado em POST /venda"));
        assertTrue(log.getAll().contains("java.lang.IllegalArgumentException: bug de teste"), "a pilha vai para o log");

        mvc.perform(post("/venda/previa").sessionAttr("autenticado", true).param("qtd_1", "1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.erro").value("Algo deu errado. Tente de novo."));
    }
}
