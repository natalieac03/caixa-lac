package br.com.lac.caixa.sistema;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Abre um Chromium do Playwright para os testes de sistema. Tenta o navegador
 * baixado pelo Playwright e, se não houver, o Google Chrome instalado na máquina.
 * Sem navegador: no computador o teste é pulado; no CI (CI=true) é erro, como
 * os testes com Docker (ExigeDocker).
 */
public class Navegador implements BeforeAllCallback {

    private static Playwright playwright;
    private static Browser browser;

    static Browser browser() {
        return browser;
    }

    @Override
    public void beforeAll(ExtensionContext contexto) {
        if (browser != null) return;
        RuntimeException falha = null;
        try {
            // Sem download automático (seria ~1 GB): usa o navegador já instalado pelo
            // Playwright (no CI, pelo passo "playwright install") ou o Google Chrome.
            Map<String, String> ambiente = new HashMap<>(System.getenv());
            ambiente.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
            playwright = Playwright.create(new Playwright.CreateOptions().setEnv(ambiente));
            for (String canal : new String[] {null, "chrome"}) {
                try {
                    var opcoes = new BrowserType.LaunchOptions().setHeadless(true);
                    if (canal != null) opcoes.setChannel(canal);
                    browser = playwright.chromium().launch(opcoes);
                    return;
                } catch (RuntimeException e) {
                    falha = e;
                }
            }
        } catch (RuntimeException e) {
            falha = e;
        }
        fechar();
        conferir(false, "true".equalsIgnoreCase(System.getenv("CI")), falha);
    }

    /** Com navegador segue; sem: erro no CI, pulado fora dele. */
    static void conferir(boolean temNavegador, boolean ci, RuntimeException falha) {
        if (temNavegador) return;
        if (ci)
            throw new IllegalStateException(
                    "CI=true sem navegador: os testes de sistema não podem ser pulados.", falha);
        Assumptions.abort("Sem navegador para o Playwright: testes de sistema pulados (no CI isso é erro).");
    }

    private static void fechar() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
        browser = null;
        playwright = null;
    }

    // O navegador é reaproveitado entre as classes de teste e fecha no fim da JVM.
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(Navegador::fechar));
    }
}
