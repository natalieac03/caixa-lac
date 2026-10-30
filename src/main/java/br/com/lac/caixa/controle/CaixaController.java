package br.com.lac.caixa.controle;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.servico.*;
import br.com.lac.caixa.servico.preco.Orcamento;
import br.com.lac.caixa.util.Dinheiro;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;
@Controller
public class CaixaController {
    private final EstoqueService estoque;
    private final CaixaService caixa;
    public CaixaController(EstoqueService estoque, CaixaService caixa) { this.estoque=estoque; this.caixa=caixa; }
    @GetMapping("/caixa") public String abrirCaixa() { return "abrir-caixa"; }
    @GetMapping("/venda/nova") public String novaVenda(Model model) {
        model.addAttribute("tokenVenda", java.util.UUID.randomUUID().toString());
        model.addAttribute("produtos", estoque.listar()); model.addAttribute("formas", FormaPagamento.values()); return "venda-nova";
    }
    @PostMapping("/venda/previa") @ResponseBody
    public Orcamento previa(@RequestParam Map<String,String> params) { return caixa.previa(quantidades(params)); }
    @PostMapping("/venda")
    public String registrar(@RequestParam Map<String,String> params, @RequestParam FormaPagamento formaPagamento,
            @RequestParam(required=false) String formaPagamentoOutro, @RequestParam(required=false) String valorTotal,
            @RequestParam(required=false) String motivoAjuste, @RequestParam String token, RedirectAttributes flash) {
        if (caixa.tokenRegistrado(token)) return "redirect:/historico";
        try {
            var resultado=caixa.registrarVenda(quantidades(params), formaPagamento, formaPagamentoOutro, Dinheiro.parse(valorTotal), motivoAjuste, token);
            flash.addFlashAttribute("sucesso", "Venda #"+resultado.venda().getId()+" registrada. "+resultado.mensagemPagamento());
            return "redirect:/historico";
        } catch (org.springframework.dao.DataIntegrityViolationException | org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
            if (caixa.tokenRegistrado(token)) return "redirect:/historico";
            throw ex;
        }
    }
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public String estoqueMudou(RedirectAttributes flash) {
        flash.addFlashAttribute("erro", "O estoque mudou enquanto você vendia. Confira e confirme de novo.");
        return "redirect:/venda/nova";
    }

    private Map<Long,Integer> quantidades(Map<String,String> params) {
        Map<Long,Integer> resultado=new LinkedHashMap<>();
        params.forEach((chave,valor) -> { if (chave.startsWith("qtd_")) {
            int qtd=valor.isBlank()?0:Integer.parseInt(valor.trim());
            if (qtd<0) throw new IllegalArgumentException("Quantidade inválida.");
            if (qtd>0) resultado.put(Long.valueOf(chave.substring(4)),qtd);
        }});
        return resultado;
    }
}
