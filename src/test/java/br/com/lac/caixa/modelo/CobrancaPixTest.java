package br.com.lac.caixa.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Regras da CobrancaPix: troca de order, estorno e contestação. */
class CobrancaPixTest {
    static final LocalDateTime AGORA = LocalDateTime.of(2026, 3, 7, 14, 0);

    static CobrancaPix pendente(String valor) {
        var c = new CobrancaPix(UUID.randomUUID().toString(), 0, new BigDecimal(valor));
        c.registrarOrder(new BigDecimal(valor), "ORD1", "qr", "b64", AGORA);
        return c;
    }

    @Test
    @DisplayName("Pago com valor diferente (valorPago preenchido): não dá para gerar outra order por cima")
    void registrarOrderRecusaComValorPago() {
        var c = pendente("10");
        c.atualizar("processed", "accredited", new BigDecimal("1.00"), AGORA);
        assertEquals(StatusCobranca.PENDENTE, c.getStatus());
        var erro = assertThrows(
                RegraDeNegocioException.class,
                () -> c.registrarOrder(new BigDecimal("12"), "ORD2", "qr", "b64", AGORA));
        assertTrue(erro.getMessage().contains("valor diferente"), erro.getMessage());
        assertEquals("ORD1", c.getOrderId());
    }

    @ParameterizedTest(name = "{0} -> ESTORNADA")
    @DisplayName("Estorno e contestação no Mercado Pago: a cobrança vira ESTORNADA (nunca PAGA)")
    @ValueSource(strings = {"refunded", "charged_back"})
    void estornada(String statusMp) {
        var c = pendente("10");
        assertTrue(c.atualizar(statusMp, statusMp, new BigDecimal("10.00"), AGORA));
        assertEquals(StatusCobranca.valueOf("ESTORNADA"), c.getStatus());
    }
}
