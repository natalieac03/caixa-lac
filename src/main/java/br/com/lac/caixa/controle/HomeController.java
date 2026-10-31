package br.com.lac.caixa.controle;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Tela inicial. */
@Controller
public class HomeController {
    @GetMapping("/")
    public String inicio() {
        return "inicio";
    }
}
