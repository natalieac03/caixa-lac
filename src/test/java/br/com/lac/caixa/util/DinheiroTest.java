package br.com.lac.caixa.util;

import br.com.lac.caixa.modelo.*;
import br.com.lac.caixa.pagamento.PagamentoFactory;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DinheiroTest {
    @Test void aceitaFormatosERecusaValorMalformado() {
        assertEquals(new BigDecimal("1234.56"),Dinheiro.parse("1.234,56"));
        assertEquals(new BigDecimal("12.50"),Dinheiro.parse("12,50"));
        assertEquals(new BigDecimal("12.50"),Dinheiro.parse("12.50"));
        assertNull(Dinheiro.parse(" "));
        assertThrows(IllegalArgumentException.class,()->Dinheiro.parse("1,2,3"));
        assertThrows(IllegalArgumentException.class,()->Dinheiro.parse("texto"));
    }
    @Test void todasAsMensagensUsamReais() {
        for(var forma:FormaPagamento.values())
            assertTrue(PagamentoFactory.criar(forma,"teste").pagar(new BigDecimal("12.50")).contains("R$ 12,50"));
    }
    @Test void csvUsaVirgulaEEscapaNomes() {
        var p=new Produto("Produto \"especial\"",new BigDecimal("12.50"),2);
        assertTrue(ExportadorCSV.doEstoque(List.of(p)).contains("\"Produto \"\"especial\"\"\";\"\";12,50;2"));
        var v=new Venda(FormaPagamento.PIX,null); v.setValorTotal(new BigDecimal("12.50"));
        assertTrue(ExportadorCSV.doHistorico(List.of(v)).contains(";12,50\n"));
        var resposta=ExportadorCSV.arquivoCsv("Teste","estoque.csv");
        assertEquals("attachment; filename=\"estoque.csv\"",resposta.getHeaders().getFirst("Content-Disposition"));
        assertNotNull(resposta.getBody());
    }
}
