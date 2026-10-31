package br.com.lac.caixa.servico.financas;

import br.com.lac.caixa.consulta.FiltroFinancas;
import br.com.lac.caixa.consulta.PeriodoDoEvento;
import br.com.lac.caixa.modelo.Despesa;
import br.com.lac.caixa.modelo.Evento;
import br.com.lac.caixa.servico.DespesaService;
import br.com.lac.caixa.servico.EventoService;
import br.com.lac.caixa.servico.financas.Financas.LinhaAjuste;
import br.com.lac.caixa.servico.financas.Financas.LinhaCancelamento;
import br.com.lac.caixa.servico.financas.Financas.LinhaCategoria;
import br.com.lac.caixa.servico.financas.Financas.LinhaForma;
import br.com.lac.caixa.servico.financas.Financas.LinhaProduto;
import br.com.lac.caixa.servico.financas.Financas.Metrica;
import br.com.lac.caixa.servico.financas.PainelFinancas.Painel;
import br.com.lac.caixa.servico.financas.PainelFinancas.ProdutoAbc;
import br.com.lac.caixa.util.Dinheiro;
import br.com.lac.caixa.util.GraficoSvg;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Monta o relatório do evento para imprimir: painel, top 10, despesas, ocorrências e os gráficos em SVG. */
@Service
@Transactional(readOnly = true)
public class RelatorioDoEvento {
    private final EventoService eventos;
    private final PainelFinancas painel;
    private final OcorrenciasFinanceiras ocorrencias;
    private final DespesaService despesas;

    public RelatorioDoEvento(
            EventoService eventos, PainelFinancas painel, OcorrenciasFinanceiras ocorrencias, DespesaService despesas) {
        this.eventos = eventos;
        this.painel = painel;
        this.ocorrencias = ocorrencias;
        this.despesas = despesas;
    }

    public record Relatorio(
            Evento evento,
            PeriodoDoEvento periodo,
            Painel painel,
            List<LinhaProduto> top,
            List<Despesa> despesas,
            BigDecimal totalDespesas,
            List<LinhaCancelamento> cancelamentos,
            List<LinhaAjuste> ajustes,
            String graficoProdutos,
            String graficoCategorias,
            String graficoFormas) {}

    public Relatorio montar(Long id) {
        var evento = eventos.buscar(id);
        var filtro = FiltroFinancas.doEvento(eventos.periodo(id));
        var p = painel.montar(filtro, Metrica.RECEITA);
        var top = p.produtos().stream().limit(10).map(ProdutoAbc::produto).toList();
        return new Relatorio(
                evento,
                eventos.periodo(evento),
                p,
                top,
                despesas.filtrar(new DespesaService.Filtro(id, null, null)),
                p.resumo().despesas(),
                ocorrencias.cancelamentos(filtro),
                ocorrencias.ajustesManuais(filtro),
                graficoProdutos(top),
                graficoCategorias(p.categorias()),
                graficoFormas(p.formas()));
    }

    private static String graficoProdutos(List<LinhaProduto> top) {
        return GraficoSvg.barrasHorizontais(
                "Top 10 produtos por receita líquida",
                top.stream().map(LinhaProduto::nome).toList(),
                top.stream().map(LinhaProduto::receitaLiquida).toList(),
                top.stream().map(x -> Dinheiro.reais(x.receitaLiquida())).toList());
    }

    private static String graficoCategorias(List<LinhaCategoria> categorias) {
        return GraficoSvg.barrasHorizontais(
                "Receita líquida por categoria",
                categorias.stream().map(LinhaCategoria::categoria).toList(),
                categorias.stream().map(LinhaCategoria::receitaLiquida).toList(),
                categorias.stream().map(c -> Dinheiro.reais(c.receitaLiquida())).toList());
    }

    private static String graficoFormas(List<LinhaForma> todas) {
        var formas = todas.stream().filter(l -> l.vendas() > 0).toList();
        return GraficoSvg.barrasHorizontais(
                "Valor recebido por forma de pagamento",
                formas.stream().map(l -> l.forma().getRotulo()).toList(),
                formas.stream().map(LinhaForma::valor).toList(),
                formas.stream()
                        .map(l -> Dinheiro.reais(l.valor()) + " (" + l.vendas()
                                + (l.vendas() == 1 ? " venda)" : " vendas)"))
                        .toList());
    }
}
