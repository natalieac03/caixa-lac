package br.com.lac.caixa.servico.financas;

import static br.com.lac.caixa.servico.financas.Contas.dinheiro;

import br.com.lac.caixa.consulta.FiltroFinancas;
import br.com.lac.caixa.repositorio.ConsultasFinanceiras;
import br.com.lac.caixa.repositorio.ConsultasFinanceiras.Balde;
import br.com.lac.caixa.servico.financas.Financas.Celula;
import br.com.lac.caixa.servico.financas.Financas.Granularidade;
import br.com.lac.caixa.servico.financas.Financas.Ponto;
import br.com.lac.caixa.servico.financas.Financas.Serie;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A série de receita e lucro por hora, dia, semana ou mês, e o mapa de calor por dia da semana e hora. */
@Service
@Transactional(readOnly = true)
public class SerieFinanceira {
    private final ConsultasFinanceiras consultas;

    public SerieFinanceira(ConsultasFinanceiras consultas) {
        this.consultas = consultas;
    }

    public List<Balde> baldes(FiltroFinancas f) {
        return consultas.porDiaEHora(f);
    }

    static Granularidade granularidade(FiltroFinancas f) {
        LocalDateTime de = f.inicioConsulta();
        LocalDateTime ate = f.fimEscolhido();
        if (Duration.between(de, ate).compareTo(Duration.ofHours(48)) <= 0) return Granularidade.HORA;
        if (!ate.isAfter(de.plusDays(90))) return Granularidade.DIA;
        if (!ate.isAfter(de.plusYears(1))) return Granularidade.SEMANA;
        return Granularidade.MES;
    }

    private static LocalDateTime inicioDoPonto(Granularidade g, LocalDateTime momento, LocalDate inicioDoPeriodo) {
        LocalDate dia = momento.toLocalDate();
        LocalDate comeco = switch (g) {
            case HORA -> null;
            case DIA -> dia;
            case SEMANA -> dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> dia.withDayOfMonth(1);
        };
        if (comeco == null) return momento.truncatedTo(ChronoUnit.HOURS);
        return (comeco.isBefore(inicioDoPeriodo) ? inicioDoPeriodo : comeco).atStartOfDay();
    }

    private static LocalDateTime proximoPonto(Granularidade g, LocalDateTime cursor) {
        return switch (g) {
            case HORA -> cursor.plusHours(1);
            case DIA -> cursor.plusDays(1);
            case SEMANA ->
                cursor.toLocalDate()
                        .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                        .atStartOfDay();
            case MES -> cursor.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay();
        };
    }

    public Serie serie(FiltroFinancas f) {
        return serie(f, baldes(f));
    }

    public Serie serie(FiltroFinancas f, List<Balde> baldes) {
        Granularidade g = granularidade(f);
        LocalDate inicioDoPeriodo = f.inicioConsulta().toLocalDate();
        Map<LocalDateTime, BigDecimal[]> somas = new TreeMap<>();
        Map<LocalDateTime, Long> vendas = new TreeMap<>();
        for (Balde b : baldes) {
            LocalDateTime chave = inicioDoPonto(g, b.dia().atTime(b.hora(), 0), inicioDoPeriodo);
            somas.merge(chave, new BigDecimal[] {b.liquido(), b.cmv()}, (x, y) ->
                    new BigDecimal[] {x[0].add(y[0]), x[1].add(y[1])});
            vendas.merge(chave, b.vendas(), Long::sum);
        }
        List<Ponto> pontos = new ArrayList<>();
        LocalDateTime cursor = inicioDoPonto(g, f.inicioConsulta(), inicioDoPeriodo);
        while (cursor.isBefore(f.fimConsulta())) {
            BigDecimal[] s = somas.getOrDefault(cursor, new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal liquido = dinheiro(s[0]);
            pontos.add(new Ponto(cursor, liquido, liquido.subtract(dinheiro(s[1])), vendas.getOrDefault(cursor, 0L)));
            cursor = proximoPonto(g, cursor);
        }
        return new Serie(g, pontos);
    }

    public List<Celula> mapaDeCalor(FiltroFinancas f) {
        return mapaDeCalor(baldes(f));
    }

    public List<Celula> mapaDeCalor(List<Balde> baldes) {
        Map<DayOfWeek, long[]> vendas = new EnumMap<>(DayOfWeek.class);
        Map<DayOfWeek, BigDecimal[]> receita = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) {
            vendas.put(d, new long[24]);
            BigDecimal[] zeros = new BigDecimal[24];
            Arrays.fill(zeros, BigDecimal.ZERO);
            receita.put(d, zeros);
        }
        for (Balde b : baldes) {
            DayOfWeek d = b.dia().getDayOfWeek();
            vendas.get(d)[b.hora()] += b.vendas();
            receita.get(d)[b.hora()] = receita.get(d)[b.hora()].add(b.liquido());
        }
        List<Celula> celulas = new ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            for (int h = 0; h < 24; h++) celulas.add(new Celula(d, h, vendas.get(d)[h], dinheiro(receita.get(d)[h])));
        }
        return celulas;
    }
}
