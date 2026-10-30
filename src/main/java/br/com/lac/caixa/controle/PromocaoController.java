package br.com.lac.caixa.controle;

import br.com.lac.caixa.modelo.TipoPromocao;
import br.com.lac.caixa.servico.CategoriaService;
import br.com.lac.caixa.servico.PromocaoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class PromocaoController {

    private final PromocaoService promocoes;
    private final CategoriaService categorias;

    public PromocaoController(PromocaoService promocoes, CategoriaService categorias) {
        this.promocoes = promocoes;
        this.categorias = categorias;
    }

    @GetMapping("/descontos")
    public String listar(Model model) {
        model.addAttribute("promocoes", promocoes.listar());
        model.addAttribute("categorias", categorias.listar());
        model.addAttribute("tipos", TipoPromocao.values());
        return "descontos";
    }

    /**
     * O formulário envia qtdCat_{catId} para cada categoria marcada.
     * Categorias desmarcadas ficam disabled e não chegam no params.
     */
    @PostMapping("/descontos/novo")
    public String novo(@RequestParam Map<String, String> params,
                       @RequestParam TipoPromocao tipo,
                       @RequestParam String preco,
                       RedirectAttributes flash) {
        try {
            BigDecimal valor = new BigDecimal(preco.trim().replace(",", "."));
            Map<Long, Integer> qtdPorCat = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (e.getKey().startsWith("qtdCat_")) {
                    Long catId = Long.valueOf(e.getKey().substring(7));
                    int qtd = Integer.parseInt(e.getValue().trim());
                    if (qtd > 0) qtdPorCat.put(catId, qtd);
                }
            }
            promocoes.criar(qtdPorCat, tipo, valor);
            flash.addFlashAttribute("sucesso", "Promoção criada.");
        } catch (NumberFormatException e) {
            flash.addFlashAttribute("erro", "Valor inválido.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/descontos";
    }

    @PostMapping("/descontos/{id}/alternar")
    public String alternar(@PathVariable Long id, RedirectAttributes flash) {
        promocoes.alternar(id);
        flash.addFlashAttribute("sucesso", "Promoção atualizada.");
        return "redirect:/descontos";
    }

    @PostMapping("/descontos/{id}/remover")
    public String remover(@PathVariable Long id, RedirectAttributes flash) {
        promocoes.remover(id);
        flash.addFlashAttribute("sucesso", "Promoção removida.");
        return "redirect:/descontos";
    }
}
