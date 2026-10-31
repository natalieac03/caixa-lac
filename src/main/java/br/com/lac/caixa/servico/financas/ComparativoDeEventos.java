package br.com.lac.caixa.servico.financas;

import br.com.lac.caixa.consulta.FiltroFinancas;
import br.com.lac.caixa.consulta.PeriodoDoEvento;
import br.com.lac.caixa.servico.EventoService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A tela do comparativo de eventos: o resumo de cada evento e o do dia a dia. */
@Service
@Transactional(readOnly = true)
public class ComparativoDeEventos {
    private final FinancasService financas;
    private final EventoService eventos;
    private final Clock relogio;

    public ComparativoDeEventos(FinancasService financas, EventoService eventos, Clock relogio) {
        this.financas = financas;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    public List<Financas.ResumoDoEvento> eventos() {
        var periodos = eventos.periodos();
        List<Financas.ResumoDoEvento> lista = new ArrayList<>();
        for (PeriodoDoEvento p : periodos) {
            lista.add(new Financas.ResumoDoEvento(
                    p.id(), p.nome(), p.inicio(), p.fim(), financas.resumo(FiltroFinancas.doEvento(p))));
        }
        if (!periodos.isEmpty()) {
            LocalDate de = periodos.get(0).inicio().toLocalDate();
            LocalDate ate = periodos.stream()
                    .map(p -> p.fim().minusNanos(1).toLocalDate())
                    .max(Comparator.naturalOrder())
                    .orElse(de);
            var diaADia = FiltroFinancas.diaADia(de, ate, LocalDateTime.now(relogio));
            lista.add(new Financas.ResumoDoEvento(
                    null, "Dia a dia", de.atStartOfDay(), ate.plusDays(1).atStartOfDay(), financas.resumo(diaADia)));
        }
        return lista;
    }
}
