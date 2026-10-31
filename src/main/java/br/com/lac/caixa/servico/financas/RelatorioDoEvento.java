package br.com.lac.caixa.servico.financas;

import static br.com.lac.caixa.servico.financas.Contas.dinheiro;

import br.com.lac.caixa.repositorio.ConsultasFinanceiras;
import br.com.lac.caixa.repositorio.FiltroFinancas;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** O que só o relatório do evento (e os alertas) mostra: as vendas canceladas e os ajustes manuais. */
@Service
@Transactional(readOnly = true)
public class RelatorioDoEvento {
    private final ConsultasFinanceiras consultas;

    public RelatorioDoEvento(ConsultasFinanceiras consultas) {
        this.consultas = consultas;
    }

    /** Vendas canceladas do período (pela data da venda). */
    public List<Financas.LinhaCancelamento> cancelamentos(FiltroFinancas f) {
        return consultas.cancelamentos(f).stream()
                .map(c -> new Financas.LinhaCancelamento(
                        c.vendaId(), c.dataHora(), dinheiro(c.valor()), c.canceladaEm(), c.motivo()))
                .toList();
    }

    /** Vendas válidas com ajuste manual, com o motivo e a diferença (cobrado - calculado). */
    public List<Financas.LinhaAjuste> ajustesManuais(FiltroFinancas f) {
        return consultas.ajustesManuais(f).stream()
                .map(a -> new Financas.LinhaAjuste(
                        a.vendaId(),
                        a.dataHora(),
                        dinheiro(a.calculado()),
                        dinheiro(a.cobrado()),
                        dinheiro(a.cobrado().subtract(a.calculado())),
                        a.motivo()))
                .toList();
    }
}
