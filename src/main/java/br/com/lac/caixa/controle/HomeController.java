package br.com.lac.caixa.controle;

import br.com.lac.caixa.servico.AlertasService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    private final AlertasService alertas;

    public HomeController(AlertasService alertas) {
        this.alertas = alertas;
    }

    @GetMapping("/")
    public String inicio(Model model) {
        model.addAttribute("alertas", alertas.calcular());
        return "inicio";
    }
}
