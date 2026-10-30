package br.com.lac.caixa.servico;

import br.com.lac.caixa.modelo.Venda;

/** Resultado de uma venda: a venda salva e a mensagem de confirmacao do pagamento. */
public record ResultadoVenda(Venda venda, String mensagemPagamento) {
}
