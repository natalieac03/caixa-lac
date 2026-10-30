package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.FormaPagamento;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Filtro do histórico, do CSV e do fechamento. Campo vazio = sem filtro:
 * VendaSpecs só adiciona o predicado dos campos preenchidos. O produto é
 * buscado sem diferenciar maiúsculas nem acentos ("boton" acha "Bóton").
 * Datas da tela são inclusivas; a consulta usa limite final exclusivo.
 * Evento é um atalho de período: com ele, vale o intervalo [inicio, fim) do
 * evento (com hora) e as datas ficam vazias.
 */
public record FiltroHistorico(
        FormaPagamento forma, LocalDate inicio, LocalDate fim, String produto, PeriodoDoEvento evento) {

    public FiltroHistorico {
        if (evento != null) {
            inicio = null;
            fim = null;
        }
        if (inicio != null && fim != null && inicio.isAfter(fim))
            throw new RegraDeNegocioException("A data inicial deve ser anterior ou igual à final.");
        produto = semAcento(produto == null ? "" : produto.trim());
    }

    public FiltroHistorico(FormaPagamento forma, LocalDate inicio, LocalDate fim, String produto) {
        this(forma, inicio, fim, produto, null);
    }

    /**
     * Letras acentuadas e suas versões sem acento, na mesma posição. O banco faz
     * lower(translate(nome, COM_ACENTO, SEM_ACENTO)) (H2 e Postgres, sem extensão);
     * o termo buscado passa pelo mesmo caminho aqui.
     */
    public static final String COM_ACENTO = "áàâãäéèêëíìîïóòôõöúùûüçÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇ";

    public static final String SEM_ACENTO = "aaaaaeeeeiiiiooooouuuucAAAAAEEEEIIIIOOOOOUUUUC";

    /** "Bóton" -> "boton". */
    public static String semAcento(String texto) {
        StringBuilder sb = new StringBuilder(texto.length());
        for (char c : texto.toCharArray()) {
            int i = COM_ACENTO.indexOf(c);
            sb.append(i >= 0 ? SEM_ACENTO.charAt(i) : c);
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /** Só por intervalo de datas (fechamento). */
    public static FiltroHistorico periodo(LocalDate inicio, LocalDate fim) {
        return new FiltroHistorico(null, inicio, fim, "");
    }

    /** Só pelo evento (fechamento). */
    public static FiltroHistorico doEvento(PeriodoDoEvento evento) {
        return new FiltroHistorico(null, null, null, "", evento);
    }

    /** Início do evento, ou do primeiro dia, ou null sem data inicial. */
    public LocalDateTime inicioConsulta() {
        if (evento != null) return evento.inicio();
        return inicio == null ? null : inicio.atStartOfDay();
    }

    /** Fim do evento, ou início do dia seguinte ao último (exclusivo), ou null sem data final. */
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
