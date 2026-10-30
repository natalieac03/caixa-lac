package br.com.lac.caixa.sistema;

import br.com.lac.caixa.SenhaDeTeste;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Testes de sistema: a tela de venda de verdade, num Chromium, contra o app rodando. */
@ExtendWith(Navegador.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:sistema;DB_CLOSE_DELAY=-1"})
class VendaNoNavegadorTest {
    @LocalServerPort int porta;
    @Autowired ProdutoRepository produtos;

    BrowserContext contexto;
    Page pagina;
    Produto boton;

    @BeforeEach
    void abrir() {
        boton = produtos.save(new Produto("Bóton Sistema " + System.nanoTime(), new BigDecimal("6.00"), 50));
        contexto = Navegador.browser().newContext();
        pagina = contexto.newPage();
    }

    @AfterEach
    void fechar() {
        contexto.close();
    }

    String url(String caminho) {
        return "http://localhost:" + porta + caminho;
    }

    void entrarEAbrirVenda() {
        pagina.navigate(url("/login"));
        pagina.locator("#senha").fill(SenhaDeTeste.VALOR);
        pagina.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Entrar")).click();
        pagina.waitForURL(url("/"));
        pagina.navigate(url("/venda/nova"));
    }

    @Test
    @DisplayName("Venda dividida Pix + Dinheiro com troco, do início ao fim")
    void vendaDivididaComTroco() {
        entrarEAbrirVenda();

        // Arrange: 3 bótons de R$ 6,00 = R$ 18,00
        pagina.getByLabel("Quantidade de " + boton.getNome()).fill("3");
        assertThat(pagina.locator("#totalCarrinho")).hasText("18,00");
        assertThat(pagina.locator("#contagemItens")).hasText("3 itens");
        assertThat(pagina.locator("#botaoConfirmar")).hasText("Confirmar R$ 18,00");

        // Act: divide em Pix 10,00 + Dinheiro 8,00 recebendo 10,00
        pagina.getByText("Dividir pagamento").click();
        var linhas = pagina.locator(".linha-pagamento");
        assertThat(linhas).hasCount(2);
        linhas.nth(0).locator("select.forma").selectOption("PIX");
        linhas.nth(0).locator("input.valor").fill("10,00");
        assertThat(pagina.locator("#saldoPagamento")).hasText("Falta R$ 8,00");
        assertThat(pagina.locator("#botaoConfirmar")).isDisabled();
        linhas.nth(1).locator("select.forma").selectOption("DINHEIRO");
        linhas.nth(1).locator("input.valor").fill("8,00");
        linhas.nth(1).locator("input.recebido").fill("10,00");
        assertThat(linhas.nth(1).locator(".troco")).hasText("Troco: R$ 2,00");
        assertThat(pagina.locator("#saldoPagamento")).hasText("Pagamento fechado: R$ 18,00");
        assertThat(pagina.locator("#botaoConfirmar")).isEnabled();
        pagina.locator("#botaoConfirmar").click();

        // Assert: confirmação e histórico com as duas partes
        pagina.waitForURL(Pattern.compile(".*/historico$"));
        assertThat(pagina.locator(".aviso-sucesso"))
                .containsText("Recebido R$ 10,00 via Pix + R$ 8,00 em dinheiro (troco R$ 2,00).");
        assertThat(pagina.locator("td.pagamento").first())
                .hasAttribute("data-pagamento", "Pix R$ 10,00 + Dinheiro R$ 8,00 (troco R$ 2,00)");
    }

    @Test
    @DisplayName("Sessão expirada na tela de venda: a prévia mostra \"Sessão expirou. Entre de novo.\" com link para o login")
    void sessaoExpirada() {
        entrarEAbrirVenda();
        pagina.getByLabel("Quantidade de " + boton.getNome()).fill("1");
        assertThat(pagina.locator("#totalCarrinho")).hasText("6,00");

        // Act: a sessão some (como depois de 12 h ou de trocar a senha) e a pessoa mexe na quantidade
        contexto.clearCookies();
        pagina.getByLabel("Quantidade de " + boton.getNome()).fill("2");

        // Assert
        var aviso = pagina.locator("#sessaoExpirada");
        assertThat(aviso).containsText("Sessão expirou. Entre de novo.");
        assertThat(aviso.getByRole(com.microsoft.playwright.options.AriaRole.LINK)).hasAttribute("href", "/login");
        assertThat(pagina.locator("#botaoConfirmar")).isDisabled();
    }
}
