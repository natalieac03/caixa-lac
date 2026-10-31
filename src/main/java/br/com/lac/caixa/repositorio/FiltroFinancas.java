package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.FormaPagamento;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Filtro das finanças: período ou evento, categoria, forma, dia a dia e o agora. */
public record FiltroFinancas(
        LocalDate inicio,
        LocalDate fim,
        PeriodoDoEvento evento,
        Long categoriaId,
        FormaPagamento forma,
        boolean diaADia,
        LocalDateTime agora) {

    public FiltroFinancas {
        if (evento != null) {
            inicio = null;
            fim = null;
            diaADia = false;
        } else {
            if (inicio == null || fim == null) throw new RegraDeNegocioException("Informe o período ou um evento.");
            if (inicio.isAfter(fim))
                throw new RegraDeNegocioException("A data inicial deve ser anterior ou igual à final.");
        }
        if (diaADia && agora == null) throw new IllegalArgumentException("Dia a dia precisa da hora de agora.");
    }

    public FiltroFinancas(
            LocalDate inicio, LocalDate fim, PeriodoDoEvento evento, Long categoriaId, FormaPagamento forma) {
        this(inicio, fim, evento, categoriaId, forma, false, null);
    }

    public static FiltroFinancas diaADia(LocalDate inicio, LocalDate fim, LocalDateTime agora) {
        return new FiltroFinancas(inicio, fim, null, null, null, true, agora);
    }

    public static FiltroFinancas periodo(LocalDate inicio, LocalDate fim) {
        return new FiltroFinancas(inicio, fim, null, null, null);
    }

    public static FiltroFinancas doEvento(PeriodoDoEvento evento) {
        return new FiltroFinancas(null, null, evento, null, null);
    }

    public FiltroFinancas comCategoria(Long categoria) {
        return new FiltroFinancas(inicio, fim, evento, categoria, forma, diaADia, agora);
    }

    public FiltroFinancas comForma(FormaPagamento outra) {
        return new FiltroFinancas(inicio, fim, evento, categoriaId, outra, diaADia, agora);
    }

    public FiltroFinancas noPeriodo(LocalDate de, LocalDate ate, long recuo) {
        return new FiltroFinancas(
                de, ate, null, categoriaId, forma, diaADia, agora == null ? null : agora.minusDays(recuo));
    }

    public FiltroFinancas noEvento(PeriodoDoEvento outro) {
        return new FiltroFinancas(null, null, outro, categoriaId, forma, false, null);
    }

    public LocalDateTime inicioConsulta() {
        return evento != null ? evento.inicio() : inicio.atStartOfDay();
    }

    public LocalDateTime fimConsulta() {
        if (evento != null) return evento.fim();
        LocalDateTime fimDoPeriodo = fim.plusDays(1).atStartOfDay();
        if (agora == null || !agora.isBefore(fimDoPeriodo)) return fimDoPeriodo;
        LocalDateTime depoisDeAgora = agora.plus(1, ChronoUnit.MICROS);
        return depoisDeAgora.isBefore(fimDoPeriodo) ? depoisDeAgora : fimDoPeriodo;
    }

    public LocalDateTime fimEscolhido() {
        return evento != null ? evento.fim() : fim.plusDays(1).atStartOfDay();
    }

    public long dias() {
        if (evento == null) {
            LocalDate ate = agora != null && agora.toLocalDate().isBefore(fim) ? agora.toLocalDate() : fim;
            return Math.max(1, ChronoUnit.DAYS.between(inicio, ate) + 1);
        }
        long minutos = Duration.between(evento.inicio(), evento.fim()).toMinutes();
        return Math.max(1, (minutos + 1439) / 1440);
    }

    public boolean aceitaDespesas() {
        return categoriaId == null && forma == null;
    }
}
