package br.com.lac.caixa.servico;

import br.com.lac.caixa.modelo.FormaPagamento;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Datas da tela são inclusivas; a consulta usa limite final exclusivo. */
public record FiltroHistorico(FormaPagamento forma, LocalDate inicio, LocalDate fim, String produto) {
    public FiltroHistorico {
        if (inicio != null && fim != null && inicio.isAfter(fim))
            throw new IllegalArgumentException("A data inicial deve ser anterior ou igual à final.");
        produto = produto == null ? "" : produto.trim().toLowerCase(Locale.ROOT);
    }
    public LocalDateTime inicioConsulta() { return inicio == null ? null : inicio.atStartOfDay(); }
    public LocalDateTime fimConsulta() { return fim == null ? null : fim.plusDays(1).atStartOfDay(); }
    public String formaConsulta() { return forma == null ? null : forma.name(); }
    public static FiltroHistorico criar(FormaPagamento forma, String inicio, String fim, String dia, String produto) {
        if ((inicio == null || inicio.isBlank()) && (fim == null || fim.isBlank()) && dia != null && !dia.isBlank()) {
            inicio = dia; fim = dia;
        }
        return new FiltroHistorico(forma, data(inicio), data(fim), produto);
    }
    private static LocalDate data(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try { return LocalDate.parse(texto.trim()); }
        catch (DateTimeParseException ex) { throw new IllegalArgumentException("Data inválida."); }
    }
}
