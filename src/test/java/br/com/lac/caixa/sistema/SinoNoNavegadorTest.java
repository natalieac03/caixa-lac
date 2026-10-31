package br.com.lac.caixa.sistema;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.lac.caixa.SenhaDeTeste;
import br.com.lac.caixa.servico.EstoqueService;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/** Sininho num Chromium: novos, vistos, teclado e celular. */
@ExtendWith(Navegador.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:sistema_sino;DB_CLOSE_DELAY=-1"
        })
class SinoNoNavegadorTest {
    @LocalServerPort
    int porta;

    @Autowired
    EstoqueService estoque;

    @Autowired
    JdbcTemplate jdbc;

    BrowserContext contexto;
    Page pagina;

    @BeforeEach
    void abrir() {
        for (String t : new String[] {
            "movimento_estoque",
            "compra_estoque",
            "pagamento_venda",
            "venda_desconto",
            "item_venda",
            "venda",
            "despesa",
            "evento",
            "produto"
        }) {
            jdbc.update("delete from " + t);
        }
        contexto = Navegador.browser().newContext(new Browser.NewContextOptions().setViewportSize(1280, 900));
        pagina = entrar(contexto);
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

    @AfterEach
    void fechar() {
        contexto.close();
    }

    String url(String caminho) {
        return "http://localhost:" + porta + caminho;
    }

    @Test
    @DisplayName("Selo conta os novos; abrir marca como vistos; aviso novo volta a contar; Esc fecha e devolve o foco")
    void seloEPainel() {
        pagina.navigate(url("/"));
        var botao = pagina.locator("#botao-sino");
        assertThat(pagina.locator("#selo-avisos")).isHidden();
        assertThat(pagina.locator("#texto-avisos-novos")).hasText("nenhum aviso novo");

        estoque.criarNovo("Caneca A", new BigDecimal("30"), 5, null);
        estoque.criarNovo("Caneca B", new BigDecimal("30"), 5, null);
        pagina.navigate(url("/estoque"));
        assertThat(pagina.locator("#selo-avisos")).hasText("2");
        assertThat(pagina.locator("#texto-avisos-novos")).hasText("2 avisos novos");
        assertThat(botao).hasAttribute("aria-expanded", "false");

        botao.click();
        assertThat(botao).hasAttribute("aria-expanded", "true");
        var painel = pagina.locator("#painel-avisos");
        assertThat(painel).isVisible();
        assertThat(painel.locator("#alerta-sem-custo h3")).containsText("Produtos sem custo");
        assertThat(painel.locator("#alerta-sem-custo a").first()).hasAttribute("href", "/estoque?semCusto=true");
        assertThat(pagina.locator("#selo-avisos")).isHidden();

        pagina.keyboard().press("Escape");
        assertThat(painel).isHidden();
        assertThat(botao).hasAttribute("aria-expanded", "false");
        assertEquals("botao-sino", pagina.evaluate("() => document.activeElement.id"));

        pagina.reload();
        assertThat(pagina.locator("#selo-avisos")).isHidden();
        assertThat(pagina.locator("#painel-avisos li")).hasCount(2);

        estoque.criarNovo("Caneca C", new BigDecimal("30"), 5, null);
        pagina.reload();
        assertThat(pagina.locator("#selo-avisos")).hasText("1");
        assertThat(pagina.locator("#texto-avisos-novos")).hasText("1 aviso novo");
    }

    @Test
    @DisplayName("A consulta a /avisos atualiza o painel sem recarregar a página")
    void consultaAtualiza() {
        pagina.navigate(url("/historico"));
        assertThat(pagina.locator("#selo-avisos")).isHidden();
        estoque.criarNovo("Ecobag Sem Custo", new BigDecimal("30"), 5, null);
        pagina.evaluate("() => document.dispatchEvent(new Event('visibilitychange'))");
        assertThat(pagina.locator("#selo-avisos")).hasText("1");
        assertThat(pagina.locator("#painel-avisos li")).containsText("Ecobag Sem Custo");
    }

    @Test
    @DisplayName("Celular: o painel ocupa a largura da tela, sem rolagem lateral")
    void noCelularOPainelOcupaALarguraSemRolagem() {
        estoque.criarNovo("Bóton Sem Custo", new BigDecimal("6"), 5, null);
        var celular = Navegador.browser().newContext(new Browser.NewContextOptions().setViewportSize(390, 844));
        try {
            Page p = entrar(celular);
            p.locator("#botao-sino").click();
            var caixa = p.locator("#painel-avisos").boundingBox();
            assertEquals(0, Math.round(caixa.x));
            assertEquals(390, Math.round(caixa.width));
            Object sobra =
                    p.evaluate("() => document.documentElement.scrollWidth - document.documentElement.clientWidth");
            assertEquals(0, ((Number) sobra).intValue());
        } finally {
            celular.close();
        }
    }

    @Test
    @DisplayName("Sem localStorage (bloqueado): nada quebra e todos os avisos continuam contando como novos")
    void semLocalStorage() {
        estoque.criarNovo("Adesivo Sem Custo", new BigDecimal("8"), 5, null);
        var bloqueado = Navegador.browser().newContext(new Browser.NewContextOptions().setViewportSize(1280, 900));
        bloqueado.addInitScript(
                "Object.defineProperty(window, 'localStorage', { get() { throw new Error('bloqueado'); } });");
        try {
            Page p = entrar(bloqueado);
            assertThat(p.locator("#selo-avisos")).hasText("1");
            p.locator("#botao-sino").click();
            assertThat(p.locator("#painel-avisos")).isVisible();
            p.keyboard().press("Escape");
            p.reload();
            assertThat(p.locator("#selo-avisos")).hasText("1");
        } finally {
            bloqueado.close();
        }
    }
}
