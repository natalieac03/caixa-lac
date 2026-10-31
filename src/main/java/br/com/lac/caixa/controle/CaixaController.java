package br.com.lac.caixa.controle;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.servico.CaixaService;
import br.com.lac.caixa.servico.CobrancaPixService;
import br.com.lac.caixa.servico.EstoqueService;
import br.com.lac.caixa.servico.ResultadoVenda;
import br.com.lac.caixa.servico.preco.Orcamento;
import br.com.lac.caixa.util.Dinheiro;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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

@Controller
public class CaixaController {

    private final EstoqueService estoque;
    private final CaixaService caixa;
    private final CobrancaPixService cobrancasPix;

    public CaixaController(EstoqueService estoque, CaixaService caixa, CobrancaPixService cobrancasPix) {
        this.estoque = estoque;
        this.caixa = caixa;
        this.cobrancasPix = cobrancasPix;
    }

    @GetMapping("/caixa")
    public String abrirCaixa() {
        return "abrir-caixa";
    }

    /** Cada formulário leva um token novo: reenviar o mesmo não cria outra venda. */
    @GetMapping("/venda/nova")
    public String novaVenda(Model model) {
        return tela(model, UUID.randomUUID().toString());
    }

    private String tela(Model model, String token) {
        model.addAttribute("tokenVenda", token);
        model.addAttribute("produtos", estoque.listar());
        model.addAttribute("formas", FormaPagamento.values());
        model.addAttribute("pixAutomatico", cobrancasPix.ligado());
        return "venda-nova";
    }

    /**
     * A venda não entrou (estoque acabou, Pix ainda não pago, conflito...): a tela
     * volta com o mesmo carrinho e o mesmo token, para corrigir e confirmar de novo
     * sem digitar tudo (e sem duplicar a venda se ela tiver entrado).
     */
    private String telaDeNovo(
            Map<String, String> params, String token, String erro, Model model, HttpServletResponse resposta) {
        resposta.setStatus(HttpServletResponse.SC_CONFLICT);
        Map<String, String> carrinho = new LinkedHashMap<>();
        params.forEach((chave, valor) -> {
            if (chave.startsWith("qtd_") && valor != null && !valor.isBlank()) carrinho.put(chave.substring(4), valor);
        });
        model.addAttribute("erro", erro);
        model.addAttribute("carrinho", carrinho);
        model.addAttribute("valorTotalDigitado", params.get("valorTotal"));
        model.addAttribute("motivoDigitado", params.get("motivoAjuste"));
        model.addAttribute("formaEscolhida", params.get("pag_forma_0"));
        model.addAttribute("descricaoDigitada", params.get("pag_descricao_0"));
        model.addAttribute("recebidoDigitado", params.get("pag_recebido_0"));
        return tela(model, token);
    }

    /** Orçamento calculado no servidor; a tela só desenha o resultado. */
    @PostMapping("/venda/previa")
    @ResponseBody
    public Orcamento previa(@RequestParam Map<String, String> params) {
        return caixa.previa(Quantidades.porProduto(params));
    }

    @PostMapping("/venda")
    public String registrar(
            @RequestParam Map<String, String> params,
            @RequestParam(required = false) String valorTotal,
            @RequestParam(required = false) String motivoAjuste,
            @RequestParam String token,
            RedirectAttributes flash,
            Model model,
            HttpServletResponse resposta) {
        try {
            ResultadoVenda resultado = caixa.registrarVenda(
                    Quantidades.porProduto(params),
                    PagamentosDoFormulario.ler(params),
                    Dinheiro.parse(valorTotal),
                    motivoAjuste,
                    token);
            flash.addFlashAttribute(
                    "sucesso",
                    resultado.repetida()
                            ? "Venda #" + resultado.venda().getId() + " já estava registrada."
                            : "Venda #" + resultado.venda().getId() + " registrada. " + resultado.mensagemPagamento());
            return "redirect:/historico";
        } catch (RegraDeNegocioException ex) {
            return telaDeNovo(params, token, ex.getMessage(), model, resposta);
        } catch (ObjectOptimisticLockingFailureException ex) {
            return telaDeNovo(
                    params,
                    token,
                    "O estoque mudou enquanto você vendia. Confira e confirme de novo.",
                    model,
                    resposta);
        } catch (DataIntegrityViolationException ex) {
            // Corrida: dois envios do mesmo token ao mesmo tempo; o segundo bate no índice único.
            if (!caixa.tokenRegistrado(token)) throw ex;
            flash.addFlashAttribute("sucesso", "Venda já registrada.");
            return "redirect:/historico";
        }
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public String estoqueMudou(RedirectAttributes flash) {
        flash.addFlashAttribute("erro", "O estoque mudou enquanto você vendia. Confira e confirme de novo.");
        return "redirect:/venda/nova";
    }
}
