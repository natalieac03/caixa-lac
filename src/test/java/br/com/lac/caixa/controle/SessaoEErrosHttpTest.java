package br.com.lac.caixa.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lac.caixa.repositorio.VendaRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:sessao;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class SessaoEErrosHttpTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    ServerProperties servidor;

    @Autowired
    VendaRepository vendas;

    @Test
    @DisplayName("Sessão dura 12 horas (um dia de evento)")
    void sessaoDeDozeHoras() {
        assertEquals(Duration.ofHours(12), servidor.getServlet().getSession().getTimeout());
    }

    @Test
    @DisplayName("Prévia sem sessão responde 401 com JSON, em vez de redirecionar para o login")
    void previaSemSessaoE401() throws Exception {
        mvc.perform(post("/venda/previa").param("qtd_1", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.erro").value("Sessão expirada"));
    }

    @Test
    @DisplayName("Quem pede JSON sem sessão recebe 401; uma tela normal continua indo para o login")
    void acceptJsonSemSessao() throws Exception {
        mvc.perform(get("/historico").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("Sessão expirada"));
        mvc.perform(get("/historico")).andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Prévia com erro de regra devolve a mensagem do servidor em \"erro\"")
    void previaComErroDeRegra() throws Exception {
        mvc.perform(post("/venda/previa").sessionAttr("autenticado", true).param("qtd_1", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Quantidade inválida: -1"));
    }

    @Test
    @DisplayName("Erro de programa (NumberFormatException) não vira mensagem: log com a pilha e \"Algo deu errado\"")
    void erroDeProgramaVaiParaOLog(CapturedOutput log) throws Exception {
        long antes = vendas.count();

        mvc.perform(post("/venda")
                        .sessionAttr("autenticado", true)
                        .param("token", UUID.randomUUID().toString())
                        .param("qtd_abc", "1")
                        .param("pag_forma_0", "PIX"))
                .andExpect(redirectedUrl("/venda/nova"))
                .andExpect(flash().attribute("erro", "Algo deu errado. Tente de novo."));

        assertEquals(antes, vendas.count());
        assertTrue(log.getAll().contains("Erro inesperado em POST /venda"));
        assertTrue(log.getAll().contains("java.lang.NumberFormatException"), "a pilha vai para o log");
        mvc.perform(post("/venda/previa").sessionAttr("autenticado", true).param("qtd_abc", "1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.erro").value("Algo deu errado. Tente de novo."));
    }

    @Test
    @DisplayName("Erro volta para a página de origem (Referer do próprio site), mantendo os filtros")
    void voltaParaOReferer() throws Exception {
        mvc.perform(post("/historico/999999/cancelar")
                        .sessionAttr("autenticado", true)
                        .param("motivo", "x")
                        .header("Referer", "http://localhost/historico?forma=PIX&produto=b%C3%B3ton&pagina=2"))
                .andExpect(redirectedUrl("/historico?forma=PIX&produto=b%C3%B3ton&pagina=2"))
                .andExpect(flash().attribute("erro", "Venda não encontrada: 999999"));
    }

    @Test
    @DisplayName("Referer de outro site, de outra porta ou malformado é ignorado: volta para a lista da área")
    void refererDeForaEIgnorado() throws Exception {
        for (String referer : new String[] {
            "https://outro-site.com/historico?x=1",
            "http://localhost:9999/historico?x=1",
            "http://localhost//outro-site.com/x",
            "::nao é url::"
        }) {
            mvc.perform(post("/historico/999999/cancelar")
                            .sessionAttr("autenticado", true)
                            .param("motivo", "x")
                            .header("Referer", referer))
                    .andExpect(redirectedUrl("/historico"));
        }
        mvc.perform(post("/estoque/999999/adicionar")
                        .sessionAttr("autenticado", true)
                        .param("quantidade", "1"))
                .andExpect(redirectedUrl("/estoque"));
    }
}
