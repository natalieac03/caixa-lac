package br.com.lac.caixa.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Mensagem curta no log de inicialização quando a APP_SENHA é inválida. */
public class SenhaInvalidaFailureAnalyzer extends AbstractFailureAnalyzer<SenhaDeAcesso.SenhaInvalidaException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, SenhaDeAcesso.SenhaInvalidaException cause) {
        return new FailureAnalysis(
                "A senha de acesso está ausente, vazia ou tem menos de " + SenhaDeAcesso.TAMANHO_MINIMO
                        + " caracteres.",
                SenhaDeAcesso.MENSAGEM + ". No Railway: Variables > APP_SENHA. "
                        + "No computador: exporte APP_SENHA ou copie senha-local.properties.exemplo "
                        + "para senha-local.properties.",
                cause);
    }
}
