package br.com.lac.caixa.controle;

import br.com.lac.caixa.dominio.RegraDeNegocioException;

/**
 * O seletor "Evento" dos filtros (histórico, fechamento, finanças): vazio =
 * Todos, "dia-a-dia" = Dia a dia (vendas fora de qualquer evento, dentro das
 * datas), ou o id de um evento (vale como período).
 */
public record SeletorDeEvento(String valor, Long id, boolean diaADia) {
    public static final String DIA_A_DIA = "dia-a-dia";

    public static SeletorDeEvento ler(String texto) {
        if (texto == null || texto.isBlank()) return new SeletorDeEvento("", null, false);
        String t = texto.trim();
        if (DIA_A_DIA.equals(t)) return new SeletorDeEvento(DIA_A_DIA, null, true);
        try {
            long id = Long.parseLong(t);
            return new SeletorDeEvento(t, id, false);
        } catch (NumberFormatException e) {
            throw new RegraDeNegocioException("Evento inválido no filtro.");
        }
    }
}
