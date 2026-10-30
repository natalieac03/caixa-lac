package br.com.lac.caixa.pix;

import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga as propriedades do Mercado Pago e diz no log, sem segredos, se o Pix automático está ligado. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(MercadoPagoConfig.class)
class MercadoPagoInicio {

    private static final Logger LOG = LoggerFactory.getLogger(MercadoPagoInicio.class);

    private final MercadoPagoConfig config;

    MercadoPagoInicio(MercadoPagoConfig config) {
        this.config = config;
    }

    /**
     * Onde os avisos do webhook são processados: em segundo plano, para responder
     * 200 na hora (o Mercado Pago espera até 22 s). Nos testes, na mesma thread.
     */
    @Bean("avisosMercadoPago")
    Executor avisosMercadoPago(@Value("${mercadopago.webhook-sincrono:false}") boolean sincrono) {
        if (sincrono) return Runnable::run;
        var executor = new SimpleAsyncTaskExecutor("aviso-mp-");
        executor.setConcurrencyLimit(4);
        return executor;
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
