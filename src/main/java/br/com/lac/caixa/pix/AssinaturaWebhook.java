package br.com.lac.caixa.pix;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Validação do x-signature dos webhooks do Mercado Pago. */
public final class AssinaturaWebhook {

    public static final Duration TOLERANCIA = Duration.ofMinutes(10);

    private AssinaturaWebhook() {}

    public static String manifesto(String dataId, String requestId, String ts) {
        StringBuilder m = new StringBuilder();
        if (dataId != null && !dataId.isBlank())
            m.append("id:").append(dataId.toLowerCase(Locale.ROOT)).append(';');
        if (requestId != null && !requestId.isBlank())
            m.append("request-id:").append(requestId).append(';');
        m.append("ts:").append(ts).append(';');
        return m.toString();
    }

    public static String hmac(String segredo, String mensagem) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(mensagem.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível", e);
        }
    }

    public static boolean valida(String segredo, String xSignature, String requestId, String dataId, Instant agora) {
        if (segredo == null || segredo.isBlank() || xSignature == null) return false;
        String ts = null, v1 = null;
        for (String parte : xSignature.split(",")) {
            String[] chaveValor = parte.split("=", 2);
            if (chaveValor.length != 2) continue;
            String chave = chaveValor[0].trim(), valor = chaveValor[1].trim();
            if (chave.equals("ts")) ts = valor;
            else if (chave.equals("v1")) v1 = valor;
        }
        if (ts == null || v1 == null || !dentroDaTolerancia(ts, agora)) return false;
        String esperado = hmac(segredo, manifesto(dataId, requestId, ts));
        return MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8),
                v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean dentroDaTolerancia(String ts, Instant agora) {
        long numero;
        try {
            numero = Long.parseLong(ts);
        } catch (NumberFormatException e) {
            return false;
        }
        Instant momento = numero > 100_000_000_000L ? Instant.ofEpochMilli(numero) : Instant.ofEpochSecond(numero);
        return Duration.between(momento, agora).abs().compareTo(TOLERANCIA) <= 0;
    }
}
