package br.com.lac.caixa.postgres;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/** Pula os testes de Postgres sem Docker no computador; no CI, falta de Docker é erro. */
public class ExigeDocker implements BeforeAllCallback {

    static boolean noCi() {
        return "true".equalsIgnoreCase(System.getenv("CI"));
    }

    @Override
    public void beforeAll(ExtensionContext contexto) {
        conferir(DockerClientFactory.instance().isDockerAvailable(), noCi(), contexto.getDisplayName());
    }

    static void conferir(boolean dockerDisponivel, boolean ci, String teste) {
        if (dockerDisponivel) return;
        if (ci) {
            throw new IllegalStateException(
                    "CI=true sem Docker: os testes com PostgreSQL real (" + teste + ") não podem ser pulados.");
        }
        Assumptions.abort("Sem Docker: testes com PostgreSQL real pulados (no CI isso é erro).");
    }
}
