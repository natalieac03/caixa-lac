package br.com.lac.caixa.sistema;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

class NavegadorTest {
    @Test
    @DisplayName("Sem navegador: erro no CI, teste pulado no computador; com navegador segue")
    void regraDoCi() {
        assertThrows(IllegalStateException.class, () -> Navegador.conferir(false, true, null));
        assertThrows(TestAbortedException.class, () -> Navegador.conferir(false, false, null));
        assertDoesNotThrow(() -> Navegador.conferir(true, true, null));
    }
}
