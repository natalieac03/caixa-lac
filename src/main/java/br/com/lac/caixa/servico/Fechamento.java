package br.com.lac.caixa.servico;

import br.com.lac.caixa.modelo.FormaPagamento;
import java.math.BigDecimal;
import java.util.List;

/**
 * Fechamento de um período, sem canceladas.
 * - formas: por forma, a soma das partes de pagamento (venda dividida entra
 *   em cada forma que usou, com o valor de cada parte);
 * - quantidade e total: pela venda (total = soma dos valores cobrados);
 * - descontos e ajustes: pela venda; ajuste = cobrado - calculado, com sinal,
 *   e vendas antigas sem cálculo contam zero.
 * A soma das formas tem que bater com o total cobrado (cortesia vale zero
 * nos dois); se não bater, a tela mostra um alerta.
 */
public record Fechamento(
        List<Linha> formas,
        long quantidade,
        BigDecimal total,
        BigDecimal totalFormas,
        BigDecimal descontos,
        BigDecimal ajustes,
        long divididas,
        long cortesias,
        BigDecimal pixMercadoPago,
        BigDecimal pixManual) {
    public Fechamento {
        formas = List.copyOf(formas);
    }

    /** vendas = quantas vendas tiveram alguma parte nesta forma. */
    public record Linha(FormaPagamento forma, long vendas, BigDecimal total) {}

    /** O Pix separado por origem soma o total da linha Pix (conferir com cada extrato). */
    public boolean isPixConfere() {
        BigDecimal pix = formas.stream()
                .filter(l -> l.forma() == FormaPagamento.PIX)
                .map(Linha::total)
                .findFirst()
                .orElse(BigDecimal.ZERO);
        return pixMercadoPago.add(pixManual).compareTo(pix) == 0;
    }

    public boolean isConfere() {
        return totalFormas.compareTo(total) == 0;
    }

    public BigDecimal getDiferenca() {
        return totalFormas.subtract(total);
    }
}
