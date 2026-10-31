package br.com.lac.caixa.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import br.com.lac.caixa.SenhaDeTeste;
import br.com.lac.caixa.config.LimiteLogin;
import br.com.lac.caixa.config.SenhaDeAcesso;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

/** O login troca o id da sessão. */
class LoginControllerTest {

    @Test
    @DisplayName("Login certo troca o id da sessão e marca a sessão como autenticada")
    void loginTrocaIdDaSessao() {
        var req = new MockHttpServletRequest();
        String antes = req.getSession().getId();
        var senha = new SenhaDeAcesso(new MockEnvironment().withProperty("app.senha", SenhaDeTeste.VALOR));
        var controller = new LoginController(senha, new LimiteLogin());

        String destino = controller.entrar(SenhaDeTeste.VALOR, req, new RedirectAttributesModelMap());

        assertEquals("redirect:/", destino);
        assertNotEquals(antes, req.getSession().getId());
        assertEquals(true, req.getSession().getAttribute("autenticado"));
    }
}
