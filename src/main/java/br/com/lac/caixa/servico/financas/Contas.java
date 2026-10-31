package br.com.lac.caixa.servico.financas;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** As contas comuns dos serviços de finanças: centavos, percentuais e razões, sempre HALF_EVEN. */
final class Contas {
    static final BigDecimal CEM = BigDecimal.valueOf(100);

    private Contas() {}

    static BigDecimal dinheiro(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2, RoundingMode.HALF_EVEN);
    }

    /** parte / todo x 100, 2 casas; null se o todo é zero ou ausente. */
    static BigDecimal percentual(BigDecimal parte, BigDecimal todo) {
        if (parte == null || todo == null || todo.signum() == 0) return null;
        return parte.multiply(CEM).divide(todo, 2, RoundingMode.HALF_EVEN);
    }

    static BigDecimal razao(BigDecimal a, long b) {
        return b == 0 ? null : a.divide(BigDecimal.valueOf(b), 2, RoundingMode.HALF_EVEN);
    }
}
