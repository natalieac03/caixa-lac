package br.com.lac.caixa.servico;

import br.com.lac.caixa.modelo.Venda;

/** A venda salva, a mensagem de confirmação e se ela já existia. */
public record ResultadoVenda(Venda venda, String mensagemPagamento, boolean repetida) {}
