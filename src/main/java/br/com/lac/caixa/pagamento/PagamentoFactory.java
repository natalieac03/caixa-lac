package br.com.lac.caixa.pagamento;

import br.com.lac.caixa.modelo.FormaPagamento;

/**
 * Escolhe a estrategia de pagamento correta a partir da forma selecionada.
 * Isola o "switch" num lugar so, mantendo o resto do codigo limpo.
 */
public class PagamentoFactory {

    private PagamentoFactory() {
    }

    public static Pagamento criar(FormaPagamento forma, String descricaoOutro) {
        return switch (forma) {
            case PIX -> new PagamentoPix();
            case DEBITO -> new PagamentoDebito();
            case CREDITO -> new PagamentoCredito();
            case DINHEIRO -> new PagamentoDinheiro();
            case OUTRO -> new PagamentoOutro(descricaoOutro);
        };
    }
}
