package br.com.lac.caixa.util;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
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
        assertThrows(RegraDeNegocioException.class,()->Dinheiro.parse("1,2,3"));
        assertThrows(RegraDeNegocioException.class,()->Dinheiro.parse("texto"));
    }
    @Test void reaisSeparaMilharECsvNao() {
        assertEquals("R$ 1.234,56",Dinheiro.reais(new BigDecimal("1234.56")));
        assertEquals("R$ 0,50",Dinheiro.reais(new BigDecimal("0.5")));
        assertEquals("1234,56",Dinheiro.numero(new BigDecimal("1234.56")));
        assertTrue(PagamentoFactory.mensagem(List.of(new PagamentoVenda(FormaPagamento.PIX,new BigDecimal("1234.56"),null,null))).contains("R$ 1.234,56"));
    }
    @Test void todasAsMensagensUsamReais() {
        for(var forma:FormaPagamento.values())
            assertTrue(PagamentoFactory.mensagem(List.of(new PagamentoVenda(forma,new BigDecimal("12.50"),"teste",null))).contains("R$ 12,50"));
    }
    @Test void csvUsaVirgulaEEscapaNomes() {
        var p=new Produto("Produto \"especial\"",new BigDecimal("12.50"),2);
        assertTrue(ExportadorCSV.doEstoque(List.of(p)).contains("\"Produto \"\"especial\"\"\";\"\";12,50;2"));
        var v=Venda.nova(); v.setValorTotal(new BigDecimal("12.50")); v.registrarPagamentos(List.of(new PagamentoVenda(FormaPagamento.PIX,new BigDecimal("12.50"),null,null)));
        assertTrue(ExportadorCSV.doHistorico(List.of(v)).contains(";12,50;12,50;0,00;0,00;0,00;0,00;\"\"\n"));
        var resposta=ExportadorCSV.arquivoCsv("Teste","estoque.csv");
        assertEquals("attachment; filename=\"estoque.csv\"",resposta.getHeaders().getFirst("Content-Disposition"));
        assertNotNull(resposta.getBody());
    }
}
