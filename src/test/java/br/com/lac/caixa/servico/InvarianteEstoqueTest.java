package br.com.lac.caixa.servico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.MovimentoEstoque;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.TipoMovimento;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.repositorio.MovimentoEstoqueRepository;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Todo lugar que mexe no estoque grava movimento. */
@SpringBootTest(
        properties = {
            "spring.profiles.active=test",
            "spring.datasource.url=jdbc:h2:mem:invariante_estoque;DB_CLOSE_DELAY=-1"
        })
class InvarianteEstoqueTest {
    @Autowired
    EstoqueService estoque;

    @Autowired
    CaixaService caixa;

    @Autowired
    ProdutoRepository produtos;

    @Autowired
    MovimentoEstoqueRepository movimentos;

    Venda vender(Produto p, int quantidade) {
        return caixa.registrarVenda(
                        Map.of(p.getId(), quantidade),
                        List.of(PagamentoInformado.total(FormaPagamento.DINHEIRO)),
                        null,
                        null,
                        UUID.randomUUID().toString())
                .venda();
    }

    @Test
    @DisplayName("Cada operação grava o movimento certo: tipo, sinal, custo, venda e compra")
    void movimentosDeCadaOperacao() {
        var p = estoque.criarNovo("Bóton Movimento", new BigDecimal("6"), 10, null, new BigDecimal("2"));

        var venda = vender(p, 3);
        caixa.cancelarVenda(venda.getId(), "Ensaio");
        var compra = estoque.registrarCompra(p.getId(), 5, new BigDecimal("15.00"), LocalDate.now(), "fornecedor");
        estoque.adicionarUnidades(p.getId(), 2);
        estoque.removerUnidades(p.getId(), 4);

        List<MovimentoEstoque> ms = movimentos.findByProdutoIdOrderByIdAsc(p.getId());
        assertEquals(
                List.of(
                        TipoMovimento.ENTRADA_AJUSTE,
                        TipoMovimento.SAIDA_VENDA,
                        TipoMovimento.DEVOLUCAO_CANCELAMENTO,
                        TipoMovimento.ENTRADA_COMPRA,
                        TipoMovimento.ENTRADA_AJUSTE,
                        TipoMovimento.SAIDA_AJUSTE),
                ms.stream().map(MovimentoEstoque::getTipo).toList());
        assertEquals(
                List.of(10, -3, 3, 5, 2, -4),
                ms.stream().map(MovimentoEstoque::getQuantidade).toList());
        assertEquals(venda.getId(), ms.get(1).getVenda().getId());
        assertEquals(venda.getId(), ms.get(2).getVenda().getId());
        assertEquals(0, new BigDecimal("2").compareTo(ms.get(1).getCustoUnitario()), "custo congelado no item");
        assertEquals(compra.getId(), ms.get(3).getCompra().getId());
        assertEquals(0, new BigDecimal("3").compareTo(ms.get(3).getCustoUnitario()), "custo da compra: 15/5");
        assertEquals(13, movimentos.saldo(p.getId()));
        assertEquals(13, produtos.findById(p.getId()).orElseThrow().getQuantidade());
    }

    @Test
    @DisplayName("Produto com movimentos é arquivado, não apagado; operação recusada não grava movimento")
    void arquivaERecusaSemMovimento() {
        var p = estoque.criarNovo("Bóton Arquivo", new BigDecimal("6"), 2, null, null);

        assertThrows(RegraDeNegocioException.class, () -> estoque.removerUnidades(p.getId(), 5));
        assertEquals(1, movimentos.findByProdutoIdOrderByIdAsc(p.getId()).size());

        estoque.remover(p.getId());

        assertTrue(!produtos.findById(p.getId()).orElseThrow().isAtivo(), "arquivado");
        assertEquals(2, movimentos.saldo(p.getId()));
    }

    @Test
    @DisplayName("Propriedade: centenas de operações aleatórias (semente fixa) e a soma sempre bate")
    void operacoesAleatorias() {
        Random aleatorio = new Random(20261030L);
        List<Produto> lista = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            lista.add(
                    estoque.criarNovo("Bóton Aleatório " + i, new BigDecimal("6"), aleatorio.nextInt(20), null, null));
        }
        List<Venda> vendidas = new ArrayList<>();

        for (int passo = 0; passo < 300; passo++) {
            Produto p = lista.get(aleatorio.nextInt(lista.size()));
            int qtd = 1 + aleatorio.nextInt(6);
            try {
                switch (aleatorio.nextInt(5)) {
                    case 0 -> vendidas.add(vender(p, qtd));
                    case 1 -> {
                        if (!vendidas.isEmpty()) {
                            caixa.cancelarVenda(
                                    vendidas.remove(aleatorio.nextInt(vendidas.size()))
                                            .getId(),
                                    "Ensaio");
                        }
                    }
                    case 2 ->
                        estoque.registrarCompra(
                                p.getId(),
                                qtd,
                                new BigDecimal(aleatorio.nextInt(5000)).movePointLeft(2),
                                LocalDate.now(),
                                null);
                    case 3 -> estoque.adicionarUnidades(p.getId(), qtd);
                    default -> estoque.removerUnidades(p.getId(), qtd);
                }
            } catch (RegraDeNegocioException recusadaSemMexerNoEstoque) {
            }
        }

        assertEquals(List.of(), movimentos.produtosComSaldoDivergente());
        for (Produto p : lista) {
            assertEquals(produtos.findById(p.getId()).orElseThrow().getQuantidade(), movimentos.saldo(p.getId()));
        }
    }
}
