package br.com.lac.caixa.modelo;

/** Formas de pagamento aceitas no caixa. */
public enum FormaPagamento {
    PIX("Pix"),
    DEBITO("Débito"),
    CREDITO("Crédito"),
    DINHEIRO("Dinheiro"),
    OUTRO("Outro");

    private final String rotulo;

    FormaPagamento(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
