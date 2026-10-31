package br.com.lac.caixa.controle;

import br.com.lac.caixa.config.LimiteLogin;
import br.com.lac.caixa.config.SenhaDeAcesso;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Login com a senha única e logout. */
@Controller
public class LoginController {

    private final SenhaDeAcesso senha;

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
    public String entrar(
            @RequestParam("senha") String informada, HttpServletRequest request, RedirectAttributes flash) {
        LimiteLogin.Tentativa tentativa = limite.tentar(request.getRemoteAddr(), senha.confere(informada));
        if (tentativa.aceita()) {
            HttpSession session = request.getSession(true);
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

    @PostMapping("/logout")
    public String sair(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return "redirect:/login";
    }
}
