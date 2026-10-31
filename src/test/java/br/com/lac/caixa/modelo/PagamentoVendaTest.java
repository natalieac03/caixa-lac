package br.com.lac.caixa.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.pagamento.PagamentoFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PagamentoVendaTest {
    static BigDecimal r(String v) {
        return new BigDecimal(v);
    }

    static PagamentoVenda parte(FormaPagamento f, String valor) {
        return new PagamentoVenda(f, r(valor), null, null);
    }

    Venda venda(String cobrado, String motivo) {
        var v = Venda.nova(java.time.LocalDateTime.now());
        v.registrarValores(r("18"), BigDecimal.ZERO, r(cobrado), motivo);
        return v;
    }

    @Test
    void somaTemQueSerExatamenteOCobradoAoCentavo() {
        var v = venda("18.00", null);
        var falta = assertThrows(
                RegraDeNegocioException.class,
                () -> v.registrarPagamentos(
                        List.of(parte(FormaPagamento.PIX, "10"), parte(FormaPagamento.DINHEIRO, "7.99"))));
        assertTrue(falta.getMessage().contains("falta R$ 0,01"), falta.getMessage());
        var sobra = assertThrows(
                RegraDeNegocioException.class,
                () -> v.registrarPagamentos(
                        List.of(parte(FormaPagamento.PIX, "10"), parte(FormaPagamento.DINHEIRO, "8.01"))));
        assertTrue(sobra.getMessage().contains("sobra R$ 0,01"), sobra.getMessage());
        assertTrue(v.getPagamentos().isEmpty());
        v.registrarPagamentos(List.of(parte(FormaPagamento.PIX, "10"), parte(FormaPagamento.DINHEIRO, "8")));
        assertEquals(2, v.getPagamentos().size());
        assertTrue(v.isPagamentoDividido());
        assertEquals(0, r("8").compareTo(v.valorPago(FormaPagamento.DINHEIRO)));
        assertEquals(0, BigDecimal.ZERO.compareTo(v.valorPago(FormaPagamento.CREDITO)));
    }

    @Test
    void trocoDoDinheiro() {
        var dinheiro = new PagamentoVenda(FormaPagamento.DINHEIRO, r("8"), null, r("10"));
        assertEquals(0, r("2").compareTo(dinheiro.getTroco()));
        assertEquals("Dinheiro R$ 8,00 (troco R$ 2,00)", dinheiro.getTexto());
        assertNull(parte(FormaPagamento.DINHEIRO, "8").getTroco());
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        new PagamentoVenda(FormaPagamento.DINHEIRO, r("8"), null, r("8")).getTroco()));
        var menor = assertThrows(
                RegraDeNegocioException.class,
                () -> new PagamentoVenda(FormaPagamento.DINHEIRO, r("8"), null, r("7.99")));
        assertTrue(menor.getMessage().contains("menor"));
        assertThrows(
                RegraDeNegocioException.class, () -> new PagamentoVenda(FormaPagamento.PIX, r("8"), null, r("10")));
    }

    @Test
    void partesInvalidas() {
        assertThrows(RegraDeNegocioException.class, () -> new PagamentoVenda(null, r("1"), null, null));
        assertThrows(RegraDeNegocioException.class, () -> parte(FormaPagamento.PIX, "0"));
        assertThrows(RegraDeNegocioException.class, () -> parte(FormaPagamento.PIX, "-1"));
        assertThrows(RegraDeNegocioException.class, () -> new PagamentoVenda(FormaPagamento.OUTRO, r("1"), "  ", null));
        assertEquals("Vale R$ 1,00", new PagamentoVenda(FormaPagamento.OUTRO, r("1"), " Vale ", null).getTexto());
        assertNull(new PagamentoVenda(FormaPagamento.PIX, r("1"), "ignorada", null).getDescricao());
        var muitas = new ArrayList<PagamentoVenda>();
        for (int i = 0; i < 11; i++) muitas.add(parte(FormaPagamento.PIX, "1"));
        var v = venda("11", null);
        assertThrows(RegraDeNegocioException.class, () -> v.registrarPagamentos(muitas));
        assertThrows(RegraDeNegocioException.class, () -> venda("18", null).registrarPagamentos(List.of()));
    }

    @Test
    void cortesiaNaoTemPagamentoEExigeMotivo() {
        var comMotivo = venda("0", "Brinde do evento");
        comMotivo.registrarPagamentos(List.of());
        assertTrue(comMotivo.isCortesia());
        assertEquals("Cortesia", comMotivo.getPagamentoTexto());
        assertEquals("Cortesia: nada a receber.", PagamentoFactory.mensagem(comMotivo.getPagamentos()));
        assertThrows(
                RegraDeNegocioException.class,
                () -> venda("0", "Brinde").registrarPagamentos(List.of(parte(FormaPagamento.PIX, "1"))));
        assertThrows(RegraDeNegocioException.class, () -> venda("0", " ").registrarPagamentos(List.of()));
        assertThrows(RegraDeNegocioException.class, () -> venda("0", null).registrarPagamentos(List.of()));
    }

    @Test
    void mensagemUmaPartePorLinha() {
        var partes = List.of(
                parte(FormaPagamento.PIX, "10"), new PagamentoVenda(FormaPagamento.DINHEIRO, r("8"), null, r("10")));
        assertEquals(
                "Recebido R$ 10,00 via Pix + R$ 8,00 em dinheiro (troco R$ 2,00).", PagamentoFactory.mensagem(partes));
        var v = venda("18", null);
        v.registrarPagamentos(partes);
        assertEquals("Pix R$ 10,00 + Dinheiro R$ 8,00 (troco R$ 2,00)", v.getPagamentoTexto());
        assertEquals(
                "Recebido R$ 5,00 no débito + R$ 1,00 no crédito + R$ 2,00 via Vale.",
                PagamentoFactory.mensagem(List.of(
                        parte(FormaPagamento.DEBITO, "5"),
                        parte(FormaPagamento.CREDITO, "1"),
                        new PagamentoVenda(FormaPagamento.OUTRO, r("2"), "Vale", null))));
    }
}
