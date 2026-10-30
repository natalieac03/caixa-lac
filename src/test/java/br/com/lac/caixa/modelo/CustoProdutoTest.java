package br.com.lac.caixa.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustoProdutoTest {

    static BigDecimal r(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("Compra recalcula o custo médio ponderado: (10 x 2,00 + 15,00) / 15 = 2,3333")
    void custoMedioPonderado() {
        // Arrange
        var p = new Produto("Bóton", r("6"), 10);
        p.setCustoUnitario(r("2"));

        // Act
        p.registrarCompra(5, r("15.00"));

        // Assert
        assertEquals(15, p.getQuantidade());
        assertEquals(r("2.3333"), p.getCustoUnitario());
    }

    @Test
    @DisplayName("Produto sem custo assume o custo unitário da compra; com estoque zero, idem")
    void semCustoAssumeACompra() {
        var semCusto = new Produto("Adesivo", r("8"), 4);
        var zerado = new Produto("Caneca", r("20"), 0);
        zerado.setCustoUnitario(r("9"));

        semCusto.registrarCompra(3, r("10.00"));
        zerado.registrarCompra(2, r("11.00"));

        assertEquals(r("3.3333"), semCusto.getCustoUnitario());
        assertEquals(7, semCusto.getQuantidade());
        assertEquals(r("5.5000"), zerado.getCustoUnitario());
    }

    @Test
    @DisplayName("Custo é opcional e não pode ser negativo; compra exige quantidade >= 1 e custo >= 0")
    void validacoes() {
        var p = new Produto("Bóton", r("6"), 1);
        assertTrue(p.isSemCusto());
        p.setCustoUnitario(BigDecimal.ZERO);
        assertEquals(r("0.0000"), p.getCustoUnitario());
        p.setCustoUnitario(null);
        assertTrue(p.isSemCusto());
        assertThrows(RegraDeNegocioException.class, () -> p.setCustoUnitario(r("-0.01")));
        assertThrows(RegraDeNegocioException.class, () -> p.registrarCompra(0, r("1")));
        assertThrows(RegraDeNegocioException.class, () -> p.registrarCompra(1, r("-1")));
        assertThrows(RegraDeNegocioException.class, () -> p.registrarCompra(1, null));
        assertEquals(1, p.getQuantidade());
    }

    @Test
    @DisplayName("O item da venda congela o custo do momento; sem custo fica null")
    void itemCongelaCusto() {
        var p = new Produto("Bóton", r("6"), 10);
        p.setCustoUnitario(r("2.5"));
        var item = new ItemVenda(p, 2, r("6"));
        var semCusto = new ItemVenda(new Produto("Outro", r("1"), 1), 1);

        p.setCustoUnitario(r("9"));

        assertEquals(r("2.5000"), item.getCustoUnitario());
        assertNull(semCusto.getCustoUnitario());
    }
}
