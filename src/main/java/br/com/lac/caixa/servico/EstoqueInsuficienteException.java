package br.com.lac.caixa.servico;

/** Lancada quando uma venda pede mais unidades do que ha no estoque. */
public class EstoqueInsuficienteException extends RegraDeNegocioException {
    public EstoqueInsuficienteException(String mensagem) {
        super(mensagem);
    }
}
