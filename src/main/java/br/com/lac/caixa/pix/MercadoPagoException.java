package br.com.lac.caixa.pix;

/**
 * Falha ao falar com o Mercado Pago (erro HTTP, 429 repetido, timeout). A
 * mensagem nunca contém o token. status = código HTTP (0 sem resposta) e
 * codigo = o "code" do erro do Mercado Pago, quando veio (ex.: cannot_cancel_order).
 */
public class MercadoPagoException extends RuntimeException {

    private final int status;
    private final String codigo;

    public MercadoPagoException(String mensagem, int status, String codigo, Throwable causa) {
        super(mensagem, causa);
        this.status = status;
        this.codigo = codigo;
    }

    public int getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }
}
