package br.com.lac.caixa.controle;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    /** Senha definida em application.properties (app.senha), trocavel por variavel de ambiente. */
    private final String senhaCorreta;

    private final br.com.lac.caixa.servico.LimiteLogin limite;

    public LoginController(@Value("${app.senha}") String senhaCorreta, br.com.lac.caixa.servico.LimiteLogin limite) {
        this.limite = limite;
        this.senhaCorreta = senhaCorreta;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/login")
    public String entrar(@RequestParam String senha, HttpServletRequest request) {
        if (limite.autenticar(request.getRemoteAddr(), senhaCorreta, senha)) {
            HttpSession session = request.getSession(true);
            request.changeSessionId();
            session.setAttribute("autenticado", Boolean.TRUE);
            return "redirect:/";
        }
        return "redirect:/login?erro";
    }

    @GetMapping("/logout")
    public String sair(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
