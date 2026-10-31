package br.com.lac.caixa.controle;

import br.com.lac.caixa.consulta.FiltroFinancas;
import br.com.lac.caixa.servico.DespesaService;
import br.com.lac.caixa.servico.EventoService;
import br.com.lac.caixa.servico.financas.ComparativoDeEventos;
import br.com.lac.caixa.servico.financas.Financas.LinhaCategoria;
import br.com.lac.caixa.servico.financas.Financas.LinhaForma;
import br.com.lac.caixa.servico.financas.Financas.Metrica;
import br.com.lac.caixa.servico.financas.Financas.ResumoDoEvento;
import br.com.lac.caixa.servico.financas.PainelFinancas;
import br.com.lac.caixa.servico.financas.RelatorioDoEvento;
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
    private final EventoService eventos;
    private final PainelFinancas painel;
    private final RelatorioDoEvento relatorio;
    private final ComparativoDeEventos comparativo;
    private final DespesaService despesas;

    public RelatorioController(
            EventoService eventos,
            PainelFinancas painel,
            RelatorioDoEvento relatorio,
            ComparativoDeEventos comparativo,
            DespesaService despesas) {
        this.eventos = eventos;
        this.painel = painel;
        this.relatorio = relatorio;
        this.comparativo = comparativo;
        this.despesas = despesas;
    }

    @GetMapping("/eventos/{id}/relatorio")
    public String relatorio(@PathVariable Long id, Model model) {
        var evento = eventos.buscar(id);
        var filtro = FiltroFinancas.doEvento(eventos.periodo(id));
        var p = painel.montar(filtro, Metrica.RECEITA);
        var top = p.produtos().stream()
                .limit(10)
                .map(PainelFinancas.ProdutoAbc::produto)
                .toList();
        var filtroDespesas = new DespesaService.Filtro(id, null, null);
        model.addAttribute("evento", evento);
        model.addAttribute("periodo", eventos.periodo(evento));
        model.addAttribute("painel", p);
        model.addAttribute("top", top);
        model.addAttribute("despesas", despesas.filtrar(filtroDespesas));
        model.addAttribute("totalDespesas", despesas.total(filtroDespesas));
        model.addAttribute("cancelamentos", relatorio.cancelamentos(filtro));
        model.addAttribute("ajustes", relatorio.ajustesManuais(filtro));
        model.addAttribute(
                "graficoProdutos",
                GraficoSvg.barrasHorizontais(
                        "Top 10 produtos por receita líquida",
                        top.stream().map(x -> x.nome()).toList(),
                        top.stream().map(x -> x.receitaLiquida()).toList(),
                        top.stream()
                                .map(x -> Dinheiro.reais(x.receitaLiquida()))
                                .toList()));
        model.addAttribute(
                "graficoCategorias",
                GraficoSvg.barrasHorizontais(
                        "Receita líquida por categoria",
                        p.categorias().stream().map(LinhaCategoria::categoria).toList(),
                        p.categorias().stream()
                                .map(LinhaCategoria::receitaLiquida)
                                .toList(),
                        p.categorias().stream()
                                .map(c -> Dinheiro.reais(c.receitaLiquida()))
                                .toList()));
        var formas = p.formas().stream().filter(l -> l.vendas() > 0).toList();
        model.addAttribute(
                "graficoFormas",
                GraficoSvg.barrasHorizontais(
                        "Valor recebido por forma de pagamento",
                        formas.stream().map(l -> l.forma().getRotulo()).toList(),
                        formas.stream().map(LinhaForma::valor).toList(),
                        formas.stream()
                                .map(l -> Dinheiro.reais(l.valor()) + " (" + l.vendas()
                                        + (l.vendas() == 1 ? " venda)" : " vendas)"))
                                .toList()));
        return "relatorio-evento";
    }

    @GetMapping("/eventos/comparativo")
    public String comparativo(Model model) {
        var lista = comparativo.eventos();
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
