package br.com.lac.caixa.pagamento;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.PagamentoVenda;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Escolhe a estrategia de pagamento correta a partir da forma selecionada.
 * Isola o "switch" num lugar so, mantendo o resto do codigo limpo.
 */
public class PagamentoFactory {

    private PagamentoFactory() {
    }

    public static Pagamento criar(FormaPagamento forma) {
        return switch (forma) {
            case PIX -> new PagamentoPix();
            case DEBITO -> new PagamentoDebito();
            case CREDITO -> new PagamentoCredito();
            case DINHEIRO -> new PagamentoDinheiro();
            case OUTRO -> new PagamentoOutro();
        };
    }

    /**
     * Confirmação da venda, uma parte por linha:
     * "Recebido R$ 10,00 via Pix + R$ 8,00 em dinheiro (troco R$ 2,00)."
     */
    public static String mensagem(List<PagamentoVenda> partes) {
        if (partes.isEmpty()) return "Cortesia: nada a receber.";
        return "Recebido " + partes.stream()
                .map(p -> criar(p.getForma()).descrever(p))
                .collect(Collectors.joining(" + ")) + ".";
    }
}
