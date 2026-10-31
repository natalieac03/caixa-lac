package br.com.lac.caixa.financas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.lac.caixa.RelogioDeTeste;
import br.com.lac.caixa.pix.FakeMercadoPago;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Rede de segurança para upgrades: JSON da prévia, de /financas/dados e do Mercado Pago e CSV do histórico,
 * com a massa fixa e relógio fixo, iguais byte a byte aos de src/test/resources/esperado/.
 */
@SpringBootTest(
        properties = {
            "spring.profiles.active=test",
            "spring.datasource.url=jdbc:h2:mem:saidas_esperadas;DB_CLOSE_DELAY=-1",
            "mercadopago.webhook-sincrono=true"
        })
@AutoConfigureMockMvc
class SaidasEsperadasTest {
    static final Path ESPERADO = Path.of("src/test/resources/esperado");
    static final FakeMercadoPago FAKE = new FakeMercadoPago();
    static final MockWebServer SERVIDOR = FakeMercadoPago.iniciar(FAKE);
    static final String TOKEN = "11111111-2222-3333-4444-555555555555";

    @TestConfiguration
    static class Relogio {
        @Bean
        @Primary
        Clock relogioDeTeste() {
            var relogio = new RelogioDeTeste();
            relogio.definir(LocalDateTime.of(2026, 1, 15, 10, 0));
            return relogio;
        }
    }

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

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationContext contexto;

    String resposta(MockHttpServletRequestBuilder pedido) throws Exception {
        MockHttpServletResponse r =
                mvc.perform(pedido.sessionAttr("autenticado", true)).andReturn().getResponse();
        return "HTTP " + r.getStatus() + "\nContent-Type: " + r.getContentType() + "\n\n"
                + r.getContentAsString(StandardCharsets.UTF_8);
    }

    static void conferir(String arquivo, String atual) throws Exception {
        Path caminho = ESPERADO.resolve(arquivo);
        if (Boolean.getBoolean("esperado.gerar")) {
            Files.createDirectories(ESPERADO);
            Files.writeString(caminho, atual);
        }
        assertEquals(Files.readString(caminho), atual, arquivo);
    }

    @Test
    @DisplayName("Prévia, finanças, histórico em CSV e Pix do Mercado Pago saem iguais aos arquivos esperados")
    void saidasIguaisAsEsperadas() throws Exception {
        var massa = new MassaDeTeste(contexto).montar();
        String boton = "qtd_" + massa.p.get("Bóton 1").getId();
        String caneca = "qtd_" + massa.p.get("Caneca 2").getId();
        String poster = "qtd_" + massa.p.get("Pôster 1").getId();

        conferir(
                "previa.json",
                resposta(post("/venda/previa")
                        .param(boton, "4")
                        .param(caneca, "1")
                        .param(poster, "1")));
        conferir(
                "previa-erro.json",
                resposta(
                        post("/venda/previa").accept(MediaType.APPLICATION_JSON).param(boton, "-1")));
        conferir(
                "financas-dados.json",
                resposta(get("/financas/dados?atalho=personalizado&inicio=2025-03-01&fim=2025-03-15&metrica=lucro")));
        conferir(
                "financas-dados-feira.json",
                resposta(get("/financas/dados?evento=" + massa.e.get("Feira 1").getId())));
        conferir("historico.csv", resposta(get("/historico/exportar.csv?inicio=2025-01-01&fim=2025-12-31")));
        conferir(
                "pix.json",
                resposta(post("/venda/pix")
                        .accept(MediaType.APPLICATION_JSON)
                        .param("token", TOKEN)
                        .param("parte", "0")
                        .param("valor", "18,00")));
        conferir("mercadopago-order.json", FAKE.ultimoCorpoCru + "\n");
    }
}
