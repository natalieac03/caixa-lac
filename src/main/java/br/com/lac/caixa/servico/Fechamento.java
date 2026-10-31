package br.com.lac.caixa.servico;

import br.com.lac.caixa.modelo.FormaPagamento;
import java.math.BigDecimal;
import java.util.List;

/** Resultado do fechamento de um período: por forma, totais, descontos e ajustes. */
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

    public record Linha(FormaPagamento forma, long vendas, BigDecimal total) {}

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
