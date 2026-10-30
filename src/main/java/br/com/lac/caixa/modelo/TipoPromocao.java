package br.com.lac.caixa.modelo;

/**
 * Como a promoção é aplicada:
 * - A_PARTIR_DE: atingiu o mínimo, TODAS as unidades saem pelo preço promocional.
 *   Ex.: a partir de 2 un., cada uma por R$ 5 -> 3 un. = 15.
 * - PACOTE: a cada X unidades cobra-se o preço fechado do pacote; o que
 *   passar sai no preço normal. Ex.: bóton R$ 6, pacote de 2 por R$ 10
 *   -> 3 un. = 10 + 6 = 16.
 */
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
