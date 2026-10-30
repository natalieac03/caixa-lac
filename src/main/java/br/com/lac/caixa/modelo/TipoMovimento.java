package br.com.lac.caixa.modelo;

/** Por que o estoque mudou. Entradas e devoluções somam; saídas subtraem. */
public enum TipoMovimento {
    ENTRADA_COMPRA(1),
    ENTRADA_AJUSTE(1),
    SAIDA_VENDA(-1),
    SAIDA_AJUSTE(-1),
    DEVOLUCAO_CANCELAMENTO(1);

    private final int sinal;

    TipoMovimento(int sinal) {
        this.sinal = sinal;
    }

    public int getSinal() {
        return sinal;
    }
}
