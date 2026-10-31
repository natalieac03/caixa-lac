package br.com.lac.caixa.sistema;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.lac.caixa.SenhaDeTeste;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.pix.FakeMercadoPago;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
        jdbc.update("update cobranca_pix set status = 'CANCELADA' where status = 'PENDENTE'");
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
        pagina.getByLabel("Quantidade de " + boton.getNome()).fill("3");
        assertThat(pagina.locator("#totalCarrinho")).hasText("18,00");
        var painel = pagina.locator("#pixUnico");
        assertThat(painel).isVisible();
        assertThat(pagina.locator("#botaoConfirmar")).isDisabled();

        painel.getByText("Gerar Pix").click();

        assertThat(painel.locator(".qr-valor")).hasText("R$ 18,00");
        assertThat(painel.locator("img.qr-pix")).hasAttribute("src", Pattern.compile("^data:image/png;base64,iVBOR"));
        assertThat(painel.locator(".situacao-pix")).hasText("Aguardando pagamento...");
        assertThat(pagina.locator("#botaoConfirmar")).isDisabled();

        String order = jdbc.queryForObject("select order_id from cobranca_pix where status = 'PENDENTE'", String.class);
        FAKE.pagar(order, "18.00");

        assertThat(painel.locator(".situacao-pix")).hasText("Pix recebido (Mercado Pago).");
        assertThat(pagina.locator("#botaoConfirmar")).isEnabled();
        pagina.locator("#botaoConfirmar").click();
        pagina.waitForURL(Pattern.compile(".*/historico$"));
        assertThat(pagina.locator(".aviso-sucesso")).containsText("Recebido R$ 18,00 via Pix.");
        assertEquals(
                1,
                jdbc.queryForObject(
                        "select count(*) from pagamento_venda where origem = 'MERCADO_PAGO' and mp_order_id = ?",
                        Integer.class,
                        order));
    }

    @Test
    @DisplayName("Mudar o total depois de gerar o Pix cancela a cobrança; Pix manual libera a venda")
    void totalMudouEPixManual() {
        pagina.getByLabel("Quantidade de " + boton.getNome()).fill("2");
        assertThat(pagina.locator("#totalCarrinho")).hasText("12,00");
        var painel = pagina.locator("#pixUnico");
        painel.getByText("Gerar Pix").click();
        assertThat(painel.locator(".qr-valor")).hasText("R$ 12,00");
        String order = jdbc.queryForObject("select order_id from cobranca_pix where status = 'PENDENTE'", String.class);

        pagina.getByLabel("Quantidade de " + boton.getNome()).fill("3");

        assertThat(painel.locator(".situacao-pix")).containsText("O valor mudou");
        assertThat(painel.getByText("Gerar Pix")).isVisible();
        pagina.waitForCondition(() -> "canceled".equals(FAKE.order(order).status));

        painel.getByText("Pix manual", new Locator.GetByTextOptions().setExact(true))
                .click();
        assertThat(pagina.locator("#botaoConfirmar")).isEnabled();
        pagina.locator("#botaoConfirmar").click();
        pagina.waitForURL(Pattern.compile(".*/historico$"));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "select count(*) from pagamento_venda where origem = 'MANUAL' and valor = 18", Integer.class));
    }

    @Test
    @DisplayName("Usar Pix manual com o Pix já pago: a tela fica com o pago (Mercado Pago), não troca para manual")
    void usarManualComPixJaPago() {
        pagina.getByLabel("Quantidade de " + boton.getNome()).fill("1");
        var painel = pagina.locator("#pixUnico");
        painel.getByText("Gerar Pix").click();
        assertThat(painel.locator(".situacao-pix")).hasText("Aguardando pagamento...");
        String order = jdbc.queryForObject(
                "select order_id from cobranca_pix where status = 'PENDENTE' and valor = 6", String.class);
        FAKE.pagar(order, "6.00");

        painel.getByText("Usar Pix manual").click();

        assertThat(painel.locator(".situacao-pix")).hasText("Pix recebido (Mercado Pago).");
        assertThat(painel.locator("input.origem")).hasValue("MERCADO_PAGO");
        pagina.locator("#botaoConfirmar").click();
        pagina.waitForURL(Pattern.compile(".*/historico$"));
        assertEquals(
                order,
                jdbc.queryForObject(
                        "select mp_order_id from pagamento_venda where origem = 'MERCADO_PAGO' and valor = 6",
                        String.class));
    }

    @Test
    @DisplayName("A tela não dispara uma consulta nova com a anterior ainda em andamento")
    void semConsultasSobrepostas() {
        pagina.getByLabel("Quantidade de " + boton.getNome()).fill("2");
        var painel = pagina.locator("#pixUnico");
        List<Route> presas = new ArrayList<>();
        pagina.route(Pattern.compile(".*/venda/pix/[0-9a-f-]{36}/0$"), presas::add);
        painel.getByText("Gerar Pix").click();
        assertThat(painel.locator(".situacao-pix")).hasText("Aguardando pagamento...");
        pagina.waitForTimeout(7000);
        int disparadas = presas.size();
        presas.forEach(Route::resume);
        pagina.unroute(Pattern.compile(".*/venda/pix/[0-9a-f-]{36}/0$"));
        assertEquals(1, disparadas, "uma consulta por vez");
    }
}
