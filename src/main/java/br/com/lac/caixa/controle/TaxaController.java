package br.com.lac.caixa.controle;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.TaxaPagamento;
import br.com.lac.caixa.servico.TaxaService;
import br.com.lac.caixa.util.Dinheiro;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TaxaController {
    private final TaxaService taxas;
    private final Clock relogio;

    public TaxaController(TaxaService taxas, Clock relogio) {
        this.taxas = taxas;
        this.relogio = relogio;
    }

    @GetMapping("/taxas")
    public String tela(Model model) {
        LocalDate hoje = LocalDate.now(relogio);
        Map<FormaPagamento, TaxaPagamento> vigentes = new LinkedHashMap<>();
        for (FormaPagamento f : FormaPagamento.values())
            vigentes.put(f, taxas.vigente(f, hoje).orElse(null));
        model.addAttribute("formas", FormaPagamento.values());
        model.addAttribute("vigentes", vigentes);
        model.addAttribute("historico", taxas.historico());
        model.addAttribute("hoje", hoje);
        model.addAttribute("semTaxa", taxas.vendasSemTaxaRegistrada());
        return "taxas";
    }

    @PostMapping("/taxas")
    public String definir(
            @RequestParam(required = false) FormaPagamento forma,
            @RequestParam(required = false) String percentual,
            @RequestParam(required = false) String valorFixo,
            @RequestParam(required = false) String vigenteDesde,
            RedirectAttributes flash) {
        if (forma == null) throw new RegraDeNegocioException("Escolha a forma de pagamento.");
        var t = taxas.definir(forma, percentual(percentual), Dinheiro.parse(valorFixo), data(vigenteDesde));
        flash.addFlashAttribute(
                "sucesso",
                "Taxa de " + forma.getRotulo() + " a partir de "
                        + t.getVigenteDesde().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ": "
                        + t.getPercentual().stripTrailingZeros().toPlainString().replace('.', ',') + "% + "
                        + Dinheiro.reais(t.getValorFixo()) + ".");
        return "redirect:/taxas";
    }

    /** "3,5" ou "3.5" (pontos percentuais), até 4 casas. Vazio = zero. */
    static BigDecimal percentual(String texto) {
        if (texto == null || texto.isBlank()) return BigDecimal.ZERO;
        String t = texto.trim().replace("%", "").trim().replace(',', '.');
        if (!t.matches("[0-9]{1,3}(\\.[0-9]{1,4})?"))
            throw new RegraDeNegocioException("Percentual inválido: " + texto.trim());
        return new BigDecimal(t);
    }

    private static LocalDate data(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException ex) {
            throw new RegraDeNegocioException("Data inválida.");
        }
    }
}
