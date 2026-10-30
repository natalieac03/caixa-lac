package br.com.lac.caixa.servico;

import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.repositorio.CategoriaRepository;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import br.com.lac.caixa.repositorio.VendaRepository;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:auditoria;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AuditoriaHttpTest {
    static final String IP = "203.0.113.9";

    @Autowired MockMvc mvc;
    @Autowired ProdutoRepository produtos;
    @Autowired CategoriaRepository categorias;
    @Autowired VendaRepository vendas;

    final ListAppender<ILoggingEvent> linhas = new ListAppender<>();
    Produto p;

    @BeforeEach
    void preparar() {
        linhas.start();
        ((Logger) LoggerFactory.getLogger("auditoria")).addAppender(linhas);
        p = produtos.save(new Produto("Auditado", new BigDecimal("6"), 10));
    }

    @AfterEach
    void soltar() {
        ((Logger) LoggerFactory.getLogger("auditoria")).detachAppender(linhas);
    }

    MockHttpServletRequestBuilder doIp(MockHttpServletRequestBuilder req) {
        return req.sessionAttr("autenticado", true).with(r -> { r.setRemoteAddr(IP); return r; });
    }

    List<String> eventos(String evento) {
        return linhas.list.stream().map(ILoggingEvent::getFormattedMessage)
                .filter(l -> l.startsWith("evento=" + evento + " ")).toList();
    }

    @Test
    @DisplayName("Venda com ajuste gera uma linha de venda e uma de ajuste, com id, valores e IP")
    void vendaEAjuste() throws Exception {
        String token = UUID.randomUUID().toString();

        mvc.perform(doIp(post("/venda")).param("token", token).param("qtd_" + p.getId(), "3")
                .param("pag_forma_0", "PIX").param("valorTotal", "15").param("motivoAjuste", "Amigo da LAC"));

        long id = vendas.findByToken(token).orElseThrow().getId();
        assertEquals(List.of("evento=venda id=" + id + " ip=" + IP + " itens=\"Auditado x3\" calculado=18.00 desconto=0.00"
                + " cobrado=15.00 pagamento=\"Pix R$ 15,00\""), eventos("venda"));
        assertEquals(List.of("evento=ajuste_valor id=" + id + " ip=" + IP + " antes=18.00 depois=15.00 motivo=\"Amigo da LAC\""),
                eventos("ajuste_valor"));
    }

    @Test
    @DisplayName("Correção de data e cancelamento entram no log com antes e depois")
    void dataECancelamento() throws Exception {
        String token = UUID.randomUUID().toString();
        mvc.perform(doIp(post("/venda")).param("token", token).param("qtd_" + p.getId(), "1").param("pag_forma_0", "PIX"));
        long id = vendas.findByToken(token).orElseThrow().getId();

        mvc.perform(doIp(post("/historico/" + id + "/data")).param("data", "2030-01-02").param("hora", "10:30"));
        mvc.perform(doIp(post("/historico/" + id + "/cancelar")).param("motivo", "Desistiu"));

        String data = eventos("correcao_data").get(0);
        assertTrue(data.startsWith("evento=correcao_data id=" + id + " ip=" + IP + " antes="), data);
        assertTrue(data.endsWith(" depois=2030-01-02T10:30"), data);
        assertEquals(List.of("evento=cancelamento id=" + id + " ip=" + IP + " cobrado=6.00 motivo=Desistiu itens=\"Auditado x1\""),
                eventos("cancelamento"));
    }

    @Test
    @DisplayName("Edição de cadastro registra antes e depois (produto e categoria)")
    void edicaoDeCadastro() throws Exception {
        var cat = categorias.save(new Categoria("Auditoria " + p.getId()));

        mvc.perform(doIp(post("/estoque/" + p.getId() + "/editar")).param("nome", "Auditado Novo").param("preco", "7,50")
                .param("categoriaId", cat.getId().toString()));
        mvc.perform(doIp(post("/estoque/categorias/" + cat.getId() + "/renomear")).param("nome", "Outro Nome " + p.getId()));

        assertEquals(List.of("evento=produto_editado id=" + p.getId() + " ip=" + IP + " antes=\"Auditado | 6.00 | \""
                + " depois=\"Auditado Novo | 7.50 | Auditoria " + p.getId() + "\""), eventos("produto_editado"));
        assertEquals(1, eventos("categoria_renomeada").size());
    }

    @Test
    @DisplayName("Venda desfeita (sem estoque) não aparece no log de auditoria")
    void rollbackNaoLoga() throws Exception {
        mvc.perform(doIp(post("/venda")).param("token", UUID.randomUUID().toString()).param("qtd_" + p.getId(), "99")
                .param("pag_forma_0", "PIX"));

        assertEquals(List.of(), eventos("venda"));
    }

    @Test
    @DisplayName("A senha nunca vai para o log, nem errada nem certa")
    void senhaNuncaNoLog(CapturedOutput log) throws Exception {
        mvc.perform(post("/login").param("senha", "senha-errada-secreta-123").with(r -> { r.setRemoteAddr("198.51.100.77"); return r; }));
        mvc.perform(post("/login").param("senha", br.com.lac.caixa.SenhaDeTeste.VALOR).with(r -> { r.setRemoteAddr("198.51.100.78"); return r; }));

        assertFalse(log.getAll().contains("senha-errada-secreta-123"));
        assertFalse(log.getAll().contains(br.com.lac.caixa.SenhaDeTeste.VALOR));
        assertTrue(linhas.list.stream().noneMatch(l -> l.getFormattedMessage().contains("senha")));
    }

    @Test
    @DisplayName("Valores com quebra de linha não forjam outra linha de auditoria")
    void semInjecaoDeLinha() {
        String linha = Auditoria.formatar(mapa("evento", "x", "id", 1, "motivo", "a\nevento=venda falsa"));

        assertEquals("evento=x id=1 motivo=\"a evento=venda falsa\"", linha);
        assertFalse(linha.contains("\n"));
    }

    static java.util.Map<String, Object> mapa(Object... kv) {
        var m = new java.util.LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
