package br.com.lac.caixa.controle;

import br.com.lac.caixa.modelo.Evento;
import br.com.lac.caixa.servico.AlertasService;
import br.com.lac.caixa.servico.EventoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Leva os avisos do sininho a todas as telas. O cálculo é preguiçoso: só roda
 * quando a página desenha o topo (respostas JSON, CSV e redirecionamentos não
 * pagam nada) e uma vez por requisição.
 */
@ControllerAdvice
public class AvisosNaTela {
    private final AlertasService alertas;
    private final EventoService eventos;

    public AvisosNaTela(AlertasService alertas, EventoService eventos) {
        this.alertas = alertas;
        this.eventos = eventos;
    }

    /** O evento em andamento para o selo do topo (ou null) e a sugestão de nome para iniciar um. */
    public static final class EventoDaTela {
        private final EventoService servico;
        private Evento aberto;
        private boolean lido;

        EventoDaTela(EventoService servico) {
            this.servico = servico;
        }

        public Evento getAberto() {
            if (!lido) {
                aberto = servico.aberto().orElse(null);
                lido = true;
            }
            return aberto;
        }

        /** "14:00" (hoje) ou "dd/MM HH:mm". */
        public String getDesde() {
            return getAberto() == null ? "" : servico.desde(getAberto());
        }

        public String getNomeSugerido() {
            return servico.nomeSugerido();
        }
    }

    @ModelAttribute("eventoDaTela")
    public EventoDaTela eventoDaTela() {
        return new EventoDaTela(eventos);
    }

    /** Calcula na primeira leitura e guarda o resultado para o resto da página. */
    public static final class Preguicoso {
        private final AlertasService servico;
        private AlertasService.Alertas valor;

        Preguicoso(AlertasService servico) {
            this.servico = servico;
        }

        public AlertasService.Alertas getAlertas() {
            if (valor == null) valor = servico.calcular();
            return valor;
        }
    }

    @ModelAttribute("avisosDaTela")
    public Preguicoso avisos(HttpServletRequest request) {
        return new Preguicoso(alertas);
    }
}
