package br.com.lac.caixa.sistema;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.lac.caixa.SenhaDeTeste;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.pix.FakeMercadoPago;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.math.BigDecimal;
import java.util.regex.Pattern;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Pix automático na tela de venda, num Chromium, com o Mercado Pago falso. */
@ExtendWith(Navegador.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:sistema_pix;DB_CLOSE_DELAY=-1"})
class PixNoNavegadorTest {
    static final FakeMercadoPago FAKE = new FakeMercadoPago();
    static final MockWebServer SERVIDOR = FakeMercadoPago.iniciar(FAKE);

    @DynamicPropertySource
    static void mercadoPago(DynamicPropertyRegistry registro) {
        registro.add("mercadopago.url", () -> SERVIDOR.url("/").toString());
        registro.add("mercadopago.access-token", () -> FakeMercadoPago.TOKEN);
        registro.add("mercadopago.payer-email", () -> "caixa@lac.test");
    }

    @AfterAll
    static void parar() throws Exception {
        SERVIDOR.shutdown();
    }

    @LocalServerPort
    int porta;

    @Autowired
    ProdutoRepository produtos;

    @Autowired
    JdbcTemplate jdbc;

    BrowserContext contexto;
    Page pagina;
    Produto boton;

    @BeforeEach
    void abrir() {
        boton = produtos.save(new Produto("Bóton Pix " + System.nanoTime(), new BigDecimal("6.00"), 50));
        contexto = Navegador.browser().newContext();
        pagina = contexto.newPage();
        pagina.navigate(url("/login"));
        pagina.locator("#senha").fill(SenhaDeTeste.VALOR);
        pagina.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Entrar"))
                .click();
        pagina.waitForURL(url("/"));
        pagina.navigate(url("/venda/nova"));
    }

    @AfterEach
    void fechar() {
        contexto.close();
    }

    String url(String caminho) {
        return "http://localhost:" + porta + caminho;
    }

    @Test
    @DisplayName("Gerar Pix: QR na tela, pagamento confirmado sozinho e venda registrada como Mercado Pago")
    void pixConfirmadoSozinho() {
        // Arrange: 3 bótons = R$ 18,00, forma Pix (a primeira)
        pagina.getByLabel("Quantidade de " + boton.getNome()).fill("3");
        assertThat(pagina.locator("#totalCarrinho")).hasText("18,00");
        var painel = pagina.locator("#pixUnico");
        assertThat(painel).isVisible();
        assertThat(pagina.locator("#botaoConfirmar")).isDisabled();

        // Act: gera o Pix
        painel.getByText("Gerar Pix").click();

        // Assert: QR, valor e espera
        assertThat(painel.locator(".qr-valor")).hasText("R$ 18,00");
        assertThat(painel.locator("img.qr-pix")).hasAttribute("src", Pattern.compile("^data:image/png;base64,iVBOR"));
        assertThat(painel.locator(".situacao-pix")).hasText("Aguardando pagamento...");
        assertThat(pagina.locator("#botaoConfirmar")).isDisabled();

        // Act: o cliente paga no banco (Mercado Pago falso)
        String order = jdbc.queryForObject("select order_id from cobranca_pix where status = 'PENDENTE'", String.class);
        FAKE.pagar(order, "18.00");

        // Assert: a tela percebe sozinha (consulta a cada 3 s) e libera o confirmar
        assertThat(painel.locator(".situacao-pix")).hasText("Pix recebido (Mercado Pago).");
        assertThat(pagina.locator("#botaoConfirmar")).isEnabled();
        pagina.locator("#botaoConfirmar").click();
        pagina.waitForURL(Pattern.compile(".*/historico$"));
        assertThat(pagina.locator(".aviso-sucesso")).containsText("Recebido R$ 18,00 via Pix.");
        assertEquals(
                order,
                jdbc.queryForObject(
                        "select mp_order_id from pagamento_venda where origem = 'MERCADO_PAGO'", String.class));
    }

    @Test
    @DisplayName("Mudar o total depois de gerar o Pix cancela a cobrança; Pix manual libera a venda")
    void totalMudouEPixManual() {
        // Arrange
        pagina.getByLabel("Quantidade de " + boton.getNome()).fill("2");
        assertThat(pagina.locator("#totalCarrinho")).hasText("12,00");
        var painel = pagina.locator("#pixUnico");
        painel.getByText("Gerar Pix").click();
        assertThat(painel.locator(".qr-valor")).hasText("R$ 12,00");
        String order = jdbc.queryForObject("select order_id from cobranca_pix where status = 'PENDENTE'", String.class);

        // Act: muda a quantidade (o total vira R$ 18,00)
        pagina.getByLabel("Quantidade de " + boton.getNome()).fill("3");

        // Assert: a cobrança antiga foi cancelada e é preciso gerar outra
        assertThat(painel.locator(".situacao-pix")).containsText("O valor mudou");
        assertThat(painel.getByText("Gerar Pix")).isVisible();
        pagina.waitForCondition(() -> "canceled".equals(FAKE.order(order).status));

        // Act / Assert: Pix manual libera o confirmar e grava como manual
        painel.getByText("Pix manual").click();
        assertThat(pagina.locator("#botaoConfirmar")).isEnabled();
        pagina.locator("#botaoConfirmar").click();
        pagina.waitForURL(Pattern.compile(".*/historico$"));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "select count(*) from pagamento_venda where origem = 'MANUAL' and valor = 18", Integer.class));
    }
}
