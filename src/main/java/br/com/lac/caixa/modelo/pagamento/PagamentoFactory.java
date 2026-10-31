package br.com.lac.caixa.modelo.pagamento;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.PagamentoVenda;
import java.util.List;
import java.util.stream.Collectors;

/** Escolhe a estratégia de pagamento pela forma e monta a mensagem de confirmação. */
public class PagamentoFactory {

    private PagamentoFactory() {}

    public static Pagamento criar(FormaPagamento forma) {
        return switch (forma) {
            case PIX -> new PagamentoPix();
            case DEBITO -> new PagamentoDebito();
            case CREDITO -> new PagamentoCredito();
            case DINHEIRO -> new PagamentoDinheiro();
            case OUTRO -> new PagamentoOutro();
        };
    }

    public static String mensagem(List<PagamentoVenda> partes) {
        if (partes.isEmpty()) return "Cortesia: nada a receber.";
        return "Recebido "
                + partes.stream().map(p -> criar(p.getForma()).descrever(p)).collect(Collectors.joining(" + ")) + ".";
    }
}
