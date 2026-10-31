package br.com.lac.caixa.sistema;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.lac.caixa.SenhaDeTeste;
import br.com.lac.caixa.servico.EventoService;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/** "Iniciar evento" e "Encerrar" pelo topo, num Chromium. */
@ExtendWith(Navegador.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=test",
            "spring.datasource.url=jdbc:h2:mem:sistema_evento;DB_CLOSE_DELAY=-1"
        })
class EventoRapidoNoNavegadorTest {
    @LocalServerPort
    int porta;

    @Autowired
    EventoService eventos;

    @Autowired
    JdbcTemplate jdbc;

    BrowserContext contexto;

    @BeforeEach
    void limpar() {
        jdbc.update("delete from despesa");
        jdbc.update("delete from evento");
        contexto = Navegador.browser().newContext(new Browser.NewContextOptions().setViewportSize(1280, 900));
    }

    @AfterEach
    void fechar() {
        contexto.close();
    }

    String url(String caminho) {
        return "http://localhost:" + porta + caminho;
    }

    Page entrar(BrowserContext c) {
        Page p = c.newPage();
        p.navigate(url("/login"));
        p.locator("#senha").fill(SenhaDeTeste.VALOR);
        p.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Entrar"))
                .click();
        p.waitForURL(url("/"));
        return p;
    }

    @Test
    @DisplayName(
            "Iniciar pelo topo, selo com nome e hora; Encerrar pede confirmação (cancelar mantém, aceitar encerra)")
    void iniciarEEncerrarPeloTopo() {
        Page p = entrar(contexto);
        p.navigate(url("/estoque"));
        assertThat(p.locator("#selo-evento")).hasCount(0);
        p.locator("#botao-iniciar-evento").click();
        var nome = p.locator("#nomeEventoTopo");
        assertThat(nome).hasValue(Pattern.compile("^Evento \\d\\d/\\d\\d$"));
        nome.fill("Feira de Outubro II");
        p.getByText("Começar agora").click();
        p.waitForURL(url("/estoque"));

        assertThat(p.locator("#selo-evento")).containsText(Pattern.compile("Feira de Outubro II, desde \\d\\d:\\d\\d"));
        assertTrue(eventos.aberto().isPresent());

        p.onceDialog(d -> {
            assertTrue(d.message().contains("Feira de Outubro II"), d.message());
            d.dismiss();
        });
        p.locator("#botao-encerrar").click();
        assertThat(p.locator("#selo-evento")).isVisible();
        assertTrue(eventos.aberto().isPresent());

        p.onceDialog(d -> d.accept());
        p.locator("#botao-encerrar").click();
        assertThat(p.locator(".aviso-sucesso")).containsText("encerrado");
        assertThat(p.locator("#selo-evento")).hasCount(0);
        assertThat(p.locator("#botao-iniciar-evento")).isVisible();
        assertTrue(eventos.aberto().isEmpty());
    }

    @Test
    @DisplayName("Tela inicial: iniciar pelo cartão; com evento aberto, o cartão mostra o evento e o Encerrar")
    void iniciarPelaTelaInicial() {
        Page p = entrar(contexto);
        p.locator("#nomeEventoInicio").fill("Encontro LAC");
        p.getByText("Iniciar evento agora").click();
        p.waitForURL(url("/"));
        assertThat(p.locator("#titulo-evento-inicio")).containsText("Encontro LAC em andamento");
        assertThat(p.locator("#selo-evento")).containsText("Encontro LAC, desde");
    }

    @Test
    @DisplayName("Celular: selo de evento e sino no topo sem rolagem lateral")
    void noCelularSeloESinoCabemSemRolagemLateral() {
        eventos.iniciar("Feira de Outubro II");
        var celular = Navegador.browser().newContext(new Browser.NewContextOptions().setViewportSize(390, 844));
        try {
            Page p = entrar(celular);
            assertThat(p.locator("#selo-evento")).isVisible();
            assertThat(p.locator("#botao-encerrar")).isVisible();
            Object sobra =
                    p.evaluate("() => document.documentElement.scrollWidth - document.documentElement.clientWidth");
            assertEquals(0, ((Number) sobra).intValue());
        } finally {
            celular.close();
        }
    }
}
