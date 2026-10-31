package br.com.lac.caixa.controle;

import br.com.lac.caixa.servico.financas.Financas.ResumoDoEvento;
import br.com.lac.caixa.servico.financas.RelatorioDoEvento;
import br.com.lac.caixa.servico.financas.ResumoFinanceiro;
import br.com.lac.caixa.util.Dinheiro;
import br.com.lac.caixa.util.GraficoSvg;
import java.math.BigDecimal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Relatório do evento para imprimir e o comparativo entre eventos. */
@Controller
public class RelatorioController {
    private final RelatorioDoEvento relatorio;
    private final ResumoFinanceiro resumos;

    public RelatorioController(RelatorioDoEvento relatorio, ResumoFinanceiro resumos) {
        this.relatorio = relatorio;
        this.resumos = resumos;
    }

    @GetMapping("/eventos/{id}/relatorio")
    public String relatorio(@PathVariable Long id, Model model) {
        var r = relatorio.montar(id);
        model.addAttribute("evento", r.evento());
        model.addAttribute("periodo", r.periodo());
        model.addAttribute("painel", r.painel());
        model.addAttribute("top", r.top());
        model.addAttribute("despesas", r.despesas());
        model.addAttribute("totalDespesas", r.totalDespesas());
        model.addAttribute("cancelamentos", r.cancelamentos());
        model.addAttribute("ajustes", r.ajustes());
        model.addAttribute("graficoProdutos", r.graficoProdutos());
        model.addAttribute("graficoCategorias", r.graficoCategorias());
        model.addAttribute("graficoFormas", r.graficoFormas());
        return "relatorio-evento";
    }

    @GetMapping("/eventos/comparativo")
    public String comparativo(Model model) {
        var lista = resumos.comparativoDeEventos();
        model.addAttribute("eventos", lista);
        model.addAttribute(
                "grafico",
                lista.isEmpty()
                        ? ""
                        : GraficoSvg.colunasAgrupadas(
                                "Receita líquida e lucro líquido por evento",
                                lista.stream().map(ResumoDoEvento::nome).toList(),
                                "Receita líquida",
                                lista.stream()
                                        .map(e -> e.resumo().receitaLiquida())
                                        .toList(),
                                lista.stream()
                                        .map(e -> Dinheiro.reais(e.resumo().receitaLiquida()))
                                        .toList(),
                                "Lucro líquido (listrado)",
                                lista.stream()
                                        .map(e -> zero(e.resumo().lucroLiquido()))
                                        .toList(),
                                lista.stream()
                                        .map(e -> Dinheiro.reais(zero(e.resumo().lucroLiquido())))
                                        .toList()));
        return "comparativo-eventos";
    }

    private static BigDecimal zero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : v;
    }
}
