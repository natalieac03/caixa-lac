package br.com.lac.caixa.servico;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lac.caixa.dominio.EstoqueInsuficienteException;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.repositorio.MovimentoEstoqueRepository;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Várias vendas do mesmo produto ao mesmo tempo (vários caixas no evento). */
@SpringBootTest(
        properties = {
            "spring.profiles.active=test",
            "spring.datasource.url=jdbc:h2:mem:venda_concorrente;DB_CLOSE_DELAY=-1"
        })
@AutoConfigureMockMvc
class VendaConcorrenteTest {
    @Autowired
    CaixaService caixa;

    @Autowired
    EstoqueService estoque;

    @Autowired
    ProdutoRepository produtos;

    @Autowired
    MovimentoEstoqueRepository movimentos;

    @Autowired
    MockMvc mvc;

    /** Dispara n vendas de 1 unidade ao mesmo tempo; devolve quantas entraram e quantas faltou estoque. */
    int[] venderJuntos(long produtoId, int n) throws Exception {
        var pool = Executors.newFixedThreadPool(n);
        var largada = new CountDownLatch(1);
        List<Future<String>> resultados = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            resultados.add(pool.submit(() -> {
                largada.await();
                try {
                    caixa.registrarVenda(
                            Map.of(produtoId, 1),
                            List.of(PagamentoInformado.total(FormaPagamento.DINHEIRO)),
                            null,
                            null,
                            UUID.randomUUID().toString());
                    return "ok";
                } catch (EstoqueInsuficienteException e) {
                    return "sem estoque";
                } catch (Exception e) {
                    return e.getClass().getSimpleName() + ": " + e.getMessage();
                }
            }));
        }
        largada.countDown();
        int ok = 0, semEstoque = 0;
        for (var r : resultados) {
            String s = r.get();
            if (s.equals("ok")) ok++;
            else if (s.equals("sem estoque")) semEstoque++;
            else throw new AssertionError("venda falhou sem motivo de estoque: " + s);
        }
        pool.shutdown();
        return new int[] {ok, semEstoque};
    }

    @Test
    @DisplayName("10 vendas ao mesmo tempo com 10 em estoque: todas entram e o estoque zera")
    void todasEntramEnquantoHaEstoque() throws Exception {
        var p = estoque.criarNovo("Bóton Disputado " + System.nanoTime(), new BigDecimal("6"), 10, null);
        int[] r = venderJuntos(p.getId(), 10);
        assertEquals(10, r[0]);
        assertEquals(0, produtos.findById(p.getId()).orElseThrow().getQuantidade());
        assertEquals(0, movimentos.saldo(p.getId()));
    }

    @Test
    @DisplayName("10 vendas ao mesmo tempo com 7 em estoque: 7 entram, 3 recebem \"estoque insuficiente\"")
    void soOQueHaNoEstoque() throws Exception {
        var p = estoque.criarNovo("Caneca Disputada " + System.nanoTime(), new BigDecimal("30"), 7, null);
        int[] r = venderJuntos(p.getId(), 10);
        assertEquals(7, r[0]);
        assertEquals(3, r[1]);
        assertEquals(0, produtos.findById(p.getId()).orElseThrow().getQuantidade());
        assertEquals(List.of(), movimentos.produtosComSaldoDivergente());
    }

    @Test
    @DisplayName("Venda recusada (estoque insuficiente): a tela volta com o mesmo carrinho e o mesmo token")
    void naoDescartaOCarrinho() throws Exception {
        var p = estoque.criarNovo("Adesivo Raro " + System.nanoTime(), new BigDecimal("8"), 2, null);
        String token = UUID.randomUUID().toString();
        mvc.perform(post("/venda")
                        .sessionAttr("autenticado", true)
                        .param("token", token)
                        .param("qtd_" + p.getId(), "3")
                        .param("valorTotal", "20,00")
                        .param("motivoAjuste", "Desconto de amigo")
                        .param("pag_forma_0", "DINHEIRO"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Estoque insuficiente de " + p.getNome())))
                .andExpect(content().string(containsString("name=\"token\" value=\"" + token + "\"")))
                .andExpect(content().string(containsString("name=\"qtd_" + p.getId() + "\"")))
                .andExpect(content().string(containsString("value=\"3\"")))
                .andExpect(content().string(containsString("value=\"Desconto de amigo\"")))
                .andExpect(content()
                        .string(org.hamcrest.Matchers.matchesPattern("(?s).*value=\"DINHEIRO\"[^>]*checked.*")));
    }
}
