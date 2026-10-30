package br.com.lac.caixa.controle;
import br.com.lac.caixa.servico.LimiteLogin;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
class LoginControllerTest {
    @Test void loginTrocaIdDaSessao() {
        var req=new MockHttpServletRequest(); String antes=req.getSession().getId();
        var controller=new LoginController("teste",new LimiteLogin());
        assertEquals("redirect:/",controller.entrar("teste",req));
        assertNotEquals(antes,req.getSession().getId()); assertEquals(true,req.getSession().getAttribute("autenticado"));
    }
}
