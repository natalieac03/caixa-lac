package br.com.lac.caixa.pix;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.lac.caixa.servico.CobrancaPixService;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Properties;
import java.util.UUID;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** MP_PAYER_NOME vai como payer.first_name na order Pix. */
@SpringBootTest(
        properties = {
            "spring.profiles.active=test",
            "spring.datasource.url=jdbc:h2:mem:pagador_nome;DB_CLOSE_DELAY=-1",
            "MP_PAYER_NOME=APRO",
            "MP_PAYER_FIRST_NAME=NOME-ANTIGO"
        })
class PagadorNomeTest {
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

    @Autowired
    MercadoPagoConfig config;

    @Autowired
    CobrancaPixService cobrancas;

    static String expressao() throws Exception {
        var p = new Properties();
        try (InputStream in = PagadorNomeTest.class.getResourceAsStream("/application.properties")) {
            p.load(in);
        }
        return p.getProperty("mercadopago.payer-first-name");
    }

    @ParameterizedTest(name = "MP_PAYER_NOME=''{0}'' MP_PAYER_FIRST_NAME=''{1}'' -> ''{2}''")
    @DisplayName("MP_PAYER_NOME tem preferência; o nome antigo continua aceito; sem nenhum, vazio")
    @CsvSource({"APRO,,APRO", ",APRO,APRO", "APRO,OUTRO,APRO", ",,''"})
    void payerNomeTemPreferenciaSobreONomeAntigo(String nome, String antigo, String esperado) throws Exception {
        var env = new StandardEnvironment();
        var variaveis = new HashMap<String, Object>();
        if (nome != null) variaveis.put("MP_PAYER_NOME", nome);
        if (antigo != null) variaveis.put("MP_PAYER_FIRST_NAME", antigo);
        env.getPropertySources().addFirst(new MapPropertySource("ambiente", variaveis));
        assertEquals(esperado, env.resolvePlaceholders(expressao()));
    }

    @Test
    @DisplayName("Com MP_PAYER_NOME=APRO, a criação da order Pix leva payer.first_name = APRO")
    void vaiNaOrder() throws Exception {
        assertEquals("APRO", config.getPayerFirstName());
        cobrancas.gerar(UUID.randomUUID().toString(), 0, new BigDecimal("12"));
        var corpo = FAKE.ultimaCriacao;
        assertEquals("APRO", corpo.path("payer").path("first_name").asString());
        assertEquals("caixa@lac.test", corpo.path("payer").path("email").asString());
    }
}
