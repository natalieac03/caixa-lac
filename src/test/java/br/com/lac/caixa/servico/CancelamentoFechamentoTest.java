package br.com.lac.caixa.servico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.PagamentoVenda;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.repositorio.FiltroHistorico;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import br.com.lac.caixa.repositorio.VendaRepository;
import br.com.lac.caixa.util.ExportadorCSV;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:cancelamento;DB_CLOSE_DELAY=-1"
        })
@Transactional
class CancelamentoFechamentoTest {
    @Autowired
    CaixaService caixa;

    @Autowired
    FechamentoService fechamento;

    @Autowired
    ConsultaHistorico historico;

    @Autowired
    VendaRepository vendas;

    @Autowired
    ProdutoRepository produtos;

    final LocalDate dia = LocalDate.of(2032, 3, 4);

    Venda registrar(Produto p, int qtd, FormaPagamento forma, String cobrado) {
        var resultado = caixa.registrarVenda(
                Map.of(p.getId(), qtd),
                List.of(PagamentoInformado.total(forma)),
                cobrado == null ? null : new BigDecimal(cobrado),
                cobrado == null ? null : "Acordo",
                UUID.randomUUID().toString());
        var v = resultado.venda();
        v.setDataHora(dia.atTime(23, 59));
        return vendas.saveAndFlush(v);
    }

    static void assertValor(String esperado, BigDecimal atual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(atual), "valor " + atual);
    }

    static Fechamento.Linha linha(Fechamento f, FormaPagamento forma) {
        return f.formas().stream().filter(l -> l.forma() == forma).findFirst().orElseThrow();
    }

    Venda dividida(Produto p, int qtd, PagamentoInformado... partes) {
        var v = caixa.registrarVenda(
                        Map.of(p.getId(), qtd),
                        List.of(partes),
                        null,
                        null,
                        UUID.randomUUID().toString())
                .venda();
        v.setDataHora(dia.atTime(15, 0));
        return vendas.saveAndFlush(v);
    }

    @Test
    @DisplayName("Cancelar devolve todos os itens uma vez só, preserva data/motivo e tira a venda de totais e CSV")
    void cancelarDevolveTodosOsItensUmaVezEPreservaAuditoria() {
        // Arrange: venda de 3 x R$ 6 + 2 x R$ 2 = R$ 22
        var p = produtos.save(new Produto("Primeiro", new BigDecimal("6"), 10));
        var outro = produtos.save(new Produto("Segundo", new BigDecimal("2"), 7));
        var v = caixa.registrarVenda(
                        Map.of(p.getId(), 3, outro.getId(), 2),
                        List.of(PagamentoInformado.total(FormaPagamento.PIX)),
                        null,
                        null,
                        UUID.randomUUID().toString())
                .venda();
        v.setDataHora(dia.atTime(12, 0));
        vendas.saveAndFlush(v);
        assertEquals(7, p.getQuantidade());
        assertEquals(5, outro.getQuantidade());

        // Act: cancela e tenta cancelar de novo
        caixa.cancelarVenda(v.getId(), "Cliente desistiu");
        var instante = v.getDataCancelamento();
        caixa.cancelarVenda(v.getId(), "Segunda tentativa");
        vendas.flush();

        // Assert: estoque devolvido uma vez, auditoria preservada
        assertEquals(10, p.getQuantidade());
        assertEquals(7, outro.getQuantidade());
        assertTrue(v.isCancelada());
        assertNotNull(instante);
        assertEquals(instante, v.getDataCancelamento());
        assertEquals("Cliente desistiu", v.getMotivoCancelamento());
        assertValor("22", v.getValorCobrado());

        // Assert: aparece no histórico, mas fora dos indicadores, do CSV e do fechamento
        var filtro = new FiltroHistorico(null, dia, dia, "");
        var r = historico.consultar(filtro, 0);
        assertEquals(1, r.pagina().getTotalElements());
        assertEquals(0, r.resumo().getVendas());
        assertValor("0", r.resumo().getTotal());
        assertTrue(historico.exportar(filtro).isEmpty());
        assertFalse(ExportadorCSV.doHistorico(List.of(v)).contains("Cliente"));
        assertEquals(1, ExportadorCSV.doHistorico(List.of(v)).lines().count());
        assertValor("0", fechamento.consultar(dia, dia).total());
    }

    @Test
    @DisplayName("Motivo vazio não cancela nem devolve estoque")
    void motivoVazioNaoCancelaNemDevolveEstoque() {
        // Arrange
        var p = produtos.save(new Produto("Produto", BigDecimal.TEN, 10));
        var v = registrar(p, 2, FormaPagamento.PIX, null);

        // Act / Assert
        assertThrows(RegraDeNegocioException.class, () -> caixa.cancelarVenda(v.getId(), " "));
        assertFalse(v.isCancelada());
        assertEquals(8, p.getQuantidade());
    }

    @Test
    @DisplayName("Fechamento soma formas, descontos e ajustes sem canceladas nem vendas fora do período")
    void fechamentoSomaFormasDescontosEAjustesSemCanceladas() {
        // Arrange
        var p = produtos.save(new Produto("Produto", new BigDecimal("10"), 20));
        registrar(p, 2, FormaPagamento.PIX, "18"); // ajuste -2
        registrar(p, 1, FormaPagamento.PIX, "12"); // ajuste +2
        var credito = registrar(p, 1, FormaPagamento.CREDITO, null);
        credito.registrarValores(new BigDecimal("8"), new BigDecimal("2"), new BigDecimal("8"), null);
        credito.registrarPagamentos(
                List.of(new PagamentoVenda(FormaPagamento.CREDITO, new BigDecimal("8"), null, null)));
        vendas.saveAndFlush(credito);
        var cancelada = registrar(p, 1, FormaPagamento.DINHEIRO, null);
        caixa.cancelarVenda(cancelada.getId(), "Cancelada");
        var fora = registrar(p, 1, FormaPagamento.PIX, null);
        fora.setDataHora(dia.plusDays(1).atStartOfDay());
        vendas.saveAndFlush(fora);
        var legado = Venda.nova(java.time.LocalDateTime.now());
        legado.setDataHora(dia.atStartOfDay());
        legado.registrarValores(new BigDecimal("4"), BigDecimal.ZERO, new BigDecimal("4"), null);
        legado.registrarPagamentos(List.of(new PagamentoVenda(FormaPagamento.DEBITO, new BigDecimal("4"), null, null)));
        vendas.saveAndFlush(legado);

        // Act
        var r = fechamento.consultar(dia, dia);

        // Assert
        assertEquals(4, r.quantidade());
        assertValor("42", r.total());
        assertValor("2", r.descontos());
        assertValor("0", r.ajustes());
        assertEquals(2, linha(r, FormaPagamento.PIX).vendas());
        assertValor("30", linha(r, FormaPagamento.PIX).total());
        assertEquals(1, linha(r, FormaPagamento.CREDITO).vendas());
        assertValor("8", linha(r, FormaPagamento.CREDITO).total());
        assertTrue(r.isConfere());
        assertEquals(0, linha(r, FormaPagamento.DINHEIRO).vendas());
        assertTrue(ExportadorCSV.doFechamento(r, dia, dia).contains("TOTAL;4;42,00"));
    }

    @Test
    @DisplayName("Fechamento soma cada parte do pagamento dividido na forma certa e bate com o total cobrado")
    void fechamentoSomaCadaParteNaFormaCertaEBateComOTotal() {
        // Arrange: duas vendas divididas, uma simples, uma cortesia e uma dividida cancelada
        var p = produtos.save(new Produto("Produto", new BigDecimal("6"), 50));
        dividida(
                p,
                3,
                new PagamentoInformado(FormaPagamento.PIX, new BigDecimal("10"), null, null),
                new PagamentoInformado(FormaPagamento.DINHEIRO, new BigDecimal("8"), null, new BigDecimal("10")));
        dividida(
                p,
                2,
                new PagamentoInformado(FormaPagamento.PIX, new BigDecimal("5"), null, null),
                new PagamentoInformado(FormaPagamento.OUTRO, new BigDecimal("7"), "Vale", null));
        registrar(p, 1, FormaPagamento.DINHEIRO, null);
        var cortesia = caixa.registrarVenda(
                        Map.of(p.getId(), 1),
                        List.of(PagamentoInformado.total(FormaPagamento.PIX)),
                        BigDecimal.ZERO,
                        "Brinde",
                        UUID.randomUUID().toString())
                .venda();
        cortesia.setDataHora(dia.atTime(16, 0));
        vendas.saveAndFlush(cortesia);
        var cancelada = dividida(
                p,
                2,
                new PagamentoInformado(FormaPagamento.CREDITO, new BigDecimal("6"), null, null),
                new PagamentoInformado(FormaPagamento.DEBITO, new BigDecimal("6"), null, null));
        int antes = p.getQuantidade();

        // Act
        caixa.cancelarVenda(cancelada.getId(), "Desistiu");
        var r = fechamento.consultar(dia, dia);

        // Assert: cancelamento devolve o estoque
        assertEquals(antes + 2, p.getQuantidade());
        // Assert: cada parte na sua forma
        assertValor("15", linha(r, FormaPagamento.PIX).total());
        assertEquals(2, linha(r, FormaPagamento.PIX).vendas());
        assertValor("14", linha(r, FormaPagamento.DINHEIRO).total());
        assertEquals(2, linha(r, FormaPagamento.DINHEIRO).vendas());
        assertValor("7", linha(r, FormaPagamento.OUTRO).total());
        // Assert: cancelada sai dos totais com todas as suas partes
        assertValor("0", linha(r, FormaPagamento.CREDITO).total());
        assertValor("0", linha(r, FormaPagamento.DEBITO).total());
        // Assert: totais e conferência
        assertEquals(4, r.quantidade());
        assertValor("36", r.total());
        assertValor("36", r.totalFormas());
        assertEquals(2, r.divididas());
        assertEquals(1, r.cortesias());
        assertTrue(r.isConfere(), "soma das formas " + r.totalFormas() + " x total " + r.total());
        String csv = ExportadorCSV.doFechamento(r, dia, dia);
        assertTrue(csv.contains("\"Pix\";2;15,00"), csv);
        assertTrue(csv.contains("\"Vendas com pagamento dividido\";2;"), csv);
        assertTrue(csv.contains("\"Cortesias\";1;"), csv);
    }
}
