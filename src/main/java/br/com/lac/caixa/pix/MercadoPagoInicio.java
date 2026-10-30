package br.com.lac.caixa.pix;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/** Liga as propriedades do Mercado Pago e diz no log, sem segredos, se o Pix automático está ligado. */
@Configuration
@EnableConfigurationProperties(MercadoPagoConfig.class)
class MercadoPagoInicio {

    private static final Logger LOG = LoggerFactory.getLogger(MercadoPagoInicio.class);

    private final MercadoPagoConfig config;

    MercadoPagoInicio(MercadoPagoConfig config) {
        this.config = config;
    }

    @EventListener(ApplicationReadyEvent.class)
    void avisar() {
        if (config.isLigado()) {
            LOG.info(
                    "Pix automático (Mercado Pago) ligado. Webhook {}.",
                    config.isWebhookConfigurado()
                            ? "com assinatura configurada"
                            : "sem MP_WEBHOOK_SECRET: avisos serão recusados");
        } else if (!config.getAccessToken().isBlank()) {
            LOG.warn(
                    "Pix automático desligado: MP_ACCESS_TOKEN definido, mas falta MP_PAYER_EMAIL. O caixa usa Pix manual.");
        } else {
            LOG.info("Pix automático desligado (sem MP_ACCESS_TOKEN). O caixa usa Pix manual.");
        }
    }
}
