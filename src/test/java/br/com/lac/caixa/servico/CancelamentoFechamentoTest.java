package br.com.lac.caixa.servico;

import br.com.lac.caixa.modelo.*;
import br.com.lac.caixa.repositorio.*;
import br.com.lac.caixa.util.ExportadorCSV;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:cancelamento;DB_CLOSE_DELAY=-1"})
@Transactional
class CancelamentoFechamentoTest {
    @Autowired CaixaService caixa;
    @Autowired FechamentoService fechamento;
    @Autowired ConsultaHistorico historico;
    @Autowired VendaRepository vendas;
    @Autowired ProdutoRepository produtos;
    final LocalDate dia=LocalDate.of(2032,3,4);

    Venda registrar(Produto p, int qtd, FormaPagamento forma, String cobrado) {
        var resultado=caixa.registrarVenda(Map.of(p.getId(),qtd),forma,null,
                cobrado==null?null:new BigDecimal(cobrado),cobrado==null?null:"Acordo",UUID.randomUUID().toString());
        var v=resultado.venda(); v.setDataHora(dia.atTime(23,59)); return vendas.saveAndFlush(v);
    }
    void valor(String esperado, BigDecimal atual) { assertEquals(0,new BigDecimal(esperado).compareTo(atual)); }

    @Test void cancelarDevolveTodosOsItensUmaVezEPreservaAuditoria() {
        var p=produtos.save(new Produto("Primeiro",new BigDecimal("6"),10));
        var outro=produtos.save(new Produto("Segundo",new BigDecimal("2"),7));
        var v=caixa.registrarVenda(Map.of(p.getId(),3,outro.getId(),2),FormaPagamento.PIX,null,null,null,UUID.randomUUID().toString()).venda();
        v.setDataHora(dia.atTime(12,0)); vendas.saveAndFlush(v);
        assertEquals(7,p.getQuantidade()); assertEquals(5,outro.getQuantidade());
        caixa.cancelarVenda(v.getId(),"Cliente desistiu");
        var instante=v.getDataCancelamento();
        caixa.cancelarVenda(v.getId(),"Segunda tentativa"); vendas.flush();
        assertEquals(10,p.getQuantidade()); assertEquals(7,outro.getQuantidade());
        assertTrue(v.isCancelada()); assertNotNull(instante); assertEquals(instante,v.getDataCancelamento());
        assertEquals("Cliente desistiu",v.getMotivoCancelamento()); valor("22",v.getValorCobrado());
        var filtro=new FiltroHistorico(null,dia,dia,"");
        var r=historico.consultar(filtro,0);
        assertEquals(1,r.pagina().getTotalElements()); assertEquals(0,r.resumo().getVendas());
        valor("0",r.resumo().getTotal()); assertTrue(historico.exportar(filtro).isEmpty());
        assertFalse(ExportadorCSV.doHistorico(List.of(v)).contains("Cliente"));
        assertEquals(1,ExportadorCSV.doHistorico(List.of(v)).lines().count());
        valor("0",fechamento.consultar(dia,dia).total());
    }
    @Test void motivoVazioNaoCancelaNemDevolveEstoque() {
        var p=produtos.save(new Produto("Produto",BigDecimal.TEN,10)); var v=registrar(p,2,FormaPagamento.PIX,null);
        assertThrows(IllegalArgumentException.class,()->caixa.cancelarVenda(v.getId()," "));
        assertFalse(v.isCancelada()); assertEquals(8,p.getQuantidade());
    }
    @Test void fechamentoSomaFormasDescontosEAjustesSemCanceladas() {
        var p=produtos.save(new Produto("Produto",new BigDecimal("10"),20));
        registrar(p,2,FormaPagamento.PIX,"18"); registrar(p,1,FormaPagamento.PIX,"12");
        var credito=registrar(p,1,FormaPagamento.CREDITO,null);
        credito.registrarValores(new BigDecimal("8"),new BigDecimal("2"),new BigDecimal("8"),null); vendas.saveAndFlush(credito);
        var cancelada=registrar(p,1,FormaPagamento.DINHEIRO,null); caixa.cancelarVenda(cancelada.getId(),"Cancelada");
        var fora=registrar(p,1,FormaPagamento.PIX,null); fora.setDataHora(dia.plusDays(1).atStartOfDay()); vendas.saveAndFlush(fora);
        var legado=new Venda(FormaPagamento.DEBITO,null); legado.setDataHora(dia.atStartOfDay()); legado.setValorTotal(new BigDecimal("4")); vendas.saveAndFlush(legado);
        var r=fechamento.consultar(dia,dia);
        assertEquals(4,r.quantidade()); valor("42",r.total()); valor("2",r.descontos()); valor("0",r.ajustes());
        var pix=r.formas().stream().filter(f->f.forma()==FormaPagamento.PIX).findFirst().orElseThrow();
        assertEquals(2,pix.quantidade()); valor("30",pix.total());
        var cr=r.formas().stream().filter(f->f.forma()==FormaPagamento.CREDITO).findFirst().orElseThrow();
        assertEquals(1,cr.quantidade()); valor("8",cr.total()); valor("2",cr.descontos());
        assertEquals(0,r.formas().stream().filter(f->f.forma()==FormaPagamento.DINHEIRO).findFirst().orElseThrow().quantidade());
        assertTrue(ExportadorCSV.doFechamento(r,dia,dia).contains("TOTAL;4;42,00;2,00;0,00"));
    }
}
