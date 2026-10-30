package br.com.lac.caixa.controle;

import br.com.lac.caixa.servico.LimiteLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    /** Senha definida em app.senha (APP_SENHA em produção, obrigatória). */
    private final String senhaCorreta;

    /** Bloqueia o IP por 10 minutos após 5 erros em 10 minutos. */
    private final LimiteLogin limite;

    public LoginController(@Value("${app.senha}") String senhaCorreta, LimiteLogin limite) {
        this.senhaCorreta = senhaCorreta;
        this.limite = limite;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/login")
    public String entrar(@RequestParam String senha, HttpServletRequest request) {
        if (limite.autenticar(request.getRemoteAddr(), senhaCorreta, senha)) {
            HttpSession session = request.getSession(true);
            // Novo id após autenticar: um id de sessão anterior ao login não serve mais.
            request.changeSessionId();
            session.setAttribute("autenticado", Boolean.TRUE);
            return "redirect:/";
        }
        return "redirect:/login?erro";
    }

    /** POST: um link ou imagem em outra página não consegue deslogar a pessoa. */
    @PostMapping("/logout")
    public String sair(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return "redirect:/login";
    }
}
