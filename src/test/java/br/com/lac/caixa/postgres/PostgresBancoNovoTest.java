package br.com.lac.caixa.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.repositorio.CategoriaRepository;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import br.com.lac.caixa.repositorio.VendaRepository;
import br.com.lac.caixa.servico.CaixaService;
import br.com.lac.caixa.servico.ConsultaHistorico;
import br.com.lac.caixa.servico.FechamentoService;
import br.com.lac.caixa.servico.PagamentoInformado;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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

/** Banco vazio: Flyway roda todas as migrações e o Hibernate valida o esquema no Postgres. */
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

    static boolean vendasCriadas;
    static long produtosAntes = -1;

    /**
     * Duas vendas feitas pela aplicação sobre o estoque semeado:
     * 01/06/2024 Pix R$ 12 (Bóton Capivara x2) e 02/06/2024 Dinheiro R$ 14
     * (Adesivo Capivara Grande x1 + Bóton Logo LAC x1).
     */
    static Produto comCategoria(Produto p, Categoria c) {
        p.setCategoria(c);
        return p;
    }

    @BeforeEach
    void criarVendas() {
        if (vendasCriadas) return;
        vendasCriadas = true;
        // Banco vazio fica vazio (DadosIniciais só no perfil local): o teste cria o próprio estoque.
        produtosAntes = produtos.count();
        var boton = categorias.save(new Categoria("Bóton"));
        var adesivo = categorias.save(new Categoria("Adesivo Grande"));
        long capivara = produtos.save(comCategoria(new Produto("Bóton Capivara", new BigDecimal("6.00"), 30), boton))
                .getId();
        long logo = produtos.save(comCategoria(new Produto("Bóton Logo LAC", new BigDecimal("6.00"), 30), boton))
                .getId();
        long grande = produtos.save(
                        comCategoria(new Produto("Adesivo Capivara Grande", new BigDecimal("8.00"), 25), adesivo))
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
                9,
                jdbc.queryForObject(
                        "select count(*) from flyway_schema_history where type = 'SQL' and success", Integer.class));
        assertEquals(0, produtosAntes, "nada semeado fora do perfil local");
        mvc.perform(get("/historico").sessionAttr("autenticado", true)).andExpect(status().isOk());
    }
}
