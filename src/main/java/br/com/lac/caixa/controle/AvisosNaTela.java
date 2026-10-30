package br.com.lac.caixa.controle;

import br.com.lac.caixa.servico.AlertasService;
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

    public AvisosNaTela(AlertasService alertas) {
        this.alertas = alertas;
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
