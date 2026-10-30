package br.com.lac.caixa.dominio;

/**
 * Regra de negócio violada (ex.: preço zero, soma do pagamento errada). A
 * mensagem é para a pessoa no caixa: ErrosDeNegocio mostra o texto na tela.
 * Outras exceções (IllegalArgumentException etc.) são erro de programa e viram
 * "Algo deu errado", com a pilha no log.
 */
public class RegraDeNegocioException extends RuntimeException {
    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
