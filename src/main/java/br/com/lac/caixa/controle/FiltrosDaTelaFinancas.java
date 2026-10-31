package br.com.lac.caixa.controle;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.repositorio.FiltroFinancas;
import br.com.lac.caixa.servico.EventoService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** Transforma os filtros da URL da tela de Finanças num FiltroFinancas. */
@Component
public class FiltrosDaTelaFinancas {
    static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    static final int ANOS_NO_MAXIMO = 3;

    private final EventoService eventos;
    private final Clock relogio;

    public FiltrosDaTelaFinancas(EventoService eventos, Clock relogio) {
        this.eventos = eventos;
        this.relogio = relogio;
    }

    public record Resolvido(
            String atalho,
            LocalDate inicio,
            LocalDate fim,
            Long evento,
            boolean diaADia,
            Long categoria,
            FormaPagamento forma,
            FiltroFinancas filtro,
            String periodo,
            String aviso) {

        public String consulta() {
            var b = UriComponentsBuilder.newInstance().queryParam("atalho", atalho);
            if ("personalizado".equals(atalho)) b.queryParam("inicio", inicio).queryParam("fim", fim);
            if (evento != null) b.queryParam("evento", evento);
            if (diaADia) b.queryParam("evento", SeletorDeEvento.DIA_A_DIA);
            if (categoria != null) b.queryParam("categoria", categoria);
            if (forma != null) b.queryParam("forma", forma);
            return b.build().encode().getQuery();
        }

        public String consultaCom(String outroAtalho) {
            var b = UriComponentsBuilder.newInstance().queryParam("atalho", outroAtalho);
            if (categoria != null) b.queryParam("categoria", categoria);
            if (forma != null) b.queryParam("forma", forma);
            return b.build().encode().getQuery();
        }
    }

    public Resolvido resolver(
            String atalho, String inicio, String fim, String evento, Long categoria, FormaPagamento forma) {
        LocalDate hoje = LocalDate.now(relogio);
        var sel = SeletorDeEvento.ler(evento);
        String a = atalho == null || atalho.isBlank() ? (sel.id() != null ? "evento" : "mes") : atalho.trim();
        if (sel.diaADia() && "evento".equals(a)) a = "mes";
        String aviso = null;
        Long eventoId = sel.id();
        if ("evento".equals(a) && eventoId == null) {
            var atual = eventos.atual(LocalDateTime.now(relogio));
            if (atual.isPresent()) {
                eventoId = atual.get().getId();
            } else {
                aviso = "Nenhum evento cadastrado ainda: mostrando hoje.";
                a = "hoje";
            }
        }
        if (eventoId != null) {
            var periodo = eventos.periodo(eventoId);
            var filtro = new FiltroFinancas(null, null, periodo, categoria, forma);
            String texto = periodo.nome() + " (" + periodo.inicio().format(DATA_HORA) + " a "
                    + (periodo.aberto() ? "agora, em andamento" : periodo.fim().format(DATA_HORA)) + ")";
            return new Resolvido("evento", null, null, eventoId, false, categoria, forma, filtro, texto, aviso);
        }
        LocalDate de;
        LocalDate ate;
        switch (a) {
            case "hoje" -> {
                de = hoje;
                ate = hoje;
            }
            case "7dias" -> {
                de = hoje.minusDays(6);
                ate = hoje;
            }
            case "mes" -> {
                de = hoje.withDayOfMonth(1);
                ate = hoje;
            }
            case "personalizado" -> {
                de = data(inicio, hoje);
                ate = data(fim, de);
                if (!ate.isBefore(de.plusYears(ANOS_NO_MAXIMO)))
                    throw new RegraDeNegocioException("O período pode ter no máximo " + ANOS_NO_MAXIMO
                            + " anos. Escolha um início e um fim mais próximos.");
            }
            default -> throw new RegraDeNegocioException("Atalho de período desconhecido.");
        }
        var filtro = new FiltroFinancas(de, ate, null, categoria, forma, sel.diaADia(), LocalDateTime.now(relogio));
        String texto = (de.equals(ate) ? de.format(DATA) : de.format(DATA) + " a " + ate.format(DATA))
                + (sel.diaADia() ? " · dia a dia (fora dos eventos)" : "");
        return new Resolvido(a, de, ate, null, sel.diaADia(), categoria, forma, filtro, texto, aviso);
    }

    private static LocalDate data(String texto, LocalDate padrao) {
        if (texto == null || texto.isBlank()) return padrao;
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException ex) {
            throw new RegraDeNegocioException("Data inválida.");
        }
    }
}
