package br.com.lac.caixa.servico;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.FormaPagamento;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Filtro do histórico, do CSV e do fechamento. Campo vazio = sem filtro:
 * VendaSpecs só adiciona o predicado dos campos preenchidos.
 * Datas da tela são inclusivas; a consulta usa limite final exclusivo.
 */
public record FiltroHistorico(FormaPagamento forma, LocalDate inicio, LocalDate fim, String produto) {

    public FiltroHistorico {
        if (inicio != null && fim != null && inicio.isAfter(fim))
            throw new RegraDeNegocioException("A data inicial deve ser anterior ou igual à final.");
        produto = produto == null ? "" : produto.trim().toLowerCase(Locale.ROOT);
    }

    /** Só por intervalo de datas (fechamento). */
    public static FiltroHistorico periodo(LocalDate inicio, LocalDate fim) {
        return new FiltroHistorico(null, inicio, fim, "");
    }

    /** Início do primeiro dia, ou null sem data inicial. */
    public LocalDateTime inicioConsulta() { return inicio == null ? null : inicio.atStartOfDay(); }

    /** Início do dia seguinte ao último (exclusivo), ou null sem data final. */
    public LocalDateTime fimConsulta() { return fim == null ? null : fim.plusDays(1).atStartOfDay(); }

    public static FiltroHistorico criar(FormaPagamento forma, String inicio, String fim, String dia, String produto) {
        if ((inicio == null || inicio.isBlank()) && (fim == null || fim.isBlank()) && dia != null && !dia.isBlank()) {
            inicio = dia; fim = dia;
        }
        return new FiltroHistorico(forma, data(inicio), data(fim), produto);
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
