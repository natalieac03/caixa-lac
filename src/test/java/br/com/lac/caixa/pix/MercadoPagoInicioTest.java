package br.com.lac.caixa.pix;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** O log de inicialização diz se o Mercado Pago está ligado, sem segredos. */
@ExtendWith(OutputCaptureExtension.class)
class MercadoPagoInicioTest {
    static final String TOKEN = "TEST-token-ficticio-0000";
    static final String SEGREDO = "segredo-ficticio-do-webhook";

    static MercadoPagoConfig config(String token, String segredo, String email) {
        var c = new MercadoPagoConfig();
        c.setAccessToken(token);
        c.setWebhookSecret(segredo);
        c.setPayerEmail(email);
        return c;
    }

    @Test
    @DisplayName("Sem MP_ACCESS_TOKEN: desligado e webhook não configurado no log")
    void semTokenLogaDesligadoEWebhookNaoConfigurado(CapturedOutput saida) {
        var c = config("", "", "");
        assertFalse(c.isLigado());
        new MercadoPagoInicio(c).avisar();
        assertTrue(saida.getOut().contains("Mercado Pago: desligado"), saida.getOut());
        assertTrue(saida.getOut().contains("webhook: assinatura não configurada"), saida.getOut());
    }

    @Test
    @DisplayName("Com token, e-mail e segredo: ligado e assinatura configurada, sem mostrar os valores")
    void comTokenEmailESegredoLogaLigadoSemMostrarValores(CapturedOutput saida) {
        var c = config(TOKEN, SEGREDO, "caixa@lac.test");
        assertTrue(c.isLigado());
        new MercadoPagoInicio(c).avisar();
        assertTrue(saida.getOut().contains("Mercado Pago: ligado"), saida.getOut());
        assertTrue(saida.getOut().contains("webhook: assinatura configurada"), saida.getOut());
        assertFalse(saida.getOut().contains(TOKEN), "o token nunca vai para o log");
        assertFalse(saida.getOut().contains(SEGREDO), "o segredo nunca vai para o log");
    }

    @Test
    @DisplayName("Token sem e-mail do pagador: continua desligado e avisa o que falta")
    void tokenSemEmail(CapturedOutput saida) {
        var c = config(TOKEN, "", "");
        assertFalse(c.isLigado());
        new MercadoPagoInicio(c).avisar();
        assertTrue(saida.getOut().contains("Mercado Pago: desligado"));
        assertTrue(saida.getOut().contains("falta MP_PAYER_EMAIL"));
        assertFalse(saida.getOut().contains(TOKEN));
    }
}
