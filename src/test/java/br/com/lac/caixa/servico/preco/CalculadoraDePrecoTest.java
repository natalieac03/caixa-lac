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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CalculadoraDePrecoTest {
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

    Orcamento calcular(Produto p, int qtd, Promocao... promos) {
        return new CalculadoraDePreco().calcular(new Carrinho(List.of(new Carrinho.Linha(p, qtd))), List.of(promos));
    }

    void total(String valor, Orcamento o) {
        assertEquals(0, new BigDecimal(valor).compareTo(o.total()));
    }

    @Test
    void semPromocao() {
        total("18", calcular(produto(1, "6", cat(1)), 3));
    }

    @Test
    void aPartirDeAbaixoENoMinimo() {
        var c = cat(1);
        var p = produto(1, "6", c);
        var promo = promo(TipoPromocao.A_PARTIR_DE, "5", c, 2);
        total("6", calcular(p, 1, promo));
        total("10", calcular(p, 2, promo));
    }

    @Test
    void pacoteComSobra() {
        var c = cat(1);
        total("16", calcular(produto(1, "6", c), 3, promo(TipoPromocao.PACOTE, "10", c, 2)));
    }

    @Test
    void comboDuasCategorias() {
        var a = cat(1);
        var b = cat(2);
        var promo = new Promocao(
                List.of(new PromocaoItem(a, 2), new PromocaoItem(b, 1)), TipoPromocao.PACOTE, new BigDecimal("10"));
        total(
                "10",
                new CalculadoraDePreco()
                        .calcular(
                                new Carrinho(List.of(
                                        new Carrinho.Linha(produto(1, "6", a), 2),
                                        new Carrinho.Linha(produto(2, "4", b), 1))),
                                List.of(promo)));
    }

    @Test
    void produtoSemCategoria() {
        total("12", calcular(produto(1, "6", null), 2, promo(TipoPromocao.A_PARTIR_DE, "1", cat(1), 1)));
    }

    @Test
    void orcamentoImutavel() {
        var o = calcular(produto(1, "6", null), 1);
        assertThrows(UnsupportedOperationException.class, () -> o.itens().clear());
        assertThrows(UnsupportedOperationException.class, () -> o.descontos().clear());
    }

    @Test
    void promocaoNaoAumentaPreco() {
        var c = cat(1);
        total("18", calcular(produto(1, "6", c), 3, promo(TipoPromocao.A_PARTIR_DE, "7", c, 2)));
    }

    @Test
    void menorPrecoIndependeDaOrdem() {
        var c = cat(1);
        var p = produto(1, "6", c);
        var a = promo(TipoPromocao.A_PARTIR_DE, "5", c, 2);
        var b = promo(TipoPromocao.A_PARTIR_DE, "4", c, 2);
        total("12", calcular(p, 3, a, b));
        total("12", calcular(p, 3, b, a));
    }

    @Test
    void combinacaoExataDePacotes() {
        var c = cat(1);
        total(
                "5.60",
                calcular(
                        produto(1, "2", c),
                        4,
                        promo(TipoPromocao.PACOTE, "4", c, 3),
                        promo(TipoPromocao.PACOTE, "2.80", c, 2)));
    }
    // Regra antiga (somavam): 10 -> 8 pelo A_PARTIR_DE e o pacote ainda pegava esse 8: 8 - 4 + 3 = 7.
    // Regra nova: sem A_PARTIR_DE, o pacote leva a unidade de 10: 4 + 3 = 7 (mesmo valor, outro motivo).
    @Test
    void cadaUnidadeEmUmaPromocaoSoEPacoteLevaAMaisCara() {
        var c = cat(1);
        var carrinho = new Carrinho(
                List.of(new Carrinho.Linha(produto(1, "10", c), 1), new Carrinho.Linha(produto(2, "3", c), 1)));
        var o = new CalculadoraDePreco()
                .calcular(
                        carrinho,
                        List.of(promo(TipoPromocao.A_PARTIR_DE, "8", c, 2), promo(TipoPromocao.PACOTE, "4", c, 1)));
        total("7", o);
        assertEquals(0, new BigDecimal("10").compareTo(o.itens().get(0).precoUnitario()));
    }

    static List<Promocao> inverter(List<Promocao> l) {
        var c = new java.util.ArrayList<>(l);
        java.util.Collections.reverse(c);
        return c;
    }

    Promocao comId(Promocao p, long id) {
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    void aPartirDeEPacoteNaoSeSomamTresUnidades() {
        var c = cat(1);
        var p = produto(1, "6", c);
        var o = calcular(
                p,
                3,
                comId(promo(TipoPromocao.A_PARTIR_DE, "5", c, 2), 1),
                comId(promo(TipoPromocao.PACOTE, "12", c, 3), 2));
        total("12.00", o);
        assertEquals(1, o.descontos().size());
        assertTrue(o.descontos().get(0).descricao().startsWith("Pacote"));
        assertEquals(0, new BigDecimal("6").compareTo(o.itens().get(0).precoUnitario()));
    }

    @Test
    void aPartirDeEPacoteNaoSeSomamQuatroUnidades() {
        var c = cat(1);
        var p = produto(1, "6", c);
        // 4 x 5 = 20,00 contra 12 + 6 = 18,00: fica o pacote mais uma unidade normal.
        total(
                "18.00",
                calcular(
                        p,
                        4,
                        comId(promo(TipoPromocao.A_PARTIR_DE, "5", c, 2), 1),
                        comId(promo(TipoPromocao.PACOTE, "12", c, 3), 2)));
        // Com 6 unidades: 6 x 5 = 30 contra 2 pacotes = 24.
        total(
                "24.00",
                calcular(
                        p,
                        6,
                        comId(promo(TipoPromocao.A_PARTIR_DE, "5", c, 2), 1),
                        comId(promo(TipoPromocao.PACOTE, "12", c, 3), 2)));
    }

    @Test
    void aPartirDeVenceQuandoEMaisBarato() {
        var c = cat(1);
        var p = produto(1, "6", c);
        // 4 x 4 = 16 contra 12 + 6 = 18.
        var o = calcular(
                p,
                4,
                comId(promo(TipoPromocao.A_PARTIR_DE, "4", c, 2), 1),
                comId(promo(TipoPromocao.PACOTE, "12", c, 3), 2));
        total("16.00", o);
        assertEquals("A partir de: Produto 1", o.descontos().get(0).descricao());
    }

    @Test
    void aPartirDeSoNaCategoriaQueNaoEstaNoPacote() {
        var a = cat(1);
        var b = cat(2);
        var carrinho = new Carrinho(
                List.of(new Carrinho.Linha(produto(1, "6", a), 3), new Carrinho.Linha(produto(2, "8", b), 2)));
        var combo = comId(
                new Promocao(
                        List.of(new PromocaoItem(a, 1), new PromocaoItem(b, 1)),
                        TipoPromocao.PACOTE,
                        new BigDecimal("10")),
                2);
        // Bóton a 5 (A_PARTIR_DE) + 2 adesivos normais = 31; ou 2 combos (20) + 1 bóton normal (6) = 26; ou combo+...
        // 26 é o menor.
        total(
                "26.00",
                new CalculadoraDePreco()
                        .calcular(carrinho, List.of(comId(promo(TipoPromocao.A_PARTIR_DE, "5", a, 2), 1), combo)));
    }

    @Test
    void empateEDeterministicoPorId() {
        var c = cat(1);
        var p = produto(1, "6", c);
        // 3 x 4 = 12 empata com o pacote de 12: fica o cenário sem A_PARTIR_DE (o pacote).
        var apd = comId(promo(TipoPromocao.A_PARTIR_DE, "4", c, 2), 1);
        var pac = comId(promo(TipoPromocao.PACOTE, "12", c, 3), 2);
        for (var ordem : List.of(List.of(apd, pac), List.of(pac, apd))) {
            var o = new CalculadoraDePreco().calcular(new Carrinho(List.of(new Carrinho.Linha(p, 3))), ordem);
            total("12", o);
            assertTrue(o.descontos().get(0).descricao().startsWith("Pacote"));
        }
        // Dois pacotes com a mesma economia: fica o de menor id, nas duas ordens.
        var p5 = comId(promo(TipoPromocao.PACOTE, "10", c, 2), 5);
        var p3 = comId(promo(TipoPromocao.PACOTE, "10", c, 2), 3);
        for (var ordem : List.of(List.of(p5, p3), List.of(p3, p5))) {
            var o = new CalculadoraDePreco().calcular(new Carrinho(List.of(new Carrinho.Linha(p, 2))), ordem);
            assertEquals(1, o.descontos().size());
            total("10", o);
        }
    }

    @Test
    void cinquentaUnidadesEmCincoCategoriasEmMenosDe100ms() {
        var linhas = new java.util.ArrayList<Carrinho.Linha>();
        var promos = new java.util.ArrayList<Promocao>();
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
        long inicio = System.nanoTime();
        var o = new CalculadoraDePreco().calcular(carrinho, promos);
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        assertTrue(ms < 100, "levou " + ms + " ms");
        assertTrue(o.total().signum() > 0);
        assertEquals(
                0,
                o.total()
                        .compareTo(new CalculadoraDePreco()
                                .calcular(carrinho, inverter(promos))
                                .total()));
    }
}
