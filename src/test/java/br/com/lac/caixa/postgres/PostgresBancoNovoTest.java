package br.com.lac.caixa.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lac.caixa.migracao.Migracoes;
import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.repositorio.CategoriaRepository;
import br.com.lac.caixa.repositorio.MovimentoEstoqueRepository;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import br.com.lac.caixa.repositorio.VendaRepository;
import br.com.lac.caixa.servico.CaixaService;
import br.com.lac.caixa.servico.ConsultaHistorico;
import br.com.lac.caixa.servico.EstoqueService;
import br.com.lac.caixa.servico.FechamentoService;
import br.com.lac.caixa.servico.PagamentoInformado;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/** Banco vazio no Postgres: migrações, esquema e as consultas das telas. */
@ExtendWith(ExigeDocker.class)
@SpringBootTest(properties = {"spring.profiles.active=test"})
@AutoConfigureMockMvc
class PostgresBancoNovoTest {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        POSTGRES.start();
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @Autowired
    CaixaService caixa;

    @Autowired
    VendaRepository vendas;

    @Autowired
    ConsultaHistorico historico;

    @Autowired
    FechamentoService fechamento;

    @Autowired
    ProdutoRepository produtos;

    @Autowired
    CategoriaRepository categorias;

    @Autowired
    EstoqueService estoque;

    @Autowired
    MovimentoEstoqueRepository movimentos;

    static boolean vendasCriadas;
    static long produtosAntes = -1;

    @BeforeEach
    void criarVendas() {
        if (vendasCriadas) return;
        vendasCriadas = true;
        produtosAntes = produtos.count();
        var boton = categorias.save(new Categoria("Bóton"));
        var adesivo = categorias.save(new Categoria("Adesivo Grande"));
        long capivara = estoque.criarNovo("Bóton Capivara", new BigDecimal("6.00"), 30, boton)
                .getId();
        long logo = estoque.criarNovo("Bóton Logo LAC", new BigDecimal("6.00"), 30, boton)
                .getId();
        long grande = estoque.criarNovo("Adesivo Capivara Grande", new BigDecimal("8.00"), 25, adesivo)
                .getId();
        var a = caixa.registrarVenda(
                        Map.of(capivara, 2),
                        List.of(PagamentoInformado.total(FormaPagamento.PIX)),
                        null,
                        null,
                        UUID.randomUUID().toString())
                .venda();
        a.setDataHora(LocalDate.of(2024, 6, 1).atTime(10, 0));
        vendas.saveAndFlush(a);
        var b = caixa.registrarVenda(
                        Map.of(grande, 1, logo, 1),
                        List.of(PagamentoInformado.total(FormaPagamento.DINHEIRO)),
                        null,
                        null,
                        UUID.randomUUID().toString())
                .venda();
        b.setDataHora(LocalDate.of(2024, 6, 2).atTime(23, 59));
        vendas.saveAndFlush(b);
    }

    @ParameterizedTest(name = "forma={0} inicio={1} fim={2} produto={3}")
    @CsvSource({
        ",,,,                                     2, 4, 26",
        ",2024-06-01,2024-06-01,,                 1, 2, 12",
        ",2024-06-02,,,                           1, 2, 14",
        ",,2024-06-01,,                           1, 2, 12",
        "DINHEIRO,,,,                             1, 2, 14",
        "CREDITO,,,,                              0, 0, 0",
        ",,,logo,                                 1, 2, 14",
        ",,,capivara,                             2, 4, 26",
        "PIX,2024-06-01,2024-06-02,capivara,      1, 2, 12",
        "DINHEIRO,2024-06-01,2024-06-01,,         0, 0, 0"
    })
    void filtrosNoBancoNovo(
            String forma, String inicio, String fim, String produto, long vendas, long itens, String total)
            throws Exception {
        ConferenciaDeFiltros.conferir(mvc, historico, fechamento, forma, inicio, fim, produto, vendas, itens, total);
    }

    @Test
    void migraDoZeroEValidaComHibernate() throws Exception {
        assertEquals(
                Migracoes.quantidadePostgres(),
                jdbc.queryForObject(
                        "select count(*) from flyway_schema_history where type = 'SQL' and success", Integer.class));
        assertEquals(0, produtosAntes, "nada semeado fora do perfil local");
        mvc.perform(get("/historico").sessionAttr("autenticado", true)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Invariante no Postgres: soma dos movimentos = quantidade, com venda, cancelamento, compra e ajustes")
    void invarianteDosMovimentos() {
        var p = estoque.criarNovo("Bóton Invariante PG", new BigDecimal("6.00"), 10, null);
        var v = caixa.registrarVenda(
                        Map.of(p.getId(), 3),
                        List.of(PagamentoInformado.total(FormaPagamento.DINHEIRO)),
                        null,
                        null,
                        UUID.randomUUID().toString())
                .venda();

        caixa.cancelarVenda(v.getId(), "Ensaio");
        estoque.registrarCompra(p.getId(), 5, new BigDecimal("10.00"), LocalDate.now(), "fornecedor");
        estoque.adicionarUnidades(p.getId(), 2);
        estoque.removerUnidades(p.getId(), 4);

        assertEquals(List.of(), movimentos.produtosComSaldoDivergente());
        assertEquals(13, produtos.findById(p.getId()).orElseThrow().getQuantidade());
        assertEquals(
                List.of(
                        "ENTRADA_AJUSTE",
                        "SAIDA_VENDA",
                        "DEVOLUCAO_CANCELAMENTO",
                        "ENTRADA_COMPRA",
                        "ENTRADA_AJUSTE",
                        "SAIDA_AJUSTE"),
                jdbc.queryForList(
                        "select tipo from movimento_estoque where produto_id = ? order by id",
                        String.class,
                        p.getId()));
    }

    @Autowired
    br.com.lac.caixa.servico.EventoService eventos;

    @Autowired
    br.com.lac.caixa.servico.DespesaService despesas;

    @Autowired
    br.com.lac.caixa.servico.TaxaService taxas;

    @Test
    @DisplayName("Postgres: sobreposição de eventos, venda no evento, despesas por evento/período e taxa vigente")
    void eventosDespesasETaxas() {
        var e = eventos.criar(
                "Feira PG",
                java.time.LocalDateTime.of(2024, 6, 1, 9, 0),
                java.time.LocalDateTime.of(2024, 6, 1, 18, 0),
                null);
        org.junit.jupiter.api.Assertions.assertThrows(
                br.com.lac.caixa.dominio.RegraDeNegocioException.class,
                () -> eventos.criar(
                        "Cruza PG",
                        java.time.LocalDateTime.of(2024, 6, 1, 17, 0),
                        java.time.LocalDateTime.of(2024, 6, 1, 19, 0),
                        null));
        eventos.editar(e.getId(), "Feira PG", e.getInicio(), e.getFim(), "ok");
        var f = fechamento.consultar(br.com.lac.caixa.repositorio.FiltroHistorico.doEvento(eventos.periodo(e.getId())));
        assertEquals(1, f.quantidade());
        assertEquals(
                "Feira PG",
                eventos.doMomento(java.time.LocalDateTime.of(2024, 6, 1, 10, 0))
                        .orElseThrow()
                        .getNome());

        Long material = despesas.categorias().get(0).getId();
        despesas.criar(LocalDate.of(2024, 5, 31), "Lona", material, new BigDecimal("30"), e.getId(), null);
        despesas.criar(LocalDate.of(2024, 6, 1), "Avulsa", material, new BigDecimal("5"), null, null);
        assertEquals(
                0,
                new BigDecimal("30")
                        .compareTo(despesas.total(
                                new br.com.lac.caixa.servico.DespesaService.Filtro(e.getId(), null, null))));
        assertEquals(
                0,
                new BigDecimal("35")
                        .compareTo(
                                despesas.total(new br.com.lac.caixa.servico.DespesaService.Filtro(null, null, null))));
        assertEquals(
                1,
                despesas.filtrar(new br.com.lac.caixa.servico.DespesaService.Filtro(
                                null, LocalDate.of(2024, 6, 1), null))
                        .size());

        taxas.definir(FormaPagamento.CREDITO, new BigDecimal("3.5"), null, LocalDate.now());
        assertEquals(
                0,
                new BigDecimal("3.5")
                        .compareTo(taxas.vigente(FormaPagamento.CREDITO, LocalDate.now())
                                .orElseThrow()
                                .getPercentual()));
        assertEquals(0, taxas.vendasSemTaxaRegistrada());
    }

    @Autowired
    ConsultaHistorico consultaHistorico;

    @Autowired
    br.com.lac.caixa.servico.financas.FinancasService financas;

    @Test
    @DisplayName(
            "Postgres: evento aberto (fim nulo) nas consultas; Dia a dia com NOT EXISTS; iniciar com outro aberto recusado")
    void eventoAbertoEDiaADia() {
        var p = estoque.criarNovo("Bóton Aberto PG", new BigDecimal("6.00"), 50, null);
        var antes = caixa.registrarVenda(
                        Map.of(p.getId(), 1),
                        List.of(PagamentoInformado.total(FormaPagamento.PIX)),
                        null,
                        null,
                        UUID.randomUUID().toString())
                .venda();
        antes.setDataHora(java.time.LocalDateTime.now().minusMinutes(10));
        vendas.saveAndFlush(antes);
        var feira = eventos.iniciar("Feira PG aberta");
        try {
            org.junit.jupiter.api.Assertions.assertThrows(
                    br.com.lac.caixa.dominio.RegraDeNegocioException.class, () -> eventos.iniciar("Outra"));
            var durante = caixa.registrarVenda(
                            Map.of(p.getId(), 2),
                            List.of(PagamentoInformado.total(FormaPagamento.PIX)),
                            null,
                            null,
                            UUID.randomUUID().toString())
                    .venda();
            assertEquals(
                    feira.getId(),
                    eventos.doMomento(durante.getDataHora()).orElseThrow().getId());
            var hoje = LocalDate.now();
            var agora = java.time.LocalDateTime.now();
            var desde = antes.getDataHora().toLocalDate();
            var diaADia = new br.com.lac.caixa.repositorio.FiltroHistorico(null, desde, hoje, "").comDiaADia(agora);
            var ids = consultaHistorico
                    .consultar(diaADia, 0)
                    .pagina()
                    .map(v -> v.getId())
                    .getContent();
            assertTrue(ids.contains(antes.getId()) && !ids.contains(durante.getId()), ids.toString());
            var r = financas.resumo(br.com.lac.caixa.repositorio.FiltroFinancas.diaADia(desde, hoje, agora));
            assertTrue(r.vendas() >= 1);
            var doEvento = financas.resumo(
                    br.com.lac.caixa.repositorio.FiltroFinancas.doEvento(eventos.periodo(feira.getId())));
            assertEquals(1, doEvento.vendas());
            assertEquals(0, new BigDecimal("12.00").compareTo(doEvento.receitaLiquida()));
            org.junit.jupiter.api.Assertions.assertThrows(
                    br.com.lac.caixa.dominio.RegraDeNegocioException.class,
                    () -> eventos.criar("Cruza PG", feira.getInicio().minusHours(1), agora.plusHours(1), null));
        } finally {
            jdbc.update(
                    "update evento set fim = ? where id = ?",
                    java.time.LocalDateTime.now().plusSeconds(1),
                    feira.getId());
        }
    }

    @Autowired
    javax.sql.DataSource dataSource;

    @Test
    @DisplayName("Postgres: dois cliques em Iniciar ao mesmo tempo -> o banco recusa o segundo, com mensagem amigável")
    void doisAbertosAoMesmoTempo() throws Exception {
        jdbc.update("update evento set fim = now() + interval '1 second' where fim is null");
        try (var outra = dataSource.getConnection()) {
            outra.setAutoCommit(false);
            try (var s = outra.createStatement()) {
                s.execute(
                        "INSERT INTO evento (nome, inicio) VALUES ('Clique simultâneo', now() - interval '1 minute')");
            }
            var confirmar = new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    outra.commit();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            confirmar.start();
            var erro = org.junit.jupiter.api.Assertions.assertThrows(
                    br.com.lac.caixa.dominio.RegraDeNegocioException.class, () -> eventos.iniciar("Segundo clique"));
            confirmar.join();
            assertTrue(erro.getMessage().contains("em andamento"), erro.getMessage());
            assertEquals(1, jdbc.queryForObject("select count(*) from evento where fim is null", Integer.class));
        } finally {
            jdbc.update("update evento set fim = now() + interval '1 second' where fim is null");
        }
    }
}
