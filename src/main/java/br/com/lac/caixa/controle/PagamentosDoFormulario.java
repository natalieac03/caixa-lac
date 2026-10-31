package br.com.lac.caixa.controle;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.OrigemPagamento;
import br.com.lac.caixa.servico.PagamentoInformado;
import br.com.lac.caixa.util.Dinheiro;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Lê as partes do pagamento do formulário da venda. */
final class PagamentosDoFormulario {

    private static final String FORMA = "pag_forma_";

    private PagamentosDoFormulario() {}

    static List<PagamentoInformado> ler(Map<String, String> params) {
        TreeMap<Integer, String> formas = new TreeMap<>();
        params.forEach((chave, valor) -> {
            if (!chave.startsWith(FORMA)) return;
            try {
                formas.put(Integer.parseInt(chave.substring(FORMA.length())), valor);
            } catch (NumberFormatException ex) {
                throw new RegraDeNegocioException("Campo de pagamento inválido.");
            }
        });
        List<PagamentoInformado> partes = new ArrayList<>();
        formas.forEach((n, forma) -> partes.add(new PagamentoInformado(
                forma(forma),
                Dinheiro.parse(params.get("pag_valor_" + n)),
                params.get("pag_descricao_" + n),
                Dinheiro.parse(params.get("pag_recebido_" + n)),
                origem(params.get("pag_origem_" + n)),
                parte(params.get("pag_parte_" + n), n))));
        return partes;
    }

    private static OrigemPagamento origem(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            return OrigemPagamento.valueOf(texto.trim());
        } catch (IllegalArgumentException ex) {
            throw new RegraDeNegocioException("Origem de pagamento inválida: " + texto.trim());
        }
    }

    private static int parte(String texto, int padrao) {
        if (texto == null || texto.isBlank()) return padrao;
        try {
            int parte = Integer.parseInt(texto.trim());
            if (parte < 0) throw new NumberFormatException();
            return parte;
        } catch (NumberFormatException ex) {
            throw new RegraDeNegocioException("Parte do pagamento inválida: " + texto.trim());
        }
    }

    private static FormaPagamento forma(String texto) {
        if (texto == null || texto.isBlank()) throw new RegraDeNegocioException("Escolha a forma de pagamento.");
        try {
            return FormaPagamento.valueOf(texto.trim());
        } catch (IllegalArgumentException ex) {
            throw new RegraDeNegocioException("Forma de pagamento inválida: " + texto.trim());
        }
    }
}
