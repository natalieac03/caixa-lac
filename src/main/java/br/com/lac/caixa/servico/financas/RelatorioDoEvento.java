package br.com.lac.caixa.servico.financas;

import static br.com.lac.caixa.servico.financas.Contas.dinheiro;

import br.com.lac.caixa.repositorio.ConsultasFinanceiras;
import br.com.lac.caixa.repositorio.FiltroFinancas;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** O que só o relatório do evento mostra: vendas canceladas e ajustes manuais. */
@Service
@Transactional(readOnly = true)
public class RelatorioDoEvento {
    private final ConsultasFinanceiras consultas;

    public RelatorioDoEvento(ConsultasFinanceiras consultas) {
        this.consultas = consultas;
    }

    public List<Financas.LinhaCancelamento> cancelamentos(FiltroFinancas f) {
        return consultas.cancelamentos(f).stream()
                .map(c -> new Financas.LinhaCancelamento(
                        c.vendaId(), c.dataHora(), dinheiro(c.valor()), c.canceladaEm(), c.motivo()))
                .toList();
    }

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
