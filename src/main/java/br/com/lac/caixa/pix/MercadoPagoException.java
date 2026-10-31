package br.com.lac.caixa.pix;

/** Falha ao falar com o Mercado Pago. */
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
