package br.com.lac.caixa.controle;

import br.com.lac.caixa.modelo.TipoPromocao;
import br.com.lac.caixa.servico.CategoriaService;
import br.com.lac.caixa.servico.PromocaoService;
import br.com.lac.caixa.util.Dinheiro;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
        promocoes.criar(qtdPorCategoria(params), tipo, Dinheiro.parse(preco));
        flash.addFlashAttribute("sucesso", "Promoção criada.");
        return "redirect:/descontos";
    }

    /** Mesmo formulário da criação, já preenchido com a promoção. */
    @GetMapping("/descontos/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        listar(model);
        model.addAttribute("edicao", promocoes.buscar(id));
        return "descontos";
    }

    @PostMapping("/descontos/{id}/editar")
    public String salvarEdicao(@PathVariable Long id,
                               @RequestParam Map<String, String> params,
                               @RequestParam String preco,
                               RedirectAttributes flash) {
        var p = promocoes.editar(id, qtdPorCategoria(params), Dinheiro.parse(preco));
        flash.addFlashAttribute("sucesso", "Promoção " + p.getNomesCategorias() + " atualizada.");
        return "redirect:/descontos";
    }

    /**
     * O formulário envia qtdCat_{catId} para cada categoria marcada.
     * Categorias desmarcadas ficam disabled e não chegam no params.
     */
    private static Map<Long, Integer> qtdPorCategoria(Map<String, String> params) {
        Map<Long, Integer> qtdPorCat = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getKey().startsWith("qtdCat_")) {
                Long catId = Long.valueOf(e.getKey().substring(7));
                int qtd = Quantidades.inteiro(e.getValue());
                if (qtd > 0) qtdPorCat.put(catId, qtd);
            }
        }
        return qtdPorCat;
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
