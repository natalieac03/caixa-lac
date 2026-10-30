package br.com.lac.caixa.servico;

import br.com.lac.caixa.modelo.FormaPagamento;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Datas da tela são inclusivas; a consulta usa limite final exclusivo.
 *
 * Os parâmetros da consulta nunca são null: no Postgres o Hibernate envia
 * null sem tipo (bytea), e "cast(:inicio as timestamp) is null" quebra. Sem
 * data, valem os limites abaixo; sem forma, valem todas as formas.
 */
public record FiltroHistorico(FormaPagamento forma, LocalDate inicio, LocalDate fim, String produto) {

    static final LocalDateTime SEM_INICIO = LocalDateTime.of(1900, 1, 1, 0, 0);
    static final LocalDateTime SEM_FIM = LocalDateTime.of(9999, 1, 1, 0, 0);

    public FiltroHistorico {
        if (inicio != null && fim != null && inicio.isAfter(fim))
            throw new IllegalArgumentException("A data inicial deve ser anterior ou igual à final.");
        produto = produto == null ? "" : produto.trim().toLowerCase(Locale.ROOT);
    }

    public LocalDateTime inicioConsulta() { return inicio == null ? SEM_INICIO : inicio.atStartOfDay(); }
    public LocalDateTime fimConsulta() { return fim == null ? SEM_FIM : fim.plusDays(1).atStartOfDay(); }

    public List<FormaPagamento> formas() {
        return forma == null ? List.of(FormaPagamento.values()) : List.of(forma);
    }

    /** Mesmas formas, como texto, para a consulta nativa. */
    public List<String> formasConsulta() {
        return formas().stream().map(Enum::name).toList();
    }

    public static FiltroHistorico criar(FormaPagamento forma, String inicio, String fim, String dia, String produto) {
        if ((inicio == null || inicio.isBlank()) && (fim == null || fim.isBlank()) && dia != null && !dia.isBlank()) {
            inicio = dia; fim = dia;
        }
        return new FiltroHistorico(forma, data(inicio), data(fim), produto);
    }

    private static LocalDate data(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            LocalDate data = LocalDate.parse(texto.trim());
            if (data.isBefore(SEM_INICIO.toLocalDate()) || !data.isBefore(SEM_FIM.toLocalDate()))
                throw new DateTimeException("fora do intervalo");
            return data;
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Data inválida.");
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Data fora do intervalo aceito (1900 a 9998).");
        }
    }
}
