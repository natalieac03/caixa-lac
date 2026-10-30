package br.com.lac.caixa.controle;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.servico.CategoriaService;
import br.com.lac.caixa.servico.EventoService;
import br.com.lac.caixa.servico.financas.Financas.Metrica;
import br.com.lac.caixa.servico.financas.PainelFinancas;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** Tela de Finanças: só mostra o que o FinancasService calcula (via PainelFinancas). */
@Controller
public class FinancasController {
    private final FiltrosDaTelaFinancas filtros;
    private final PainelFinancas painel;
    private final EventoService eventos;
    private final CategoriaService categorias;

    public FinancasController(
            FiltrosDaTelaFinancas filtros, PainelFinancas painel, EventoService eventos, CategoriaService categorias) {
        this.filtros = filtros;
        this.painel = painel;
        this.eventos = eventos;
        this.categorias = categorias;
    }

    /** Ranking só por quantidade, receita ou lucro (os da tela); o resto vira receita. */
    static Metrica metrica(String texto) {
        if (texto == null) return Metrica.RECEITA;
        return switch (texto.trim().toLowerCase()) {
            case "quantidade" -> Metrica.QUANTIDADE;
            case "lucro" -> Metrica.LUCRO;
            default -> Metrica.RECEITA;
        };
    }

    @GetMapping("/financas")
    public String tela(
            @RequestParam(required = false) String atalho,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(required = false) Long evento,
            @RequestParam(required = false) Long categoria,
            @RequestParam(required = false) FormaPagamento forma,
            @RequestParam(required = false) String metrica,
            Model model) {
        var resolvido = filtros.resolver(atalho, inicio, fim, evento, categoria, forma);
        Metrica m = metrica(metrica);
        model.addAttribute("f", resolvido);
        model.addAttribute("metrica", m.name().toLowerCase());
        model.addAttribute("painel", painel.montar(resolvido.filtro(), m));
        model.addAttribute("eventos", eventos.listar());
        model.addAttribute("categorias", categorias.listar());
        model.addAttribute("formas", FormaPagamento.values());
        return "financas";
    }

    /** Os dados dos gráficos, com o mesmo filtro da tela. */
    @GetMapping(value = "/financas/dados", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public PainelFinancas.Dados dados(
            @RequestParam(required = false) String atalho,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(required = false) Long evento,
            @RequestParam(required = false) Long categoria,
            @RequestParam(required = false) FormaPagamento forma,
            @RequestParam(required = false) String metrica) {
        var resolvido = filtros.resolver(atalho, inicio, fim, evento, categoria, forma);
        return painel.dados(resolvido.filtro(), metrica(metrica));
    }
}
