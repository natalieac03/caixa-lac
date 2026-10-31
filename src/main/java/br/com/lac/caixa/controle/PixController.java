package br.com.lac.caixa.controle;

import br.com.lac.caixa.modelo.CobrancaPix;
import br.com.lac.caixa.servico.CobrancaPixService;
import br.com.lac.caixa.util.Dinheiro;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Pix automático na tela de venda: gerar, consultar e cancelar a cobrança (JSON). */
@RestController
public class PixController {

    public record PixDaParte(
            int parte,
            String status,
            BigDecimal valor,
            BigDecimal valorPago,
            String qrCode,
            String qrCodeBase64,
            String orderId) {

        static PixDaParte de(CobrancaPix c, boolean comQr) {
            return new PixDaParte(
                    c.getParte(),
                    c.getStatus().name(),
                    c.getValor(),
                    c.getValorPago(),
                    comQr ? c.getQrCode() : null,
                    comQr ? c.getQrCodeBase64() : null,
                    c.getOrderId());
        }
    }

    private final CobrancaPixService cobrancas;

    public PixController(CobrancaPixService cobrancas) {
        this.cobrancas = cobrancas;
    }

    @PostMapping("/venda/pix")
    public PixDaParte gerar(@RequestParam String token, @RequestParam int parte, @RequestParam String valor) {
        BigDecimal quantia = Dinheiro.parse(valor);
        return PixDaParte.de(cobrancas.gerar(token, parte, quantia), true);
    }

    @GetMapping("/venda/pix/{token}/{parte}")
    public PixDaParte situacao(@PathVariable String token, @PathVariable int parte) {
        return PixDaParte.de(cobrancas.consultar(token, parte), false);
    }

    @PostMapping("/venda/pix/cancelar")
    public PixDaParte cancelar(@RequestParam String token, @RequestParam int parte) {
        return PixDaParte.de(cobrancas.cancelar(token, parte), false);
    }
}
