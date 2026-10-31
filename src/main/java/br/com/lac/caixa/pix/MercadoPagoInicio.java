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

    /**
     * Duas linhas fixas no log ao subir, sem nenhum segredo (dá para conferir no
     * Railway depois do deploy): "Mercado Pago: ligado|desligado" e
     * "webhook: assinatura configurada|não configurada".
     */
    @EventListener(ApplicationReadyEvent.class)
    void avisar() {
        LOG.info("Mercado Pago: {}", config.isLigado() ? "ligado" : "desligado");
        LOG.info("webhook: assinatura {}", config.isWebhookConfigurado() ? "configurada" : "não configurada");
        if (!config.isLigado() && !config.getAccessToken().isBlank()) {
            LOG.warn(
                    "Mercado Pago desligado: MP_ACCESS_TOKEN definido, mas falta MP_PAYER_EMAIL. O caixa usa Pix manual.");
        } else if (!config.isLigado()) {
            LOG.info("Sem MP_ACCESS_TOKEN: o caixa usa Pix manual.");
        } else if (!config.isWebhookConfigurado()) {
            LOG.warn(
                    "Sem MP_WEBHOOK_SECRET: os avisos do Mercado Pago serão recusados (a tela ainda consulta a cobrança).");
        }
    }
}
