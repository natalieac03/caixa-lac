package br.com.lac.caixa.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.lac.caixa.controle.csv.ExportadorCSV;
import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.PagamentoVenda;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.modelo.pagamento.DadosDaParte;
import br.com.lac.caixa.modelo.pagamento.PagamentoFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Formatação e leitura de valores em reais, e o CSV com vírgula decimal. */
class DinheiroTest {

    static PagamentoVenda parte(FormaPagamento forma, String valor, String descricao) {
        return new PagamentoVenda(forma, new BigDecimal(valor), descricao, null, null, null);
    }

    @Test
    @DisplayName("Lê \"1.234,56\", \"12,50\" e \"12.50\"; vazio é null; formato errado é recusado")
    void aceitaFormatosERecusaValorMalformado() {
        assertEquals(new BigDecimal("1234.56"), Dinheiro.parse("1.234,56"));
        assertEquals(new BigDecimal("12.50"), Dinheiro.parse("12,50"));
        assertEquals(new BigDecimal("12.50"), Dinheiro.parse("12.50"));
        assertNull(Dinheiro.parse(" "));
        assertThrows(RegraDeNegocioException.class, () -> Dinheiro.parse("1,2,3"));
        assertThrows(RegraDeNegocioException.class, () -> Dinheiro.parse("texto"));
    }

    @Test
    @DisplayName("Na tela o valor tem separador de milhar (R$ 1.234,56); no CSV não (1234,56)")
    void reaisSeparaMilharECsvNao() {
        var mil = new BigDecimal("1234.56");

        String mensagem = PagamentoFactory.mensagem(dados(List.of(parte(FormaPagamento.PIX, "1234.56", null))));

        assertEquals("R$ 1.234,56", Dinheiro.reais(mil));
        assertEquals("R$ 0,50", Dinheiro.reais(new BigDecimal("0.5")));
        assertEquals("1234,56", Dinheiro.numero(mil));
        assertTrue(mensagem.contains("R$ 1.234,56"));
    }

    @Test
    @DisplayName("A mensagem de confirmação de toda forma de pagamento mostra o valor em reais")
    void todasAsMensagensUsamReais() {
        for (var forma : FormaPagamento.values()) {
            String mensagem = PagamentoFactory.mensagem(dados(List.of(parte(forma, "12.50", "teste"))));

            assertTrue(mensagem.contains("R$ 12,50"), forma + ": " + mensagem);
        }
    }

    @Test
    @DisplayName("CSV usa vírgula decimal e escapa aspas nos nomes")
    void csvUsaVirgulaEEscapaNomes() {
        var p = new Produto("Produto \"especial\"", new BigDecimal("12.50"), 2);
        var v = Venda.nova(LocalDateTime.now());
        v.registrarValores(new BigDecimal("12.50"), BigDecimal.ZERO, new BigDecimal("12.50"), null);
        v.registrarPagamentos(List.of(parte(FormaPagamento.PIX, "12.50", null)));

        String estoque = ExportadorCSV.doEstoque(List.of(p));
        String historico = ExportadorCSV.doHistorico(List.of(v));

        assertTrue(estoque.contains("\"Produto \"\"especial\"\"\";\"\";12,50;2"));
        assertTrue(historico.contains(";12,50;12,50;0,00;0,00;0,00;0,00;\"\"\n"));
    }

    static List<DadosDaParte> dados(List<PagamentoVenda> partes) {
        return partes.stream().map(PagamentoVenda::dados).toList();
    }
}
