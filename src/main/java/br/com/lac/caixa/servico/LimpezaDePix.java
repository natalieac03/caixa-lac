package br.com.lac.caixa.servico;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** A cada 5 minutos, encerra no Mercado Pago as cobranças pendentes abandonadas. */
@Component
public class LimpezaDePix {

    private static final Logger LOG = LoggerFactory.getLogger(LimpezaDePix.class);

    private final CobrancaPixService cobrancas;

    public LimpezaDePix(CobrancaPixService cobrancas) {
        this.cobrancas = cobrancas;
    }

    @Scheduled(
            fixedDelayString = "${mercadopago.limpeza-intervalo:PT5M}",
            initialDelayString = "${mercadopago.limpeza-intervalo:PT5M}")
    public void limpar() {
        if (!cobrancas.ligado()) return;
        for (Long id : cobrancas.abandonadas()) {
            try {
                cobrancas.encerrarSeAbandonada(id);
            } catch (RuntimeException e) {
                LOG.warn("Limpeza de Pix: cobrança {} fica para a próxima rodada ({})", id, e.getMessage());
            }
        }
    }
}
