package br.com.lac.caixa.controle;

import br.com.lac.caixa.config.SenhaDeAcesso;
import br.com.lac.caixa.servico.LimiteLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    /** Senha de APP_SENHA, conferida na inicialização. */
    private final SenhaDeAcesso senha;

    /** Bloqueia o IP por 10 minutos após 5 erros em 10 minutos. */
    private final LimiteLogin limite;

    public LoginController(SenhaDeAcesso senha, LimiteLogin limite) {
        this.senha = senha;
        this.limite = limite;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/login")
    public String entrar(@RequestParam("senha") String informada, HttpServletRequest request) {
        if (limite.autenticar(request.getRemoteAddr(), senha.confere(informada))) {
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
