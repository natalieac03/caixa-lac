package br.com.lac.caixa.controle;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.servico.CaixaService;
import br.com.lac.caixa.servico.EstoqueService;
import br.com.lac.caixa.servico.ResultadoVenda;
import br.com.lac.caixa.servico.preco.Orcamento;
import br.com.lac.caixa.util.Dinheiro;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.UUID;

@Controller
public class CaixaController {

    private final EstoqueService estoque;
    private final CaixaService caixa;

    public CaixaController(EstoqueService estoque, CaixaService caixa) {
        this.estoque = estoque;
        this.caixa = caixa;
    }

    @GetMapping("/caixa")
    public String abrirCaixa() {
        return "abrir-caixa";
    }

    /** Cada formulário leva um token novo: reenviar o mesmo não cria outra venda. */
    @GetMapping("/venda/nova")
    public String novaVenda(Model model) {
        model.addAttribute("tokenVenda", UUID.randomUUID().toString());
        model.addAttribute("produtos", estoque.listar());
        model.addAttribute("formas", FormaPagamento.values());
        return "venda-nova";
    }

    /** Orçamento calculado no servidor; a tela só desenha o resultado. */
    @PostMapping("/venda/previa")
    @ResponseBody
    public Orcamento previa(@RequestParam Map<String, String> params) {
        return caixa.previa(Quantidades.porProduto(params));
    }

    @PostMapping("/venda")
    public String registrar(@RequestParam Map<String, String> params,
                            @RequestParam FormaPagamento formaPagamento,
                            @RequestParam(required = false) String formaPagamentoOutro,
                            @RequestParam(required = false) String valorTotal,
                            @RequestParam(required = false) String motivoAjuste,
                            @RequestParam String token,
                            RedirectAttributes flash) {
        if (caixa.tokenRegistrado(token)) return "redirect:/historico";
        try {
            ResultadoVenda resultado = caixa.registrarVenda(Quantidades.porProduto(params), formaPagamento,
                    formaPagamentoOutro, Dinheiro.parse(valorTotal), motivoAjuste, token);
            flash.addFlashAttribute("sucesso",
                    "Venda #" + resultado.venda().getId() + " registrada. " + resultado.mensagemPagamento());
            return "redirect:/historico";
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
            // Dois envios simultâneos do mesmo token: o segundo perde, mas a venda existe.
            if (caixa.tokenRegistrado(token)) return "redirect:/historico";
            throw ex;
        }
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public String estoqueMudou(RedirectAttributes flash) {
        flash.addFlashAttribute("erro", "O estoque mudou enquanto você vendia. Confira e confirme de novo.");
        return "redirect:/venda/nova";
    }
}
