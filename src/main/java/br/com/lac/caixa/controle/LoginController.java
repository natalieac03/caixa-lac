package br.com.lac.caixa.controle;

import br.com.lac.caixa.config.SenhaDeAcesso;
import br.com.lac.caixa.servico.LimiteLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class LoginController {

    /** Senha de APP_SENHA, conferida na inicialização. */
    private final SenhaDeAcesso senha;

    /** Espera progressiva por IP e bloqueio após 20 erros em 10 minutos. */
    private final LimiteLogin limite;

    public LoginController(SenhaDeAcesso senha, LimiteLogin limite) {
        this.senha = senha;
        this.limite = limite;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /**
     * Erro, espera e bloqueio voltam para /login com ?erro, ?espera ou
     * ?bloqueado; os segundos que faltam vão no flash "segundos".
     */
    @PostMapping("/login")
    public String entrar(@RequestParam("senha") String informada, HttpServletRequest request,
                         RedirectAttributes flash) {
        LimiteLogin.Tentativa tentativa = limite.tentar(request.getRemoteAddr(), senha.confere(informada));
        if (tentativa.aceita()) {
            HttpSession session = request.getSession(true);
            // Novo id após autenticar: um id de sessão anterior ao login não serve mais.
            request.changeSessionId();
            session.setAttribute("autenticado", Boolean.TRUE);
            return "redirect:/";
        }
        flash.addFlashAttribute("segundos", tentativa.segundos());
        return switch (tentativa.situacao()) {
            case AGUARDE -> "redirect:/login?espera";
            case BLOQUEADO -> "redirect:/login?bloqueado";
            default -> "redirect:/login?erro";
        };
    }

    /** POST: um link ou imagem em outra página não consegue deslogar a pessoa. */
    @PostMapping("/logout")
    public String sair(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return "redirect:/login";
    }
}
