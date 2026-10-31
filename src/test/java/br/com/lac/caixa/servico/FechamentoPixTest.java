package br.com.lac.caixa.servico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.lac.caixa.exportacao.ExportadorCSV;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.OrigemPagamento;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.pix.FakeMercadoPago;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Fechamento com o Pix separado em Mercado Pago e manual. */
@SpringBootTest(
        properties = {
            "spring.profiles.active=test",
            "spring.datasource.url=jdbc:h2:mem:fechamento_pix;DB_CLOSE_DELAY=-1"
        })
class FechamentoPixTest {
    static final FakeMercadoPago FAKE = new FakeMercadoPago();
    static final MockWebServer SERVIDOR = FakeMercadoPago.iniciar(FAKE);

    @DynamicPropertySource
    static void mercadoPago(DynamicPropertyRegistry registro) {
        registro.add("mercadopago.url", () -> SERVIDOR.url("/").toString());
        registro.add("mercadopago.access-token", () -> FakeMercadoPago.TOKEN);
        registro.add("mercadopago.payer-email", () -> "caixa@lac.test");
    }

    @AfterAll
    static void parar() throws Exception {
        SERVIDOR.shutdown();
    }

    @Autowired
    CaixaService caixa;

    @Autowired
    CobrancaPixService cobrancas;

    @Autowired
    FechamentoService fechamento;

    @Autowired
    ProdutoRepository produtos;

    @Test
    @DisplayName("Pix do Mercado Pago e Pix manual somam separados e batem com o total Pix; cancelada fica fora")
    void pixSeparadoPorOrigem() {
        var p = produtos.save(new Produto("Bóton Fechamento", new BigDecimal("6"), 100));
        String t1 = UUID.randomUUID().toString();
        String order = cobrancas.gerar(t1, 7, new BigDecimal("10")).getOrderId();
        FAKE.pagar(order, "10.00");
        caixa.registrarVenda(
                Map.of(p.getId(), 3),
                List.of(
                        new PagamentoInformado(
                                FormaPagamento.PIX, new BigDecimal("10"), null, null, OrigemPagamento.MERCADO_PAGO, 7),
                        new PagamentoInformado(FormaPagamento.DINHEIRO, new BigDecimal("8"), null, null)),
                null,
                null,
                t1);
        caixa.registrarVenda(
                Map.of(p.getId(), 2),
                List.of(new PagamentoInformado(FormaPagamento.PIX, null, null, null, OrigemPagamento.MANUAL, 0)),
                null,
                null,
                UUID.randomUUID().toString());
        var cancelada = caixa.registrarVenda(
                        Map.of(p.getId(), 1),
                        List.of(PagamentoInformado.total(FormaPagamento.PIX)),
                        null,
                        null,
                        UUID.randomUUID().toString())
                .venda();
        caixa.cancelarVenda(cancelada.getId(), "Teste");

        var hoje = LocalDate.now();
        var r = fechamento.consultar(hoje, hoje);

        assertEquals(0, new BigDecimal("10").compareTo(r.pixMercadoPago()));
        assertEquals(0, new BigDecimal("12").compareTo(r.pixManual()));
        assertTrue(r.isPixConfere());
        assertTrue(r.isConfere());
        String csv = ExportadorCSV.doFechamento(r, hoje, hoje);
        assertTrue(csv.contains("\"Pix pelo Mercado Pago\";;10,00"), csv);
        assertTrue(csv.contains("\"Pix manual\";;12,00"), csv);
    }
}
