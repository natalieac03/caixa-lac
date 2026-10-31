package br.com.lac.caixa.financas;

import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.Evento;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.TipoPromocao;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.repositorio.CategoriaDespesaRepository;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import br.com.lac.caixa.repositorio.PromocaoRepository;
import br.com.lac.caixa.repositorio.VendaRepository;
import br.com.lac.caixa.servico.CaixaService;
import br.com.lac.caixa.servico.CategoriaService;
import br.com.lac.caixa.servico.DespesaService;
import br.com.lac.caixa.servico.EstoqueService;
import br.com.lac.caixa.servico.EventoService;
import br.com.lac.caixa.servico.FechamentoService;
import br.com.lac.caixa.servico.PagamentoInformado;
import br.com.lac.caixa.servico.PromocaoService;
import br.com.lac.caixa.servico.TaxaService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationContext;

/** Monta a massa fixa das finanças pelos serviços de verdade. */
public class MassaDeTeste {
    final FechamentoService fechamento;
    final CaixaService caixa;
    final EstoqueService estoque;
    final CategoriaService categorias;
    final PromocaoService promocoes;
    final PromocaoRepository repositorioDePromocoes;
    final TaxaService taxas;
    final EventoService eventos;
    final DespesaService despesas;
    final CategoriaDespesaRepository categoriasDeDespesa;
    final ProdutoRepository produtos;
    final VendaRepository vendas;

    public final Map<String, Produto> p = new HashMap<>();
    public final Map<String, Evento> e = new HashMap<>();
    public final Map<String, Categoria> c = new HashMap<>();

    public MassaDeTeste(ApplicationContext ctx) {
        fechamento = ctx.getBean(FechamentoService.class);
        caixa = ctx.getBean(CaixaService.class);
        estoque = ctx.getBean(EstoqueService.class);
        categorias = ctx.getBean(CategoriaService.class);
        promocoes = ctx.getBean(PromocaoService.class);
        repositorioDePromocoes = ctx.getBean(PromocaoRepository.class);
        taxas = ctx.getBean(TaxaService.class);
        eventos = ctx.getBean(EventoService.class);
        despesas = ctx.getBean(DespesaService.class);
        categoriasDeDespesa = ctx.getBean(CategoriaDespesaRepository.class);
        produtos = ctx.getBean(ProdutoRepository.class);
        vendas = ctx.getBean(VendaRepository.class);
    }

    static BigDecimal r(String v) {
        return new BigDecimal(v);
    }

    static LocalDateTime t(String texto) {
        return LocalDateTime.parse(texto);
    }

    Venda vender(String quando, Map<String, Integer> itens, List<PagamentoInformado> pagamentos, String totalManual) {
        Map<Long, Integer> quantidades = new LinkedHashMap<>();
        itens.forEach((nome, qtd) -> quantidades.put(p.get(nome).getId(), qtd));
        var v = caixa.registrarVenda(
                        quantidades,
                        pagamentos,
                        totalManual == null ? null : r(totalManual),
                        totalManual == null ? null : "Ensaio",
                        UUID.randomUUID().toString())
                .venda();
        v.setDataHora(t(quando));
        return vendas.saveAndFlush(v);
    }

    static Map<String, Integer> itens(Object... nomeEQuantidade) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < nomeEQuantidade.length; i += 2)
            m.put((String) nomeEQuantidade[i], (Integer) nomeEQuantidade[i + 1]);
        return m;
    }

    static List<PagamentoInformado> total(FormaPagamento forma) {
        return List.of(PagamentoInformado.total(forma));
    }

    void produto(String categoria, String nome, String preco, String custo) {
        p.put(nome, estoque.criarNovo(nome, r(preco), 50, c.get(categoria), custo == null ? null : r(custo)));
    }

    public MassaDeTeste montar() {
        for (String nome : List.of("Bóton", "Adesivo", "Camiseta", "Caneca", "Pôster")) {
            c.put(nome, categorias.obterOuCriar(nome));
        }
        if (produtos.findAll().stream().anyMatch(x -> x.getNome().equals("Bóton 1"))) {
            produtos.findAll().forEach(x -> p.put(x.getNome(), x));
            eventos.listar().forEach(x -> e.put(x.getNome(), x));
            return this;
        }
        produto("Bóton", "Bóton 1", "6.00", "2.00");
        produto("Bóton", "Bóton 2", "6.00", "2.50");
        produto("Bóton", "Bóton 3", "6.00", null);
        produto("Adesivo", "Adesivo 1", "8.00", "3.00");
        produto("Adesivo", "Adesivo 2", "8.00", "3.00");
        produto("Adesivo", "Adesivo 3", "4.00", "1.00");
        produto("Camiseta", "Camiseta 1", "50.00", "25.00");
        produto("Camiseta", "Camiseta 2", "50.00", "30.00");
        produto("Camiseta", "Camiseta 3", "45.00", null);
        produto("Caneca", "Caneca 1", "30.00", "12.00");
        produto("Caneca", "Caneca 2", "30.00", "12.00");
        produto("Caneca", "Caneca 3", "25.00", "10.00");
        produto("Pôster", "Pôster 1", "15.00", "5.00");
        produto("Pôster", "Pôster 2", "15.00", "5.00");
        produto("Pôster", "Pôster 3", "10.00", "2.00");
        promocoes.criar(Map.of(c.get("Bóton").getId(), 3), TipoPromocao.A_PARTIR_DE, r("5.00"));
        promocoes.criar(
                Map.of(c.get("Pôster").getId(), 1, c.get("Caneca").getId(), 1), TipoPromocao.PACOTE, r("40.00"));
        LocalDate hoje = LocalDate.now();
        taxas.definir(FormaPagamento.CREDITO, r("3"), null, hoje);
        taxas.definir(FormaPagamento.DEBITO, r("2"), r("0.10"), hoje);
        taxas.definir(FormaPagamento.PIX, r("1"), null, hoje);
        e.put("Feira 1", eventos.criar("Feira 1", t("2025-03-01T09:00"), t("2025-03-01T18:00"), null));
        e.put("Feira 2", eventos.criar("Feira 2", t("2025-03-08T09:00"), t("2025-03-08T18:00"), null));
        e.put("Feira 3", eventos.criar("Feira 3", t("2025-03-15T09:00"), t("2025-03-15T18:00"), null));

        vender("2025-03-01T09:00", itens("Bóton 1", 3), total(FormaPagamento.PIX), null);
        vender(
                "2025-03-01T10:30",
                itens("Camiseta 1", 1, "Caneca 1", 1),
                List.of(
                        new PagamentoInformado(FormaPagamento.CREDITO, r("50"), null, null, null, 0),
                        new PagamentoInformado(FormaPagamento.DINHEIRO, r("30"), null, null, null, 0)),
                null);
        vender("2025-03-01T12:15", itens("Adesivo 1", 2), total(FormaPagamento.DINHEIRO), "0");
        var v4 = vender("2025-03-01T14:00", itens("Pôster 1", 2), total(FormaPagamento.PIX), null);
        caixa.cancelarVenda(v4.getId(), "Ensaio");
        vender("2025-03-01T17:59", itens("Camiseta 3", 1), total(FormaPagamento.DEBITO), "40");
        vender("2025-03-01T18:00", itens("Caneca 3", 1), total(FormaPagamento.DINHEIRO), null);
        vender("2025-03-05T16:20", itens("Pôster 2", 1, "Caneca 2", 1), total(FormaPagamento.PIX), null);
        vender("2025-03-08T11:00", itens("Bóton 2", 1, "Bóton 3", 1), total(FormaPagamento.PIX), null);
        vender("2025-03-08T15:00", itens("Adesivo 2", 1, "Adesivo 3", 2), total(FormaPagamento.CREDITO), "17");

        Long material = categoriaDespesa("Material");
        despesas.criar(
                LocalDate.of(2025, 2, 28),
                "Lona",
                material,
                r("30"),
                e.get("Feira 1").getId(),
                null);
        despesas.criar(
                LocalDate.of(2025, 3, 1),
                "Mesa",
                categoriaDespesa("Taxa de espaço"),
                r("50"),
                e.get("Feira 1").getId(),
                null);
        despesas.criar(LocalDate.of(2025, 3, 5), "Uber", categoriaDespesa("Transporte"), r("20"), null, null);
        despesas.criar(
                LocalDate.of(2025, 3, 8),
                "Lanche",
                categoriaDespesa("Alimentação"),
                r("15.50"),
                e.get("Feira 2").getId(),
                null);

        repositorioDePromocoes.findAll().stream()
                .filter(x -> x.getTipo() == TipoPromocao.PACOTE)
                .forEach(x -> promocoes.remover(x.getId()));
        estoque.remover(p.get("Pôster 2").getId());
        return this;
    }

    Long categoriaDespesa(String nome) {
        return categoriasDeDespesa.findAll().stream()
                .filter(x -> x.getNome().equals(nome))
                .findFirst()
                .orElseThrow()
                .getId();
    }
}
