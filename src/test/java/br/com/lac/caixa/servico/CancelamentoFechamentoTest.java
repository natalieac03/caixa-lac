package br.com.lac.caixa.servico;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
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
        var resultado=caixa.registrarVenda(Map.of(p.getId(),qtd),List.of(PagamentoInformado.total(forma)),
                cobrado==null?null:new BigDecimal(cobrado),cobrado==null?null:"Acordo",UUID.randomUUID().toString());
        var v=resultado.venda(); v.setDataHora(dia.atTime(23,59)); return vendas.saveAndFlush(v);
    }
    void valor(String esperado, BigDecimal atual) { assertEquals(0,new BigDecimal(esperado).compareTo(atual)); }

    @Test void cancelarDevolveTodosOsItensUmaVezEPreservaAuditoria() {
        var p=produtos.save(new Produto("Primeiro",new BigDecimal("6"),10));
        var outro=produtos.save(new Produto("Segundo",new BigDecimal("2"),7));
        var v=caixa.registrarVenda(Map.of(p.getId(),3,outro.getId(),2),List.of(PagamentoInformado.total(FormaPagamento.PIX)),null,null,UUID.randomUUID().toString()).venda();
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
        assertThrows(RegraDeNegocioException.class,()->caixa.cancelarVenda(v.getId()," "));
        assertFalse(v.isCancelada()); assertEquals(8,p.getQuantidade());
    }
    @Test void fechamentoSomaFormasDescontosEAjustesSemCanceladas() {
        var p=produtos.save(new Produto("Produto",new BigDecimal("10"),20));
        registrar(p,2,FormaPagamento.PIX,"18"); registrar(p,1,FormaPagamento.PIX,"12");
        var credito=registrar(p,1,FormaPagamento.CREDITO,null);
        credito.registrarValores(new BigDecimal("8"),new BigDecimal("2"),new BigDecimal("8"),null); credito.registrarPagamentos(List.of(new PagamentoVenda(FormaPagamento.CREDITO,new BigDecimal("8"),null,null))); vendas.saveAndFlush(credito);
        var cancelada=registrar(p,1,FormaPagamento.DINHEIRO,null); caixa.cancelarVenda(cancelada.getId(),"Cancelada");
        var fora=registrar(p,1,FormaPagamento.PIX,null); fora.setDataHora(dia.plusDays(1).atStartOfDay()); vendas.saveAndFlush(fora);
        var legado=Venda.nova(); legado.setDataHora(dia.atStartOfDay()); legado.setValorTotal(new BigDecimal("4")); legado.registrarPagamentos(List.of(new PagamentoVenda(FormaPagamento.DEBITO,new BigDecimal("4"),null,null))); vendas.saveAndFlush(legado);
        var r=fechamento.consultar(dia,dia);
        assertEquals(4,r.quantidade()); valor("42",r.total()); valor("2",r.descontos()); valor("0",r.ajustes());
        var pix=r.formas().stream().filter(f->f.forma()==FormaPagamento.PIX).findFirst().orElseThrow();
        assertEquals(2,pix.vendas()); valor("30",pix.total());
        var cr=r.formas().stream().filter(f->f.forma()==FormaPagamento.CREDITO).findFirst().orElseThrow();
        assertEquals(1,cr.vendas()); valor("8",cr.total()); assertTrue(r.isConfere());
        assertEquals(0,r.formas().stream().filter(f->f.forma()==FormaPagamento.DINHEIRO).findFirst().orElseThrow().vendas());
        assertTrue(ExportadorCSV.doFechamento(r,dia,dia).contains("TOTAL;4;42,00"));
    }

    Venda dividida(Produto p, int qtd, PagamentoInformado... partes) {
        var v=caixa.registrarVenda(Map.of(p.getId(),qtd),List.of(partes),null,null,UUID.randomUUID().toString()).venda();
        v.setDataHora(dia.atTime(15,0)); return vendas.saveAndFlush(v);
    }
    @Test void fechamentoSomaCadaParteNaFormaCertaEBateComOTotal() {
        var p=produtos.save(new Produto("Produto",new BigDecimal("6"),50));
        dividida(p,3,new PagamentoInformado(FormaPagamento.PIX,new BigDecimal("10"),null,null),
                new PagamentoInformado(FormaPagamento.DINHEIRO,new BigDecimal("8"),null,new BigDecimal("10")));
        dividida(p,2,new PagamentoInformado(FormaPagamento.PIX,new BigDecimal("5"),null,null),
                new PagamentoInformado(FormaPagamento.OUTRO,new BigDecimal("7"),"Vale",null));
        registrar(p,1,FormaPagamento.DINHEIRO,null);
        var cortesia=caixa.registrarVenda(Map.of(p.getId(),1),List.of(PagamentoInformado.total(FormaPagamento.PIX)),BigDecimal.ZERO,"Brinde",UUID.randomUUID().toString()).venda();
        cortesia.setDataHora(dia.atTime(16,0)); vendas.saveAndFlush(cortesia);
        var cancelada=dividida(p,2,new PagamentoInformado(FormaPagamento.CREDITO,new BigDecimal("6"),null,null),
                new PagamentoInformado(FormaPagamento.DEBITO,new BigDecimal("6"),null,null));
        int antes=p.getQuantidade();
        caixa.cancelarVenda(cancelada.getId(),"Desistiu");
        assertEquals(antes+2,p.getQuantidade());

        var r=fechamento.consultar(dia,dia);
        java.util.function.Function<FormaPagamento,Fechamento.Linha> linha=f->r.formas().stream().filter(l->l.forma()==f).findFirst().orElseThrow();
        valor("15",linha.apply(FormaPagamento.PIX).total()); assertEquals(2,linha.apply(FormaPagamento.PIX).vendas());
        valor("14",linha.apply(FormaPagamento.DINHEIRO).total()); assertEquals(2,linha.apply(FormaPagamento.DINHEIRO).vendas());
        valor("7",linha.apply(FormaPagamento.OUTRO).total());
        // Cancelada sai dos totais com todas as suas partes.
        valor("0",linha.apply(FormaPagamento.CREDITO).total()); valor("0",linha.apply(FormaPagamento.DEBITO).total());
        assertEquals(4,r.quantidade()); valor("36",r.total()); valor("36",r.totalFormas());
        assertEquals(2,r.divididas()); assertEquals(1,r.cortesias());
        assertTrue(r.isConfere(),"soma das formas "+r.totalFormas()+" x total "+r.total());
        String csv=ExportadorCSV.doFechamento(r,dia,dia);
        assertTrue(csv.contains("\"Pix\";2;15,00"),csv); assertTrue(csv.contains("\"Vendas com pagamento dividido\";2;"),csv);
        assertTrue(csv.contains("\"Cortesias\";1;"),csv);
    }
}
