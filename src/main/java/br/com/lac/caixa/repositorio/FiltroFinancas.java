package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.FormaPagamento;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Filtro das finanças: um período fechado (datas inclusivas) OU um evento
 * (intervalo [início, fim) com hora), mais categoria de produto e forma de
 * pagamento opcionais. Canceladas nunca entram.
 *
 * <ul>
 *   <li>categoria: só os itens dessa categoria (e as vendas que têm algum);</li>
 *   <li>forma: as vendas com alguma parte nessa forma (como no histórico),
 *       com todos os seus itens.</li>
 * </ul>
 */
public record FiltroFinancas(
        LocalDate inicio, LocalDate fim, PeriodoDoEvento evento, Long categoriaId, FormaPagamento forma) {

    public FiltroFinancas {
        if (evento != null) {
            inicio = null;
            fim = null;
        } else {
            if (inicio == null || fim == null) throw new RegraDeNegocioException("Informe o período ou um evento.");
            if (inicio.isAfter(fim))
                throw new RegraDeNegocioException("A data inicial deve ser anterior ou igual à final.");
        }
    }

    public static FiltroFinancas periodo(LocalDate inicio, LocalDate fim) {
        return new FiltroFinancas(inicio, fim, null, null, null);
    }

    public static FiltroFinancas doEvento(PeriodoDoEvento evento) {
        return new FiltroFinancas(null, null, evento, null, null);
    }

    public FiltroFinancas comCategoria(Long categoria) {
        return new FiltroFinancas(inicio, fim, evento, categoria, forma);
    }

    public FiltroFinancas comForma(FormaPagamento outra) {
        return new FiltroFinancas(inicio, fim, evento, categoriaId, outra);
    }

    /** O mesmo filtro em outro período (ou evento), para comparar. */
    public FiltroFinancas noPeriodo(LocalDate de, LocalDate ate) {
        return new FiltroFinancas(de, ate, null, categoriaId, forma);
    }

    public FiltroFinancas noEvento(PeriodoDoEvento outro) {
        return new FiltroFinancas(null, null, outro, categoriaId, forma);
    }

    public LocalDateTime inicioConsulta() {
        return evento != null ? evento.inicio() : inicio.atStartOfDay();
    }

    /** Exclusivo. */
    public LocalDateTime fimConsulta() {
        return evento != null ? evento.fim() : fim.plusDays(1).atStartOfDay();
    }

    /** Dias do período: datas inclusivas; evento = duração em dias, arredondada para cima (mínimo 1). */
    public long dias() {
        if (evento == null) return ChronoUnit.DAYS.between(inicio, fim) + 1;
        long minutos = Duration.between(evento.inicio(), evento.fim()).toMinutes();
        return Math.max(1, (minutos + 1439) / 1440);
    }

    /** Série por hora quando o período tem até 2 dias (48 horas). */
    public boolean porHora() {
        return Duration.between(inicioConsulta(), fimConsulta()).compareTo(Duration.ofHours(48)) <= 0;
    }

    /** Despesas e lucro líquido não se dividem por categoria de produto nem por forma de pagamento. */
    public boolean aceitaDespesas() {
        return categoriaId == null && forma == null;
    }
}
