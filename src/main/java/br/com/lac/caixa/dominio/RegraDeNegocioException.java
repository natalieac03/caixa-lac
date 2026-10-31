package br.com.lac.caixa.dominio;

/** Regra de negócio violada, com mensagem para a pessoa no caixa. */
public class RegraDeNegocioException extends RuntimeException {
    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
