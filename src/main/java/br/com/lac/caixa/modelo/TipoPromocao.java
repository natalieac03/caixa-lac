package br.com.lac.caixa.modelo;

/** Os tipos de promoção: A_PARTIR_DE e PACOTE. */
public enum TipoPromocao {
    A_PARTIR_DE("A partir de X unidades"),
    PACOTE("Pacote fechado a cada X unidades");

    private final String rotulo;

    TipoPromocao(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
