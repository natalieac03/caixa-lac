package br.com.lac.caixa.postgres;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

class ExigeDockerTest {
    @Test
    void semDockerNoCiEErro() {
        var erro = assertThrows(
                IllegalStateException.class, () -> ExigeDocker.conferir(false, true, "PostgresLegadoTest"));
        assertTrue(erro.getMessage().contains("não podem ser pulados"));
    }

    @Test
    void semDockerForaDoCiPula() {
        assertThrows(TestAbortedException.class, () -> ExigeDocker.conferir(false, false, "PostgresLegadoTest"));
    }

    @Test
    void comDockerSegue() {
        assertDoesNotThrow(() -> ExigeDocker.conferir(true, true, "x"));
        assertDoesNotThrow(() -> ExigeDocker.conferir(true, false, "x"));
    }
}
