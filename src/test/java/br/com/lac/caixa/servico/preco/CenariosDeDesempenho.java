package br.com.lac.caixa.servico.preco;

import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.modelo.PromocaoItem;
import br.com.lac.caixa.modelo.TipoPromocao;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

/** Carrinhos grandes para os testes de desempenho da calculadora. */
final class CenariosDeDesempenho {

    record Cenario(Carrinho carrinho, List<Promocao> promocoes) {}

    private CenariosDeDesempenho() {}

    static Categoria categoria(long id) {
        var c = new Categoria("Categoria " + id);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    static Produto produto(long id, String preco, Categoria c) {
        var p = new Produto("Produto " + id, new BigDecimal(preco), 1000);
        p.setCategoria(c);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    static Promocao promocao(long id, TipoPromocao tipo, String preco, Object... categoriaEQuantidade) {
        var itens = new ArrayList<PromocaoItem>();
        for (int i = 0; i < categoriaEQuantidade.length; i += 2) {
            itens.add(new PromocaoItem((Categoria) categoriaEQuantidade[i], (Integer) categoriaEQuantidade[i + 1]));
        }
        var p = new Promocao(itens, tipo, new BigDecimal(preco));
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static void categoriasComPacote(
            int quantas, int unidades, List<Carrinho.Linha> linhas, List<Promocao> promocoes, long[] ids) {
        Categoria[] cats = new Categoria[quantas];
        for (int c = 0; c < quantas; c++) {
            cats[c] = categoria(c + 1);
            int caros = unidades / 2 + unidades % 2;
            linhas.add(new Carrinho.Linha(produto(100 + c, (6 + c) + ".00", cats[c]), caros));
            linhas.add(new Carrinho.Linha(produto(200 + c, (4 + c) + ".50", cats[c]), unidades - caros));
            promocoes.add(promocao(ids[0]++, TipoPromocao.PACOTE, (12 + 2 * c) + ".00", cats[c], 3));
        }
        promocoes.add(promocao(ids[0]++, TipoPromocao.PACOTE, "20.00", cats[0], 2, cats[1], 2));
        promocoes.add(promocao(ids[0]++, TipoPromocao.PACOTE, "18.00", cats[2], 1, cats[3], 2));
    }

    static Cenario seisPorDezComAPartirDe() {
        var linhas = new ArrayList<Carrinho.Linha>();
        var promocoes = new ArrayList<Promocao>();
        long[] ids = {1};
        categoriasComPacote(6, 10, linhas, promocoes, ids);
        for (int c = 0; c < 6; c++) {
            var cat = categoria(50 + c);
            linhas.add(new Carrinho.Linha(produto(300 + c, "8.00", cat), 2));
            promocoes.add(promocao(ids[0]++, TipoPromocao.A_PARTIR_DE, (5 + c % 2) + ".00", cat, 2));
        }
        return new Cenario(new Carrinho(linhas), promocoes);
    }

    static Cenario setePorSete() {
        var linhas = new ArrayList<Carrinho.Linha>();
        var promocoes = new ArrayList<Promocao>();
        long[] ids = {1};
        categoriasComPacote(7, 7, linhas, promocoes, ids);
        return new Cenario(new Carrinho(linhas), promocoes);
    }
}
