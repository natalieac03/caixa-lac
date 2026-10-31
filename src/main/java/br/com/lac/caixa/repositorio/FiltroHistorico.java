package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.FormaPagamento;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Filtro do histórico, do CSV e do fechamento. */
public record FiltroHistorico(
        FormaPagamento forma,
        LocalDate inicio,
        LocalDate fim,
        String produto,
        PeriodoDoEvento evento,
        boolean diaADia,
        LocalDateTime agora) {

    public FiltroHistorico {
        if (evento != null) {
            inicio = null;
            fim = null;
            diaADia = false;
        }
        if (diaADia && agora == null) throw new IllegalArgumentException("Dia a dia precisa da hora de agora.");
        if (inicio != null && fim != null && inicio.isAfter(fim))
            throw new RegraDeNegocioException("A data inicial deve ser anterior ou igual à final.");
        produto = semAcento(produto == null ? "" : produto.trim());
    }

    public FiltroHistorico(FormaPagamento forma, LocalDate inicio, LocalDate fim, String produto) {
        this(forma, inicio, fim, produto, null, false, null);
    }

    public FiltroHistorico(
            FormaPagamento forma, LocalDate inicio, LocalDate fim, String produto, PeriodoDoEvento evento) {
        this(forma, inicio, fim, produto, evento, false, null);
    }

    public FiltroHistorico comDiaADia(LocalDateTime quando) {
        return new FiltroHistorico(forma, inicio, fim, produto, null, true, quando);
    }

    public static final String COM_ACENTO = "áàâãäéèêëíìîïóòôõöúùûüçÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇ";

    public static final String SEM_ACENTO = "aaaaaeeeeiiiiooooouuuucAAAAAEEEEIIIIOOOOOUUUUC";

    public static String semAcento(String texto) {
        StringBuilder sb = new StringBuilder(texto.length());
        for (char c : texto.toCharArray()) {
            int i = COM_ACENTO.indexOf(c);
            sb.append(i >= 0 ? SEM_ACENTO.charAt(i) : c);
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    public static FiltroHistorico periodo(LocalDate inicio, LocalDate fim) {
        return new FiltroHistorico(null, inicio, fim, "");
    }

    public static FiltroHistorico doEvento(PeriodoDoEvento evento) {
        return new FiltroHistorico(null, null, null, "", evento);
    }

    public LocalDateTime inicioConsulta() {
        if (evento != null) return evento.inicio();
        return inicio == null ? null : inicio.atStartOfDay();
    }

    public LocalDateTime fimConsulta() {
        if (evento != null) return evento.fim();
        return fim == null ? null : fim.plusDays(1).atStartOfDay();
    }

    public static FiltroHistorico criar(FormaPagamento forma, String inicio, String fim, String dia, String produto) {
        return criar(forma, inicio, fim, dia, produto, null);
    }

    public static FiltroHistorico criar(
            FormaPagamento forma, String inicio, String fim, String dia, String produto, PeriodoDoEvento evento) {
        if ((inicio == null || inicio.isBlank()) && (fim == null || fim.isBlank()) && dia != null && !dia.isBlank()) {
            inicio = dia;
            fim = dia;
        }
        return new FiltroHistorico(forma, data(inicio), data(fim), produto, evento);
    }

    private static LocalDate data(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException ex) {
            throw new RegraDeNegocioException("Data inválida.");
        }
    }
}
