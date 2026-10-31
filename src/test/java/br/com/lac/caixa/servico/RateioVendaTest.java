package br.com.lac.caixa.servico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.modelo.TipoPromocao;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.repositorio.PromocaoRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Venda nova: bruto, desconto rateado e líquido por item, e descontos congelados. */
@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:rateio_venda;DB_CLOSE_DELAY=-1"
        })
class RateioVendaTest {
    @Autowired
    CaixaService caixa;

    @Autowired
    EstoqueService estoque;

    @Autowired
    CategoriaService categorias;

    @Autowired
    PromocaoService promocoes;

    @Autowired
    PromocaoRepository repositorioDePromocoes;

    @Autowired
    JdbcTemplate jdbc;

    static BigDecimal r(String v) {
        return new BigDecimal(v);
    }

    Venda vender(Map<Long, Integer> quantidades, BigDecimal totalManual) {
        return caixa.registrarVenda(
                        quantidades,
                        List.of(PagamentoInformado.total(FormaPagamento.DINHEIRO)),
                        totalManual,
                        totalManual == null ? null : "Ensaio",
                        UUID.randomUUID().toString())
                .venda();
    }

    BigDecimal[] item(Venda v, Produto p) {
        return jdbc.queryForObject(
                "select valor_bruto, desconto_rateado, valor_liquido from item_venda where venda_id = ? and produto_id = ?",
                (rs, n) -> new BigDecimal[] {rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3)},
                v.getId(),
                p.getId());
    }

    BigDecimal somaLiquidos(Venda v) {
        return jdbc.queryForObject(
                "select sum(valor_liquido) from item_venda where venda_id = ?", BigDecimal.class, v.getId());
    }

    static void assertValor(String esperado, BigDecimal valor) {
        assertEquals(0, r(esperado).compareTo(valor), "esperado " + esperado + ", veio " + valor);
    }

    Promocao ultimaPromocao() {
        return repositorioDePromocoes.findAll().stream()
                .max((a, b) -> Long.compare(a.getId(), b.getId()))
                .orElseThrow();
    }

    Categoria categoria(String nome) {
        return categorias.obterOuCriar(nome + " " + System.nanoTime());
    }

    @Test
    @DisplayName("A_PARTIR_DE: desconto rateado pelo bruto, soma dos líquidos = cobrado, desconto com id da promoção")
    void aPartirDe() {
        Categoria boton = categoria("Bóton Rateio");
        var b = estoque.criarNovo("Bóton Rateio", r("6"), 100, boton);
        var a = estoque.criarNovo("Adesivo Rateio", r("8"), 100, categoria("Adesivo Rateio"));
        promocoes.criar(Map.of(boton.getId(), 3), TipoPromocao.A_PARTIR_DE, r("5"));
        Promocao promo = ultimaPromocao();

        var quantidades = new LinkedHashMap<Long, Integer>();
        quantidades.put(b.getId(), 3);
        quantidades.put(a.getId(), 1);
        Venda v = vender(quantidades, null);

        assertValor("23", v.getValorCobrado());
        var ib = item(v, b);
        var ia = item(v, a);
        assertValor("18.00", ib[0]);
        assertValor("2.08", ib[1]);
        assertValor("15.92", ib[2]);
        assertValor("8.00", ia[0]);
        assertValor("0.92", ia[1]);
        assertValor("7.08", ia[2]);
        assertValor("23", somaLiquidos(v));
        var desconto = jdbc.queryForMap(
                "select promocao_id, descricao, valor from venda_desconto where venda_id = ?", v.getId());
        assertEquals(promo.getId(), ((Number) desconto.get("PROMOCAO_ID")).longValue());
        assertEquals("A partir de: Bóton Rateio", desconto.get("DESCRICAO"));
        assertValor("3", (BigDecimal) desconto.get("VALOR"));
        assertFalse(jdbc.queryForObject("select rateio_retroativo from venda where id = ?", Boolean.class, v.getId()));

        promocoes.remover(promo.getId());

        var depois =
                jdbc.queryForMap("select promocao_id, descricao from venda_desconto where venda_id = ?", v.getId());
        assertNull(depois.get("PROMOCAO_ID"));
        assertEquals("A partir de: Bóton Rateio", depois.get("DESCRICAO"));
    }

    @Test
    @DisplayName("Pacote: o desconto do pacote é rateado pelo bruto e guarda o id do pacote")
    void pacote() {
        Categoria boton = categoria("Bóton Pacote");
        Categoria adesivo = categoria("Adesivo Pacote");
        var b = estoque.criarNovo("Bóton Pacote", r("6"), 100, boton);
        var a = estoque.criarNovo("Adesivo Pacote", r("8"), 100, adesivo);
        promocoes.criar(Map.of(boton.getId(), 2, adesivo.getId(), 1), TipoPromocao.PACOTE, r("15"));
        Promocao pacote = ultimaPromocao();

        Venda v = vender(Map.of(b.getId(), 2, a.getId(), 1), null);

        assertValor("15", v.getValorCobrado());
        assertValor("3.00", item(v, b)[1]);
        assertValor("2.00", item(v, a)[1]);
        assertValor("15", somaLiquidos(v));
        assertEquals(
                pacote.getId(),
                jdbc.queryForObject(
                        "select promocao_id from venda_desconto where venda_id = ?", Long.class, v.getId()));
        promocoes.remover(pacote.getId());
    }

    @Test
    @DisplayName("Ajuste manual para menos e para mais entra no rateio, com sinal; cortesia zera os líquidos")
    void ajustesECortesia() {
        var p6 = estoque.criarNovo("Caneca Ajuste", r("6"), 100, null);
        var p12 = estoque.criarNovo("Camiseta Ajuste", r("12"), 100, null);
        Map<Long, Integer> carrinho = Map.of(p6.getId(), 1, p12.getId(), 1);

        Venda menos = vender(carrinho, r("17"));
        assertValor("0.33", item(menos, p6)[1]);
        assertValor("0.67", item(menos, p12)[1]);
        assertValor("17", somaLiquidos(menos));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "select count(*) from venda_desconto where venda_id = ?", Integer.class, menos.getId()),
                "ajuste manual não é desconto promocional");

        Venda mais = vender(carrinho, r("20"));
        assertValor("-0.67", item(mais, p6)[1]);
        assertValor("6.67", item(mais, p6)[2]);
        assertValor("-1.33", item(mais, p12)[1]);
        assertValor("13.33", item(mais, p12)[2]);
        assertValor("20", somaLiquidos(mais));

        Venda cortesia = vender(carrinho, BigDecimal.ZERO);
        assertValor("0", item(cortesia, p6)[2]);
        assertValor("0", item(cortesia, p12)[2]);
        assertValor("6", item(cortesia, p6)[1]);
        assertValor("12", item(cortesia, p12)[1]);
    }

    @Test
    @DisplayName("Dízima: R$ 1,00 de desconto em 3 itens iguais -> 0,34 + 0,33 + 0,33")
    void dizima() {
        var x = estoque.criarNovo("Bóton Dízima X", r("6"), 100, null);
        var y = estoque.criarNovo("Bóton Dízima Y", r("6"), 100, null);
        var z = estoque.criarNovo("Bóton Dízima Z", r("6"), 100, null);

        Venda v = vender(Map.of(x.getId(), 1, y.getId(), 1, z.getId(), 1), r("17"));

        List<BigDecimal> partes = jdbc.queryForList(
                "select desconto_rateado from item_venda where venda_id = ? order by desconto_rateado desc",
                BigDecimal.class,
                v.getId());
        assertEquals(3, partes.size());
        assertValor("0.34", partes.get(0));
        assertValor("0.33", partes.get(1));
        assertValor("0.33", partes.get(2));
        assertValor("17", somaLiquidos(v));
    }

    @Test
    @DisplayName("Propriedade: 300 vendas aleatórias (semente fixa), com promoções e ajustes, sempre fecham")
    void propriedade() {
        Random aleatorio = new Random(424242L);
        Categoria c1 = categoria("Bóton Prop");
        Categoria c2 = categoria("Adesivo Prop");
        List<Produto> lista = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String preco =
                    String.valueOf(1 + aleatorio.nextInt(30)) + "." + String.format("%02d", aleatorio.nextInt(100));
            lista.add(estoque.criarNovo("Prop " + i, r(preco), 100_000, i % 2 == 0 ? c1 : c2));
        }
        promocoes.criar(Map.of(c1.getId(), 3), TipoPromocao.A_PARTIR_DE, r("2.50"));
        Long aPartirDe = ultimaPromocao().getId();
        promocoes.criar(Map.of(c1.getId(), 1, c2.getId(), 2), TipoPromocao.PACOTE, r("9.99"));
        Long pacote = ultimaPromocao().getId();
        List<Long> vendas = new ArrayList<>();

        for (int k = 0; k < 300; k++) {
            Map<Long, Integer> carrinho = new LinkedHashMap<>();
            int linhas = 1 + aleatorio.nextInt(lista.size());
            for (int i = 0; i < linhas; i++) {
                carrinho.put(lista.get(aleatorio.nextInt(lista.size())).getId(), 1 + aleatorio.nextInt(5));
            }
            BigDecimal calculado = caixa.previa(carrinho).total();
            BigDecimal manual = switch (aleatorio.nextInt(4)) {
                case 0 -> BigDecimal.ZERO;
                case 1 -> calculado.add(BigDecimal.valueOf(aleatorio.nextInt(500), 2));
                case 2 ->
                    calculado
                            .multiply(BigDecimal.valueOf(aleatorio.nextInt(100), 2))
                            .setScale(2, java.math.RoundingMode.DOWN);
                default -> null;
            };
            vendas.add(vender(carrinho, manual).getId());
        }

        for (Long id : vendas) {
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "select count(*) from venda v where v.id = ? and v.valor_cobrado <>"
                                    + " (select sum(i.valor_liquido) from item_venda i where i.venda_id = v.id)",
                            Integer.class,
                            id),
                    "venda " + id);
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "select count(*) from item_venda where venda_id = ?"
                                    + " and (valor_bruto is null or desconto_rateado + valor_liquido <> valor_bruto)",
                            Integer.class,
                            id),
                    "venda " + id);
        }
        assertTrue(jdbc.queryForObject(
                        "select count(*) from venda_desconto where promocao_id = ?", Integer.class, aPartirDe)
                > 0);
        assertTrue(
                jdbc.queryForObject("select count(*) from venda_desconto where promocao_id = ?", Integer.class, pacote)
                        > 0);
        assertEquals(
                0,
                jdbc.queryForObject(
                        "select count(*) from venda v where v.id between ? and ? and coalesce(v.desconto, 0) <>"
                                + " coalesce((select sum(d.valor) from venda_desconto d where d.venda_id = v.id), 0)",
                        Integer.class,
                        vendas.get(0),
                        vendas.get(vendas.size() - 1)));
        promocoes.remover(aPartirDe);
        promocoes.remover(pacote);
    }
}
