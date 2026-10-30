package br.com.lac.caixa.servico.preco;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.modelo.PromocaoItem;
import br.com.lac.caixa.modelo.TipoPromocao;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CalculadoraDePrecoTest {

    // ---------- montagem ----------

    Categoria cat(long id) {
        var c = new Categoria("Categoria " + id);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    Produto produto(long id, String preco, Categoria c) {
        var p = new Produto("Produto " + id, new BigDecimal(preco), 100);
        p.setCategoria(c);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    Promocao promo(TipoPromocao tipo, String preco, Categoria c, int qtd) {
        return new Promocao(List.of(new PromocaoItem(c, qtd)), tipo, new BigDecimal(preco));
    }

    Promocao comId(Promocao p, long id) {
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    static List<Promocao> inverter(List<Promocao> l) {
        var c = new ArrayList<>(l);
        Collections.reverse(c);
        return c;
    }

    Orcamento calcular(Produto p, int qtd, Promocao... promos) {
        return new CalculadoraDePreco().calcular(new Carrinho(List.of(new Carrinho.Linha(p, qtd))), List.of(promos));
    }

    Orcamento calcular(Carrinho carrinho, List<Promocao> promos) {
        return new CalculadoraDePreco().calcular(carrinho, promos);
    }

    static void assertTotal(String esperado, Orcamento o) {
        assertEquals(0, new BigDecimal(esperado).compareTo(o.total()), "total " + o.total());
    }

    // ---------- regras básicas ----------

    @Test
    @DisplayName("Sem promoção, o total é a soma dos preços normais")
    void semPromocao() {
        // Arrange
        var p = produto(1, "6", cat(1));

        // Act
        var o = calcular(p, 3);

        // Assert
        assertTotal("18", o);
    }

    @Test
    @DisplayName("A_PARTIR_DE: abaixo do mínimo cobra o normal; no mínimo, todas as unidades no preço promocional")
    void aPartirDeAbaixoENoMinimo() {
        // Arrange
        var c = cat(1);
        var p = produto(1, "6", c);
        var aPartirDe2Por5 = promo(TipoPromocao.A_PARTIR_DE, "5", c, 2);

        // Act
        var umaUnidade = calcular(p, 1, aPartirDe2Por5);
        var duasUnidades = calcular(p, 2, aPartirDe2Por5);

        // Assert
        assertTotal("6", umaUnidade);
        assertTotal("10", duasUnidades);
    }

    @Test
    @DisplayName("PACOTE: o que sobra do pacote sai no preço normal (2 por R$ 10 + 1 de R$ 6 = R$ 16)")
    void pacoteComSobra() {
        // Arrange
        var c = cat(1);
        var p = produto(1, "6", c);
        var pacote2Por10 = promo(TipoPromocao.PACOTE, "10", c, 2);

        // Act
        var o = calcular(p, 3, pacote2Por10);

        // Assert
        assertTotal("16", o);
    }

    @Test
    @DisplayName("Combo de duas categorias: 2 + 1 unidades saem pelo preço fechado do pacote")
    void comboDuasCategorias() {
        // Arrange
        var a = cat(1);
        var b = cat(2);
        var combo = new Promocao(
                List.of(new PromocaoItem(a, 2), new PromocaoItem(b, 1)), TipoPromocao.PACOTE, new BigDecimal("10"));
        var carrinho = new Carrinho(
                List.of(new Carrinho.Linha(produto(1, "6", a), 2), new Carrinho.Linha(produto(2, "4", b), 1)));

        // Act
        var o = calcular(carrinho, List.of(combo));

        // Assert
        assertTotal("10", o);
    }

    @Test
    @DisplayName("Produto sem categoria não entra em promoção nenhuma")
    void produtoSemCategoria() {
        // Arrange
        var semCategoria = produto(1, "6", null);
        var promocaoDeOutraCategoria = promo(TipoPromocao.A_PARTIR_DE, "1", cat(1), 1);

        // Act
        var o = calcular(semCategoria, 2, promocaoDeOutraCategoria);

        // Assert
        assertTotal("12", o);
    }

    @Test
    @DisplayName("O orçamento é imutável: itens e descontos não podem ser alterados")
    void orcamentoImutavel() {
        // Arrange / Act
        var o = calcular(produto(1, "6", null), 1);

        // Assert
        assertThrows(UnsupportedOperationException.class, () -> o.itens().clear());
        assertThrows(UnsupportedOperationException.class, () -> o.descontos().clear());
    }

    @Test
    @DisplayName("A_PARTIR_DE mais cara que o preço normal não se aplica (bug a: R$ 7 contra R$ 6)")
    void promocaoNaoAumentaPreco() {
        // Arrange
        var c = cat(1);
        var p = produto(1, "6", c);
        var aPartirDe2Por7 = promo(TipoPromocao.A_PARTIR_DE, "7", c, 2);

        // Act
        var o = calcular(p, 3, aPartirDe2Por7);

        // Assert
        assertTotal("18", o);
    }

    @Test
    @DisplayName("Duas A_PARTIR_DE na mesma categoria: vence a de menor preço, em qualquer ordem")
    void menorPrecoIndependeDaOrdem() {
        // Arrange
        var c = cat(1);
        var p = produto(1, "6", c);
        var por5 = promo(TipoPromocao.A_PARTIR_DE, "5", c, 2);
        var por4 = promo(TipoPromocao.A_PARTIR_DE, "4", c, 2);

        // Act
        var cincoPrimeiro = calcular(p, 3, por5, por4);
        var quatroPrimeiro = calcular(p, 3, por4, por5);

        // Assert
        assertTotal("12", cincoPrimeiro);
        assertTotal("12", quatroPrimeiro);
    }

    @Test
    @DisplayName("Pacotes: combinação exata de menor total (dois \"2 por 2,80\" em vez de \"3 por 4,00\" + 1)")
    void combinacaoExataDePacotes() {
        // Arrange
        var c = cat(1);
        var boton = produto(1, "2", c);
        var tresPor4 = promo(TipoPromocao.PACOTE, "4", c, 3);
        var doisPor280 = promo(TipoPromocao.PACOTE, "2.80", c, 2);

        // Act
        var o = calcular(boton, 4, tresPor4, doisPor280);

        // Assert
        assertTotal("5.60", o);
    }

    // ---------- uma unidade, uma promoção ----------

    // Regra antiga (somavam): 10 -> 8 pelo A_PARTIR_DE e o pacote ainda pegava esse 8: 8 - 4 + 3 = 7.
    // Regra nova: sem A_PARTIR_DE, o pacote leva a unidade de 10: 4 + 3 = 7 (mesmo valor, outro motivo).
    @Test
    @DisplayName("Cada unidade entra em uma promoção só, e o pacote leva a unidade mais cara")
    void cadaUnidadeEmUmaPromocaoSoEPacoteLevaAMaisCara() {
        // Arrange
        var c = cat(1);
        var carrinho = new Carrinho(
                List.of(new Carrinho.Linha(produto(1, "10", c), 1), new Carrinho.Linha(produto(2, "3", c), 1)));
        var promos = List.of(promo(TipoPromocao.A_PARTIR_DE, "8", c, 2), promo(TipoPromocao.PACOTE, "4", c, 1));

        // Act
        var o = calcular(carrinho, promos);

        // Assert
        assertTotal("7", o);
        assertEquals(0, new BigDecimal("10").compareTo(o.itens().get(0).precoUnitario()));
    }

    @Test
    @DisplayName("A_PARTIR_DE \"2 por 5\" + PACOTE \"3 por 12\", 3 unidades de R$ 6: R$ 12,00 (só o pacote)")
    void aPartirDeEPacoteNaoSeSomamTresUnidades() {
        // Arrange
        var c = cat(1);
        var p = produto(1, "6", c);
        var aPartirDe = comId(promo(TipoPromocao.A_PARTIR_DE, "5", c, 2), 1);
        var pacote = comId(promo(TipoPromocao.PACOTE, "12", c, 3), 2);

        // Act
        var o = calcular(p, 3, aPartirDe, pacote);

        // Assert
        assertTotal("12.00", o);
        assertEquals(1, o.descontos().size());
        assertTrue(o.descontos().get(0).descricao().startsWith("Pacote"));
        assertEquals(0, new BigDecimal("6").compareTo(o.itens().get(0).precoUnitario()));
    }

    @Test
    @DisplayName(
            "Mesma promoção com 4 unidades: o menor entre 4 x 5 = 20 e 12 + 6 = 18, ou seja R$ 18,00 (e 6 un. = R$ 24)")
    void aPartirDeEPacoteNaoSeSomamQuatroUnidades() {
        // Arrange
        var c = cat(1);
        var p = produto(1, "6", c);
        var aPartirDe = comId(promo(TipoPromocao.A_PARTIR_DE, "5", c, 2), 1);
        var pacote = comId(promo(TipoPromocao.PACOTE, "12", c, 3), 2);

        // Act
        var quatro = calcular(p, 4, aPartirDe, pacote);
        var seis = calcular(p, 6, aPartirDe, pacote);

        // Assert
        assertTotal("18.00", quatro); // pacote + uma unidade normal
        assertTotal("24.00", seis); // 2 pacotes contra 6 x 5 = 30
    }

    @Test
    @DisplayName("A_PARTIR_DE vence o pacote quando sai mais barato (4 x 4 = 16 contra 12 + 6 = 18)")
    void aPartirDeVenceQuandoEMaisBarato() {
        // Arrange
        var c = cat(1);
        var p = produto(1, "6", c);
        var aPartirDe2Por4 = comId(promo(TipoPromocao.A_PARTIR_DE, "4", c, 2), 1);
        var pacote3Por12 = comId(promo(TipoPromocao.PACOTE, "12", c, 3), 2);

        // Act
        var o = calcular(p, 4, aPartirDe2Por4, pacote3Por12);

        // Assert
        assertTotal("16.00", o);
        assertEquals("A partir de: Produto 1", o.descontos().get(0).descricao());
    }

    @Test
    @DisplayName("Combo entre categorias ganha da A_PARTIR_DE quando o total fica menor (26 contra 31)")
    void aPartirDeSoNaCategoriaQueNaoEstaNoPacote() {
        // Arrange: 3 bótons de 6 e 2 adesivos de 8; bóton "a partir de 2 por 5"; combo bóton + adesivo por 10
        var a = cat(1);
        var b = cat(2);
        var carrinho = new Carrinho(
                List.of(new Carrinho.Linha(produto(1, "6", a), 3), new Carrinho.Linha(produto(2, "8", b), 2)));
        var aPartirDe = comId(promo(TipoPromocao.A_PARTIR_DE, "5", a, 2), 1);
        var combo = comId(
                new Promocao(
                        List.of(new PromocaoItem(a, 1), new PromocaoItem(b, 1)),
                        TipoPromocao.PACOTE,
                        new BigDecimal("10")),
                2);

        // Act
        var o = calcular(carrinho, List.of(aPartirDe, combo));

        // Assert: bótons a 5 + adesivos normais = 31; 2 combos (20) + 1 bóton normal (6) = 26
        assertTotal("26.00", o);
    }

    @Test
    @DisplayName("Empate é decidido por id, independentemente da ordem de cadastro")
    void empateEDeterministicoPorId() {
        // Arrange
        var c = cat(1);
        var p = produto(1, "6", c);
        // 3 x 4 = 12 empata com o pacote de 12: fica o cenário sem A_PARTIR_DE (o pacote).
        var apd = comId(promo(TipoPromocao.A_PARTIR_DE, "4", c, 2), 1);
        var pac = comId(promo(TipoPromocao.PACOTE, "12", c, 3), 2);
        // Dois pacotes com a mesma economia: fica o de menor id.
        var p5 = comId(promo(TipoPromocao.PACOTE, "10", c, 2), 5);
        var p3 = comId(promo(TipoPromocao.PACOTE, "10", c, 2), 3);

        for (var ordem : List.of(List.of(apd, pac), List.of(pac, apd))) {
            // Act
            var o = calcular(new Carrinho(List.of(new Carrinho.Linha(p, 3))), ordem);

            // Assert
            assertTotal("12", o);
            assertTrue(o.descontos().get(0).descricao().startsWith("Pacote"));
        }
        for (var ordem : List.of(List.of(p5, p3), List.of(p3, p5))) {
            // Act
            var o = calcular(new Carrinho(List.of(new Carrinho.Linha(p, 2))), ordem);

            // Assert
            assertEquals(1, o.descontos().size());
            assertTotal("10", o);
        }
    }

    @Test
    @DisplayName("50 unidades em 5 categorias calculam em menos de 100 ms, com o mesmo total em qualquer ordem")
    void cinquentaUnidadesEmCincoCategoriasEmMenosDe100ms() {
        // Arrange
        var linhas = new ArrayList<Carrinho.Linha>();
        var promos = new ArrayList<Promocao>();
        long id = 1;
        Categoria[] cats = new Categoria[5];
        for (int c = 0; c < 5; c++) {
            cats[c] = cat(c + 1);
            linhas.add(new Carrinho.Linha(produto(10 + c, (6 + c) + ".00", cats[c]), 6));
            linhas.add(new Carrinho.Linha(produto(20 + c, (4 + c) + ".50", cats[c]), 4));
            promos.add(comId(promo(TipoPromocao.A_PARTIR_DE, (4 + c) + ".00", cats[c], 3), id++));
            promos.add(comId(promo(TipoPromocao.PACOTE, (12 + 2 * c) + ".00", cats[c], 3), id++));
        }
        promos.add(comId(
                new Promocao(
                        List.of(new PromocaoItem(cats[0], 2), new PromocaoItem(cats[1], 2)),
                        TipoPromocao.PACOTE,
                        new BigDecimal("20")),
                id++));
        promos.add(comId(
                new Promocao(
                        List.of(
                                new PromocaoItem(cats[2], 1),
                                new PromocaoItem(cats[3], 1),
                                new PromocaoItem(cats[4], 1)),
                        TipoPromocao.PACOTE,
                        new BigDecimal("18")),
                id++));
        var carrinho = new Carrinho(linhas);
        assertEquals(50, linhas.stream().mapToInt(Carrinho.Linha::quantidade).sum());

        // Act
        long inicio = System.nanoTime();
        var o = calcular(carrinho, promos);
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        // Assert
        assertTrue(ms < 100, "levou " + ms + " ms");
        assertTrue(o.total().signum() > 0);
        assertEquals(0, o.total().compareTo(calcular(carrinho, inverter(promos)).total()));
    }
}
