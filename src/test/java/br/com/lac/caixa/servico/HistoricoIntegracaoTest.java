package br.com.lac.caixa.servico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.ItemVenda;
import br.com.lac.caixa.modelo.PagamentoVenda;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.repositorio.FiltroHistorico;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import br.com.lac.caixa.repositorio.VendaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:historico;DB_CLOSE_DELAY=-1"})
@Transactional
class HistoricoIntegracaoTest {
    @Autowired
    VendaRepository vendas;

    @Autowired
    ProdutoRepository produtos;

    @Autowired
    ConsultaHistorico consulta;

    Venda venda(Produto p, FormaPagamento forma, LocalDate dia) {
        var v = Venda.nova(java.time.LocalDateTime.now());
        v.setDataHora(dia.atTime(12, 0));
        v.adicionarItem(new ItemVenda(p, 2));
        v.registrarValores(new BigDecimal("2"), BigDecimal.ZERO, new BigDecimal("2"), null);
        v.registrarPagamentos(List.of(new PagamentoVenda(forma, new BigDecimal("2"), null, null)));
        return vendas.saveAndFlush(v);
    }

    long vendasNoFiltro(LocalDate dia, String termo) {
        return consulta.consultar(new FiltroHistorico(null, dia, dia, termo), 0)
                .resumo()
                .getVendas();
    }

    @Test
    @DisplayName("Página de 50 cortada no banco; indicadores e CSV somam o filtro inteiro")
    void paginaNoBancoESomaFiltroInteiro() {
        // Arrange: 51 vendas Pix no dia, 1 de crédito no dia e 1 Pix no dia seguinte
        var dia = LocalDate.of(2030, 1, 1);
        var p = produtos.save(new Produto("Especial %_", BigDecimal.ONE, 1000));
        for (int i = 0; i < 51; i++) {
            venda(p, FormaPagamento.PIX, dia);
        }
        venda(p, FormaPagamento.CREDITO, dia);
        venda(p, FormaPagamento.PIX, dia.plusDays(1));
        var filtro = new FiltroHistorico(FormaPagamento.PIX, dia, dia, "ESPECIAL %_");

        // Act
        var primeira = consulta.consultar(filtro, 0);
        var segunda = consulta.consultar(filtro, 1);

        // Assert
        assertEquals(50, primeira.pagina().getNumberOfElements());
        assertEquals(1, segunda.pagina().getNumberOfElements());
        assertEquals(51, primeira.pagina().getTotalElements());
        assertEquals(51, primeira.resumo().getVendas());
        assertEquals(102, primeira.resumo().getItens());
        assertEquals(0, new BigDecimal("102").compareTo(primeira.resumo().getTotal()));
        assertEquals(51, consulta.exportar(filtro).size());
        assertEquals("Especial %_ x2", primeira.pagina().getContent().get(0).getResumoProdutos());
        assertNotEquals(
                primeira.pagina().getContent().get(49).getId(),
                segunda.pagina().getContent().get(0).getId());
    }

    @Test
    @DisplayName("Filtro de produto traz a venda inteira (todos os itens) sem duplicar o total")
    void produtoQualificaVendaSemCortarOutrosItensOuDuplicarTotal() {
        // Arrange: venda com o produto buscado (x2) e outro (x3), total 5
        var dia = LocalDate.of(2031, 1, 1);
        var p = produtos.save(new Produto("Buscado", BigDecimal.ONE, 10));
        var outro = produtos.save(new Produto("Outro", BigDecimal.ONE, 10));
        var v = venda(p, FormaPagamento.PIX, dia);
        v.adicionarItem(new ItemVenda(outro, 3));
        v.registrarValores(new BigDecimal("5"), BigDecimal.ZERO, new BigDecimal("5"), null);
        v.registrarPagamentos(List.of(new PagamentoVenda(FormaPagamento.PIX, new BigDecimal("5"), null, null)));
        vendas.saveAndFlush(v);

        // Act
        var r = consulta.consultar(new FiltroHistorico(null, dia, dia, "buscado"), 0);

        // Assert
        assertEquals(1, r.resumo().getVendas());
        assertEquals(5, r.resumo().getItens());
        assertEquals(0, new BigDecimal("5").compareTo(r.resumo().getTotal()));
        assertEquals(2, r.pagina().getContent().get(0).getItens().size());
    }

    @Test
    @DisplayName("Filtro de produto não diferencia acento nem maiúscula (H2)")
    void produtoSemDiferenciarAcentoNoH2() {
        // Arrange
        var dia = LocalDate.of(2033, 2, 2);
        var p = produtos.save(new Produto("Pão de Açúcar ÉPICO", BigDecimal.ONE, 10));
        venda(p, FormaPagamento.PIX, dia);

        // Act / Assert
        for (String termo : new String[] {"pao de acucar", "PÃO", "epico", "acuca"}) {
            assertEquals(1, vendasNoFiltro(dia, termo), termo);
        }
        assertEquals(0, vendasNoFiltro(dia, "paes"));
    }
}
