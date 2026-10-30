package br.com.lac.caixa.controle;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.modelo.PromocaoItem;
import br.com.lac.caixa.servico.CaixaService;
import br.com.lac.caixa.servico.EstoqueInsuficienteException;
import br.com.lac.caixa.servico.EstoqueService;
import br.com.lac.caixa.servico.PromocaoService;
import br.com.lac.caixa.servico.ResultadoVenda;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class CaixaController {

    private final EstoqueService estoque;
    private final CaixaService caixa;
    private final PromocaoService promocoes;
    private final ObjectMapper json;

    public CaixaController(EstoqueService estoque, CaixaService caixa,
                           PromocaoService promocoes, ObjectMapper json) {
        this.estoque = estoque;
        this.caixa = caixa;
        this.promocoes = promocoes;
        this.json = json;
    }

    @GetMapping("/caixa")
    public String abrirCaixa() {
        return "abrir-caixa";
    }

    @GetMapping("/venda/nova")
    public String novaVenda(Model model) {
        model.addAttribute("produtos", estoque.listar());
        model.addAttribute("formas", FormaPagamento.values());

        // Serializa as promoções ativas para JSON (consumido pelo JS da tela).
        List<Map<String, Object>> promoData = new ArrayList<>();
        for (Promocao p : promocoes.listarAtivas()) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("id", p.getId());
            pm.put("tipo", p.getTipo().name());
            pm.put("preco", p.getPreco().doubleValue());
            pm.put("nome", p.getNomesCategorias());
            List<Map<String, Object>> itens = new ArrayList<>();
            for (PromocaoItem pi : p.getItens()) {
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("catId", pi.getCategoria().getId());
                im.put("catNome", pi.getCategoria().getNome());
                im.put("qtd", pi.getQuantidade());
                itens.add(im);
            }
            pm.put("itens", itens);
            promoData.add(pm);
        }
        try {
            model.addAttribute("promosJson", json.writeValueAsString(promoData));
        } catch (Exception e) {
            model.addAttribute("promosJson", "[]");
        }
        return "venda-nova";
    }

    @PostMapping("/venda")
    public String registrar(@RequestParam Map<String, String> params,
                            @RequestParam FormaPagamento formaPagamento,
                            @RequestParam(required = false) String formaPagamentoOutro,
                            @RequestParam(required = false) String valorTotal,
                            RedirectAttributes flash) {

        Map<Long, Integer> quantidades = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getKey().startsWith("qtd_")) {
                Long produtoId = Long.valueOf(e.getKey().substring(4));
                int qtd = parseInt(e.getValue());
                if (qtd > 0) quantidades.put(produtoId, qtd);
            }
        }

        try {
            ResultadoVenda resultado = caixa.registrarVenda(quantidades, formaPagamento,
                    formaPagamentoOutro, parseDinheiro(valorTotal));
            flash.addFlashAttribute("sucesso",
                    "Venda #" + resultado.venda().getId() + " registrada. " + resultado.mensagemPagamento());
            return "redirect:/historico";
        } catch (EstoqueInsuficienteException | IllegalArgumentException ex) {
            flash.addFlashAttribute("erro", ex.getMessage());
            return "redirect:/venda/nova";
        }
    }

    private int parseInt(String v) {
        try { return (v == null || v.isBlank()) ? 0 : Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private BigDecimal parseDinheiro(String v) {
        if (v == null || v.isBlank()) return null;
        try { return new BigDecimal(v.trim().replace(",", ".")); }
        catch (NumberFormatException e) { return null; }
    }
}
