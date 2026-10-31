package br.com.lac.caixa.controle;

import br.com.lac.caixa.pix.AssinaturaWebhook;
import br.com.lac.caixa.pix.MercadoPagoConfig;
import br.com.lac.caixa.servico.CobrancaPixService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Webhook do Mercado Pago: confere a assinatura e atualiza a cobrança em segundo plano. */
@RestController
public class WebhookController {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookController.class);

    private final MercadoPagoConfig config;
    private final CobrancaPixService cobrancas;
    private final Executor avisos;
    private final Clock relogio;
    private final ObjectMapper json = new ObjectMapper();

    public WebhookController(
            MercadoPagoConfig config,
            CobrancaPixService cobrancas,
            @Qualifier("avisosMercadoPago") Executor avisos,
            Clock relogio) {
        this.config = config;
        this.cobrancas = cobrancas;
        this.avisos = avisos;
        this.relogio = relogio;
    }

    @PostMapping("/webhooks/mercadopago")
    public ResponseEntity<Void> receber(
            @RequestHeader(name = "x-signature", required = false) String assinatura,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestParam(name = "type", required = false) String tipo,
            @RequestBody(required = false) String corpo) {
        if (dataId == null || dataId.isBlank()) {
            LOG.warn("Webhook do Mercado Pago recusado: sem data.id na URL.");
            return ResponseEntity.badRequest().build();
        }
        if (!AssinaturaWebhook.valida(config.getWebhookSecret(), assinatura, requestId, dataId, relogio.instant())) {
            LOG.warn("Webhook do Mercado Pago recusado: assinatura inválida ou fora da tolerância.");
            return ResponseEntity.status(401).build();
        }
        String orderId = dataId;
        String tipoDoAviso = tipo != null ? tipo : tipoDoCorpo(corpo);
        if (tipoDoAviso == null || "order".equals(tipoDoAviso)) {
            avisos.execute(() -> {
                try {
                    cobrancas.atualizarPorOrder(orderId);
                } catch (RuntimeException e) {
                    LOG.warn("Webhook: não foi possível atualizar a order {}: {}", orderId, e.getMessage());
                }
            });
        }
        return ResponseEntity.ok().build();
    }

    private JsonNode ler(String corpo) {
        try {
            return corpo == null || corpo.isBlank() ? null : json.readTree(corpo);
        } catch (Exception e) {
            return null;
        }
    }

    private String tipoDoCorpo(String corpo) {
        JsonNode no = ler(corpo);
        return no == null || no.path("type").isMissingNode()
                ? null
                : no.path("type").asText();
    }
}
