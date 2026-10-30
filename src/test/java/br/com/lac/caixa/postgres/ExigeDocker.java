package br.com.lac.caixa.postgres;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Testes com PostgreSQL real precisam de Docker. No computador, sem Docker,
 * eles são pulados. No CI (variável CI=true, que o GitHub Actions define),
 * falta de Docker é ERRO: esses testes não podem passar despercebidos.
 * Registrada antes da SpringExtension, roda antes de o contexto subir.
 */
public class ExigeDocker implements BeforeAllCallback {

    static boolean noCi() {
        return "true".equalsIgnoreCase(System.getenv("CI"));
    }

    @Override
    public void beforeAll(ExtensionContext contexto) {
        conferir(DockerClientFactory.instance().isDockerAvailable(), noCi(), contexto.getDisplayName());
    }

    /** Com Docker segue; sem Docker: erro no CI, pulado fora dele. */
    static void conferir(boolean dockerDisponivel, boolean ci, String teste) {
        if (dockerDisponivel) return;
        if (ci) {
            throw new IllegalStateException(
                    "CI=true sem Docker: os testes com PostgreSQL real (" + teste + ") não podem ser pulados.");
        }
        Assumptions.abort("Sem Docker: testes com PostgreSQL real pulados (no CI isso é erro).");
    }
}
