package br.com.lac.caixa.dominio;

/** Lancada quando uma venda pede mais unidades do que ha no estoque. */
public class EstoqueInsuficienteException extends RegraDeNegocioException {
    public EstoqueInsuficienteException(String mensagem) {
        super(mensagem);
    }
}
