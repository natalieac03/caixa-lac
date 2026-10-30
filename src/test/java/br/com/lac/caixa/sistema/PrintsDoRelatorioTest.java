package br.com.lac.caixa.sistema;

import br.com.lac.caixa.SenhaDeTeste;
import br.com.lac.caixa.financas.MassaDeTeste;
import br.com.lac.caixa.servico.EventoService;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.Media;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

/**
 * Gera os prints do relatório impresso em docs/prints/ (PDF A4 e imagens em modo
 * de impressão), com a massa fixa. Não roda na suíte normal; para gerar de novo:
 * ./mvnw -B test -Dtest=PrintsDoRelatorioTest -Dprints=true -Djacoco.skip=true
 */
@ExtendWith(Navegador.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:prints;DB_CLOSE_DELAY=-1"})
class PrintsDoRelatorioTest {
    @LocalServerPort
    int porta;

    @Autowired
    ApplicationContext contexto;

    @Test
    @DisplayName("Prints do relatório do evento e do comparativo (só com -Dprints=true)")
    void gerar() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("prints"), "use -Dprints=true para gerar os prints");
        var massa = new MassaDeTeste(contexto).montar();
        // Metas na Feira 1, para o print mostrar as barras de progresso.
        var feira1 = massa.e.get("Feira 1");
        contexto.getBean(EventoService.class)
                .editar(
                        feira1.getId(),
                        feira1.getNome(),
                        feira1.getInicio(),
                        feira1.getFim(),
                        "Print gerado com a massa de teste",
                        new java.math.BigDecimal("150"),
                        new java.math.BigDecimal("20"));
        Path pasta = Path.of("docs", "prints");
        Files.createDirectories(pasta);
        var navegador = Navegador.browser().newContext(new Browser.NewContextOptions().setViewportSize(900, 1200));
        try {
            Page p = navegador.newPage();
            p.navigate("http://localhost:" + porta + "/login");
            p.locator("#senha").fill(SenhaDeTeste.VALOR);
            p.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Entrar"))
                    .click();
            p.waitForURL("http://localhost:" + porta + "/");
            p.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.PRINT));

            p.navigate("http://localhost:" + porta + "/eventos/"
                    + massa.e.get("Feira 1").getId() + "/relatorio");
            p.pdf(new Page.PdfOptions()
                    .setPath(pasta.resolve("relatorio-feira-1.pdf"))
                    .setFormat("A4")
                    .setPrintBackground(true));
            p.screenshot(new Page.ScreenshotOptions()
                    .setPath(pasta.resolve("relatorio-feira-1.png"))
                    .setFullPage(true));

            p.navigate("http://localhost:" + porta + "/eventos/comparativo");
            p.pdf(new Page.PdfOptions()
                    .setPath(pasta.resolve("comparativo-eventos.pdf"))
                    .setFormat("A4")
                    .setLandscape(true)
                    .setPrintBackground(true));
            p.screenshot(new Page.ScreenshotOptions()
                    .setPath(pasta.resolve("comparativo-eventos.png"))
                    .setFullPage(true));
        } finally {
            navegador.close();
        }
    }
}
