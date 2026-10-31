package br.com.lac.caixa.controle;

import br.com.lac.caixa.controle.csv.ExportadorFinancas;
import br.com.lac.caixa.controle.csv.RespostaCsv;
import br.com.lac.caixa.controle.csv.TabelaCsv;
import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.servico.CategoriaService;
import br.com.lac.caixa.servico.EventoService;
import br.com.lac.caixa.servico.financas.Financas.Metrica;
import br.com.lac.caixa.servico.financas.PainelFinancas;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** Tela de Finanças, o JSON dos gráficos e os CSVs de cada tabela. */
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
            @RequestParam(required = false) String evento,
            @RequestParam(required = false) Long categoria,
            @RequestParam(required = false) FormaPagamento forma,
            @RequestParam(required = false) String metrica,
            Model model,
            HttpServletResponse resposta) {
        FiltrosDaTelaFinancas.Resolvido resolvido;
        try {
            resolvido = filtros.resolver(atalho, inicio, fim, evento, categoria, forma);
        } catch (RegraDeNegocioException ex) {
            resposta.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("erro", ex.getMessage());
            resolvido = filtros.resolver("mes", null, null, null, categoria, forma);
        }
        Metrica m = metrica(metrica);
        model.addAttribute("f", resolvido);
        model.addAttribute("metrica", m.name().toLowerCase());
        model.addAttribute("painel", painel.montar(resolvido.filtro(), m));
        model.addAttribute("eventos", eventos.listar());
        model.addAttribute("categorias", categorias.listar());
        model.addAttribute("formas", FormaPagamento.values());
        return "financas";
    }

    @GetMapping(value = "/financas/dados", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public PainelFinancas.Dados dados(
            @RequestParam(required = false) String atalho,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(required = false) String evento,
            @RequestParam(required = false) Long categoria,
            @RequestParam(required = false) FormaPagamento forma,
            @RequestParam(required = false) String metrica) {
        var resolvido = filtros.resolver(atalho, inicio, fim, evento, categoria, forma);
        return painel.dados(resolvido.filtro(), metrica(metrica));
    }

    @GetMapping("/financas/exportar/{tabela}.csv")
    public ResponseEntity<Resource> exportar(
            @PathVariable String tabela,
            @RequestParam(required = false) String atalho,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(required = false) String evento,
            @RequestParam(required = false) Long categoria,
            @RequestParam(required = false) FormaPagamento forma,
            @RequestParam(required = false) String metrica) {
        var tabelaCsv =
                TabelaCsv.porNome(tabela).orElseThrow(() -> new RegraDeNegocioException("Tabela desconhecida."));
        var resolvido = filtros.resolver(atalho, inicio, fim, evento, categoria, forma);
        var dados = painel.tabela(resolvido.filtro(), metrica(metrica), tabelaCsv.parte());
        return RespostaCsv.arquivo(
                ExportadorFinancas.gerar(tabelaCsv, dados, resolvido.periodo()), "financas-" + tabela + ".csv");
    }
}
