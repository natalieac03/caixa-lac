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
 *       com todos os seus itens;</li>
 *   <li>diaADia: dentro do período, só as vendas fora de qualquer evento (o
 *       aberto vale até {@code agora}) e as despesas sem evento.</li>
 * </ul>
 *
 * <p>Com {@code agora}, o período termina nele: um período que ainda não acabou
 * conta os dias até hoje e, na comparação, o anterior é cortado na mesma hora.
 */
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

    /** Dia a dia: o período, só fora dos eventos. */
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

    /**
     * O mesmo filtro em outro período, para comparar; o dia a dia continua dia a
     * dia. O agora anda junto ({@code recuo} dias antes), para o anterior ter o
     * mesmo recorte de horas.
     */
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

    /**
     * Exclusivo: o fim do período ou, se ele ainda não acabou, logo depois de
     * agora (1 microssegundo, a precisão do banco), para a venda feita agora entrar.
     */
    public LocalDateTime fimConsulta() {
        if (evento != null) return evento.fim();
        LocalDateTime fimDoPeriodo = fim.plusDays(1).atStartOfDay();
        if (agora == null || !agora.isBefore(fimDoPeriodo)) return fimDoPeriodo;
        LocalDateTime depoisDeAgora = agora.plus(1, ChronoUnit.MICROS);
        return depoisDeAgora.isBefore(fimDoPeriodo) ? depoisDeAgora : fimDoPeriodo;
    }

    /** Fim do período escolhido (exclusivo), sem o corte de agora: o tamanho que a tela pediu. */
    public LocalDateTime fimEscolhido() {
        return evento != null ? evento.fim() : fim.plusDays(1).atStartOfDay();
    }

    /**
     * Dias do período até agora: datas inclusivas, com o fim em min(fim, hoje);
     * evento = duração em dias, arredondada para cima. Mínimo 1.
     */
    public long dias() {
        if (evento == null) {
            LocalDate ate = agora != null && agora.toLocalDate().isBefore(fim) ? agora.toLocalDate() : fim;
            return Math.max(1, ChronoUnit.DAYS.between(inicio, ate) + 1);
        }
        long minutos = Duration.between(evento.inicio(), evento.fim()).toMinutes();
        return Math.max(1, (minutos + 1439) / 1440);
    }

    /** Despesas e lucro líquido não se dividem por categoria de produto nem por forma de pagamento. */
    public boolean aceitaDespesas() {
        return categoriaId == null && forma == null;
    }
}
