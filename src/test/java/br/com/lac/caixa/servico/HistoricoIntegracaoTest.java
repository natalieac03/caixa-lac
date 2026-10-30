package br.com.lac.caixa.servico;

import br.com.lac.caixa.modelo.*;
import br.com.lac.caixa.repositorio.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:historico;DB_CLOSE_DELAY=-1"})
@Transactional
class HistoricoIntegracaoTest {
    @Autowired VendaRepository vendas;
    @Autowired ProdutoRepository produtos;
    @Autowired ConsultaHistorico consulta;

    Venda venda(Produto p, FormaPagamento forma, LocalDate dia) {
        var v=Venda.nova(); v.setDataHora(dia.atTime(12,0));
        v.adicionarItem(new ItemVenda(p,2));
        v.registrarValores(new BigDecimal("2"),BigDecimal.ZERO,new BigDecimal("2"),null);
        v.registrarPagamentos(java.util.List.of(new PagamentoVenda(forma,new BigDecimal("2"),null,null)));
        return vendas.saveAndFlush(v);
    }
    @Test void paginaNoBancoESomaFiltroInteiro() {
        var dia=LocalDate.of(2030,1,1);
        var p=produtos.save(new Produto("Especial %_",BigDecimal.ONE,1000));
        for(int i=0;i<51;i++) venda(p,FormaPagamento.PIX,dia);
        venda(p,FormaPagamento.CREDITO,dia); venda(p,FormaPagamento.PIX,dia.plusDays(1));
        var filtro=new FiltroHistorico(FormaPagamento.PIX,dia,dia,"ESPECIAL %_");
        var primeira=consulta.consultar(filtro,0); var segunda=consulta.consultar(filtro,1);
        assertEquals(50,primeira.pagina().getNumberOfElements()); assertEquals(1,segunda.pagina().getNumberOfElements());
        assertEquals(51,primeira.pagina().getTotalElements()); assertEquals(51,primeira.resumo().getVendas());
        assertEquals(102,primeira.resumo().getItens()); assertEquals(0,new BigDecimal("102").compareTo(primeira.resumo().getTotal()));
        assertEquals(51,consulta.exportar(filtro).size());
        assertEquals("Especial %_ x2",primeira.pagina().getContent().get(0).getResumoProdutos());
        assertNotEquals(primeira.pagina().getContent().get(49).getId(),segunda.pagina().getContent().get(0).getId());
    }
    @Test void produtoQualificaVendaSemCortarOutrosItensOuDuplicarTotal() {
        var dia=LocalDate.of(2031,1,1);
        var p=produtos.save(new Produto("Buscado",BigDecimal.ONE,10));
        var outro=produtos.save(new Produto("Outro",BigDecimal.ONE,10));
        var v=venda(p,FormaPagamento.PIX,dia); v.adicionarItem(new ItemVenda(outro,3));
        v.registrarValores(new BigDecimal("5"),BigDecimal.ZERO,new BigDecimal("5"),null); v.registrarPagamentos(java.util.List.of(new PagamentoVenda(FormaPagamento.PIX,new BigDecimal("5"),null,null))); vendas.saveAndFlush(v);
        var r=consulta.consultar(new FiltroHistorico(null,dia,dia,"buscado"),0);
        assertEquals(1,r.resumo().getVendas()); assertEquals(5,r.resumo().getItens());
        assertEquals(0,new BigDecimal("5").compareTo(r.resumo().getTotal()));
        assertEquals(2,r.pagina().getContent().get(0).getItens().size());
    }
}
