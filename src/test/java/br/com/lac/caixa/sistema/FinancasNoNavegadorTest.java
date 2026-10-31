package br.com.lac.caixa.sistema;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import br.com.lac.caixa.SenhaDeTeste;
import br.com.lac.caixa.financas.MassaDeTeste;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

/** A tela de Finanças num Chromium, com a massa fixa: cartões e demonstrativo com os números calculados à mão. */
@ExtendWith(Navegador.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=test",
            "spring.datasource.url=jdbc:h2:mem:sistema_financas;DB_CLOSE_DELAY=-1"
        })
class FinancasNoNavegadorTest {
    static final String MARCO = "/financas?atalho=personalizado&inicio=2025-03-01&fim=2025-03-15";

    @LocalServerPort
    int porta;

    @Autowired
    ApplicationContext contexto;

    BrowserContext navegador;
    Page pagina;
    MassaDeTeste massa;

    @BeforeEach
    void abrir() {
        massa = new MassaDeTeste(contexto).montar();
        navegador = Navegador.browser()
                .newContext(new com.microsoft.playwright.Browser.NewContextOptions().setViewportSize(1280, 900));
        pagina = navegador.newPage();
        pagina.navigate(url("/login"));
        pagina.locator("#senha").fill(SenhaDeTeste.VALOR);
        pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Entrar"))
                .click();
        pagina.waitForURL(url("/"));
    }

    @AfterEach
    void fechar() {
        navegador.close();
    }

    String url(String caminho) {
        return "http://localhost:" + porta + caminho;
    }

    @Test
    @DisplayName("01 a 15/03: cartões, demonstrativo e gráficos com os números da massa")
    void cartoesEDemonstrativo() {
        pagina.navigate(url(MARCO));

        // Cartões (comparados com 14/02 a 28/02, sem vendas)
        assertThat(pagina.locator("#cartao-receita .indicador-valor")).hasText("R$ 229,00");
        assertThat(pagina.locator("#cartao-lucro .indicador-valor")).hasText("R$ 26,42");
        assertThat(pagina.locator("#cartao-margem .indicador-valor")).hasText("63,54%");
        assertThat(pagina.locator("#cartao-ticket .indicador-valor")).hasText("R$ 28,62");
        assertThat(pagina.locator("#cartao-vendas .indicador-valor")).hasText("8");
        assertThat(pagina.locator("#cartao-itens .indicador-valor")).hasText("16");
        assertThat(pagina.locator("#cartao-receita .variacao")).containsText("antes era zero");
        assertThat(pagina.locator("#aviso-parcial")).containsText("R$ 46,00");

        // Demonstrativo: valor e % da receita líquida
        var linhas = pagina.locator("#demonstrativo tbody tr");
        List<String> esperado = List.of(
                "Receita bruta R$ 257,00 112,23%",
                "(-) Descontos promocionais R$ 8,00 3,49%",
                "(+/-) Ajustes manuais R$ -20,00 -8,73%",
                "= Receita líquida R$ 229,00 100,00%",
                "(-) Taxas estimadas R$ 3,58 1,56%",
                "(-) CMV (custo da mercadoria vendida) R$ 83,50 36,46%",
                "= Margem bruta R$ 145,50 63,54%",
                "(-) Despesas R$ 115,50 50,44%",
                "= Lucro líquido R$ 26,42 11,54%");
        assertThat(linhas).hasCount(esperado.size());
        List<String> lidas = linhas.allInnerTexts().stream()
                .map(t -> t.replaceAll("\\s+", " ").trim())
                .toList();
        org.junit.jupiter.api.Assertions.assertEquals(esperado, lidas);

        // Gráficos desenhados a partir do JSON
        assertThat(pagina.locator("#financas")).hasAttribute("data-graficos", "prontos");
        pagina.screenshot(new Page.ScreenshotOptions()
                .setPath(Path.of("target", "financas-marco.png"))
                .setFullPage(true));
    }

    @Test
    @DisplayName("Feira 2 contra a Feira 1: variação escrita; drill-down de categoria e voltar")
    void variacaoEDrillDown() {
        pagina.navigate(url("/financas?evento=" + massa.e.get("Feira 2").getId()));
        assertThat(pagina.locator("#cartao-receita .indicador-valor")).hasText("R$ 29,00");
        assertThat(pagina.locator("#cartao-receita .variacao")).hasText("▼ -78,52% em relação a evento Feira 1");

        pagina.locator("#tabela-categorias button[data-categoria]").first().click();
        var drill = pagina.locator(".drill:not(.oculto)");
        assertThat(drill).isVisible();
        assertThat(pagina.locator("#categorias-visao")).isHidden();
        drill.getByText("← Voltar para as categorias").click();
        assertThat(pagina.locator("#categorias-visao")).isVisible();
    }

    @Test
    @DisplayName("Celular: a página não rola na horizontal (as tabelas rolam dentro delas)")
    void celular() {
        var celular = Navegador.browser()
                .newContext(new com.microsoft.playwright.Browser.NewContextOptions().setViewportSize(390, 844));
        try {
            var p = celular.newPage();
            p.navigate(url("/login"));
            p.locator("#senha").fill(SenhaDeTeste.VALOR);
            p.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Entrar"))
                    .click();
            p.waitForURL(url("/"));
            p.navigate(url(MARCO));
            assertThat(p.locator("#cartao-receita .indicador-valor")).hasText("R$ 229,00");
            Object largura =
                    p.evaluate("() => document.documentElement.scrollWidth - document.documentElement.clientWidth");
            org.junit.jupiter.api.Assertions.assertEquals(
                    0, ((Number) largura).intValue(), "rolagem horizontal da página");
            p.screenshot(new Page.ScreenshotOptions()
                    .setPath(Path.of("target", "financas-celular.png"))
                    .setFullPage(true));
        } finally {
            celular.close();
        }
    }

    @Test
    @DisplayName("Menu Finanças agrupa painel, eventos, despesas e taxas; abre, navega e fecha com Esc")
    void menuFinancas() {
        pagina.navigate(url("/"));
        var menu = pagina.locator("details.menu-grupo");
        var itens = menu.locator(".menu-grupo-itens");
        assertThat(itens).isHidden();
        // Só um item no topo para as quatro telas
        assertThat(
                        pagina.locator(
                                ".nav-links > a[href='/eventos'], .nav-links > a[href='/despesas'], .nav-links > a[href='/taxas']"))
                .hasCount(0);

        menu.locator("summary").click();
        assertThat(itens).isVisible();
        assertThat(itens.locator("a"))
                .hasText(new String[] {"Painel de finanças", "Eventos", "Comparativo de eventos", "Despesas", "Taxas"});
        pagina.screenshot(new Page.ScreenshotOptions().setPath(Path.of("target", "menu-financas.png")));
        pagina.keyboard().press("Escape");
        assertThat(itens).isHidden();

        menu.locator("summary").click();
        itens.getByText("Despesas").click();
        pagina.waitForURL(url("/despesas"));
        // Numa tela do grupo, o item Finanças fica marcado e a tela atual também
        assertThat(pagina.locator("details.menu-grupo summary")).hasClass(java.util.regex.Pattern.compile("ativo"));
        pagina.locator("details.menu-grupo summary").click();
        assertThat(pagina.locator(".menu-grupo-itens a[aria-current='page']")).hasText("Despesas");
        // Clicar fora fecha
        pagina.locator("h1").click();
        assertThat(pagina.locator(".menu-grupo-itens")).isHidden();
    }

    @Test
    @DisplayName("Menu Finanças no celular: abre dentro da barra, sem rolagem horizontal")
    void menuNoCelular() {
        var celular = Navegador.browser()
                .newContext(new com.microsoft.playwright.Browser.NewContextOptions().setViewportSize(390, 844));
        try {
            var p = celular.newPage();
            p.navigate(url("/login"));
            p.locator("#senha").fill(SenhaDeTeste.VALOR);
            p.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Entrar"))
                    .click();
            p.waitForURL(url("/"));
            p.locator("details.menu-grupo summary").click();
            assertThat(p.locator(".menu-grupo-itens")).isVisible();
            Object largura =
                    p.evaluate("() => document.documentElement.scrollWidth - document.documentElement.clientWidth");
            org.junit.jupiter.api.Assertions.assertEquals(0, ((Number) largura).intValue(), "rolagem horizontal");
            p.screenshot(new Page.ScreenshotOptions().setPath(Path.of("target", "menu-financas-celular.png")));
        } finally {
            celular.close();
        }
    }
}
