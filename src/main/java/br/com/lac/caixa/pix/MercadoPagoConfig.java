package br.com.lac.caixa.pix;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do Mercado Pago, só por variável de ambiente (application.properties
 * liga cada campo a MP_ACCESS_TOKEN, MP_WEBHOOK_SECRET e MP_PAYER_EMAIL). Sem
 * token (ou sem e-mail do pagador), o Pix automático fica desligado e o caixa usa
 * Pix manual. toString nunca mostra os segredos.
 */
@ConfigurationProperties(prefix = "mercadopago")
public class MercadoPagoConfig {

    private String accessToken = "";
    private String webhookSecret = "";
    private String payerEmail = "";
    private String url = "https://api.mercadopago.com";
    private Duration timeout = Duration.ofSeconds(5);
    /** Validade do Pix (ISO 8601). A documentação exige no mínimo 30 minutos. */
    private String expiracao = "PT30M";
    /** Espera máxima ao respeitar o Retry-After de um 429. */
    private Duration esperaMaximaRetry = Duration.ofSeconds(5);

    public boolean isLigado() {
        return !accessToken.isBlank() && !payerEmail.isBlank();
    }

    public boolean isWebhookConfigurado() {
        return !webhookSecret.isBlank();
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken == null ? "" : accessToken.trim();
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
    }

    public String getPayerEmail() {
        return payerEmail;
    }

    public void setPayerEmail(String payerEmail) {
        this.payerEmail = payerEmail == null ? "" : payerEmail.trim();
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public String getExpiracao() {
        return expiracao;
    }

    public void setExpiracao(String expiracao) {
        this.expiracao = expiracao;
    }

    public Duration getEsperaMaximaRetry() {
        return esperaMaximaRetry;
    }

    public void setEsperaMaximaRetry(Duration esperaMaximaRetry) {
        this.esperaMaximaRetry = esperaMaximaRetry;
    }

    @Override
    public String toString() {
        return "MercadoPagoConfig[ligado=" + isLigado() + ", webhook=" + isWebhookConfigurado() + ", url=" + url + "]";
    }
}
