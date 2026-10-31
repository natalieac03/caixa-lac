package br.com.lac.caixa.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** A senha da interface, vinda de APP_SENHA; sem ela a aplicação não sobe. */
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
            valor = null;
        }
        if (valor == null || valor.isBlank() || valor.codePointCount(0, valor.length()) < TAMANHO_MINIMO) {
            throw new SenhaInvalidaException();
        }
        this.senha = valor.getBytes(StandardCharsets.UTF_8);
    }

    public boolean confere(String informada) {
        if (informada == null) return false;
        return MessageDigest.isEqual(senha, informada.getBytes(StandardCharsets.UTF_8));
    }

    public static class SenhaInvalidaException extends IllegalStateException {
        public SenhaInvalidaException() {
            super(MENSAGEM);
        }
    }
}
