package br.com.lac.caixa.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * A senha da interface (app.senha = ${APP_SENHA}). Só existe em variável de
 * ambiente: sem ela, vazia ou curta demais, a aplicação não sobe.
 * O valor nunca aparece em log nem em mensagem de erro.
 */
@Component
public class SenhaDeAcesso {

    public static final int TAMANHO_MINIMO = 8;
    public static final String MENSAGEM = "Defina a variável APP_SENHA (mínimo " + TAMANHO_MINIMO + " caracteres)";

    private final byte[] senha;

    public SenhaDeAcesso(Environment ambiente) {
        String valor;
        try {
            valor = ambiente.getProperty("app.senha");
        } catch (IllegalArgumentException semVariavel) {
            // "${APP_SENHA}" sem a variável definida.
            valor = null;
        }
        if (valor == null || valor.isBlank() || valor.codePointCount(0, valor.length()) < TAMANHO_MINIMO) {
            throw new SenhaInvalidaException();
        }
        this.senha = valor.getBytes(StandardCharsets.UTF_8);
    }

    /** Comparação em tempo constante. */
    public boolean confere(String informada) {
        if (informada == null) return false;
        return MessageDigest.isEqual(senha, informada.getBytes(StandardCharsets.UTF_8));
    }

    /** Falha de inicialização com mensagem própria (veja SenhaInvalidaFailureAnalyzer). */
    public static class SenhaInvalidaException extends IllegalStateException {
        public SenhaInvalidaException() {
            super(MENSAGEM);
        }
    }
}
