package br.com.lac.caixa.controle;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.servico.PagamentoInformado;
import br.com.lac.caixa.util.Dinheiro;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Lê as partes do pagamento do formulário da venda: pag_forma_N, pag_valor_N,
 * pag_descricao_N e pag_recebido_N (N = 0, 1, ...). Uma forma só, sem valor,
 * é o caso comum: paga o total.
 */
final class PagamentosDoFormulario {

    private static final String FORMA = "pag_forma_";

    private PagamentosDoFormulario() {
    }

    static List<PagamentoInformado> ler(Map<String, String> params) {
        TreeMap<Integer, String> formas = new TreeMap<>();
        params.forEach((chave, valor) -> {
            if (!chave.startsWith(FORMA)) return;
            try {
                formas.put(Integer.parseInt(chave.substring(FORMA.length())), valor);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Campo de pagamento inválido.");
            }
        });
        List<PagamentoInformado> partes = new ArrayList<>();
        formas.forEach((n, forma) -> partes.add(new PagamentoInformado(
                forma(forma),
                Dinheiro.parse(params.get("pag_valor_" + n)),
                params.get("pag_descricao_" + n),
                Dinheiro.parse(params.get("pag_recebido_" + n)))));
        return partes;
    }

    private static FormaPagamento forma(String texto) {
        if (texto == null || texto.isBlank()) throw new IllegalArgumentException("Escolha a forma de pagamento.");
        try {
            return FormaPagamento.valueOf(texto.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Forma de pagamento inválida: " + texto.trim());
        }
    }
}
