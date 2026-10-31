package br.com.lac.caixa.dominio;

/** Venda que pede mais unidades do que há no estoque. */
public class EstoqueInsuficienteException extends RegraDeNegocioException {
    public EstoqueInsuficienteException(String mensagem) {
        super(mensagem);
    }
}
