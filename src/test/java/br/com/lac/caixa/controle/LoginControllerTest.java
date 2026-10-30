package br.com.lac.caixa.controle;
import br.com.lac.caixa.config.LimiteLogin;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
class LoginControllerTest {
    @Test void loginTrocaIdDaSessao() {
        var req=new MockHttpServletRequest(); String antes=req.getSession().getId();
        var controller=new LoginController(new br.com.lac.caixa.config.SenhaDeAcesso(new org.springframework.mock.env.MockEnvironment().withProperty("app.senha",br.com.lac.caixa.SenhaDeTeste.VALOR)),new LimiteLogin());
        assertEquals("redirect:/",controller.entrar(br.com.lac.caixa.SenhaDeTeste.VALOR,req,new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap()));
        assertNotEquals(antes,req.getSession().getId()); assertEquals(true,req.getSession().getAttribute("autenticado"));
    }
}
