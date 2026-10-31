package br.com.lac.caixa.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.lac.caixa.RelogioDeTeste;
import br.com.lac.caixa.config.LimiteLogin;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Properties;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.catalina.valves.ValveBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Atrás do proxy, o limite de login conta o IP real do X-Forwarded-For. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=test",
            "spring.datasource.url=jdbc:h2:mem:proxy;DB_CLOSE_DELAY=-1",
            "server.forward-headers-strategy=native"
        })
class IpPorTrasDoProxyTest {
    @TestConfiguration
    static class Relogio {
        @Bean
        RelogioDeTeste relogioDeTeste() {
            return new RelogioDeTeste();
        }

        @Bean
        @Primary
        LimiteLogin limiteComRelogio(RelogioDeTeste relogio) {
            return new LimiteLogin(relogio);
        }
    }

    @Autowired
    ServletWebServerApplicationContext contexto;

    @LocalServerPort
    int porta;

    @Test
    void perfilProdUsaEstrategiaNative() throws Exception {
        var prod = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application-prod.properties")) {
            prod.load(in);
        }
        assertEquals("native", prod.getProperty("server.forward-headers-strategy"));
    }

    String ipVisto(String remoto, String xForwardedFor) throws Exception {
        var tomcat = ((TomcatWebServer) contexto.getWebServer()).getTomcat();
        Valve[] valves = tomcat.getEngine().getPipeline().getValves();
        var valve = (RemoteIpValve) Arrays.stream(valves)
                .filter(RemoteIpValve.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new AssertionError("RemoteIpValve não configurado"));
        var visto = new String[1];
        var copia = new RemoteIpValve();
        copia.setInternalProxies(valve.getInternalProxies());
        copia.setTrustedProxies(valve.getTrustedProxies());
        copia.setRemoteIpHeader(valve.getRemoteIpHeader());
        copia.setProtocolHeader(valve.getProtocolHeader());
        copia.setNext(new ValveBase() {
            @Override
            public void invoke(Request request, Response response) {
                visto[0] = request.getRemoteAddr();
            }
        });
        var request = new Request(new Connector());
        request.setCoyoteRequest(new org.apache.coyote.Request());
        request.setRemoteAddr(remoto);
        request.setRemoteHost(remoto);
        if (xForwardedFor != null)
            request.getCoyoteRequest()
                    .getMimeHeaders()
                    .addValue("X-Forwarded-For")
                    .setString(xForwardedFor);
        var response = new Response();
        response.setCoyoteResponse(new org.apache.coyote.Response());
        copia.invoke(request, response);
        return visto[0];
    }

    @Test
    void deRedePrivadaValeOXForwardedFor() throws Exception {
        assertEquals("203.0.113.7", ipVisto("10.0.4.2", "203.0.113.7"));
        assertEquals("203.0.113.7", ipVisto("100.64.0.9", "203.0.113.7"));
        assertEquals("203.0.113.7", ipVisto("172.17.0.1", "198.51.100.1, 203.0.113.7"));
    }

    @Test
    void deForaDaRedePrivadaValeORemoto() throws Exception {
        assertEquals("198.51.100.20", ipVisto("198.51.100.20", "203.0.113.7"));
        assertEquals("10.0.4.2", ipVisto("10.0.4.2", null));
    }

    @Test
    void limiteDeLoginContaOIpDoCabecalho() throws Exception {
        var http = HttpClient.newHttpClient();
        var errar = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Forwarded-For", "203.0.113.50")
                .POST(HttpRequest.BodyPublishers.ofString("senha=errada"))
                .build();
        assertTrue(http.send(errar, HttpResponse.BodyHandlers.discarding())
                .headers()
                .firstValue("Location")
                .orElseThrow()
                .endsWith("/login?erro"));
        assertTrue(http.send(errar, HttpResponse.BodyHandlers.discarding())
                .headers()
                .firstValue("Location")
                .orElseThrow()
                .endsWith("/login?espera"));
        var outro = HttpRequest.newBuilder(errar.uri())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Forwarded-For", "203.0.113.51")
                .POST(HttpRequest.BodyPublishers.ofString("senha=errada"))
                .build();
        assertTrue(http.send(outro, HttpResponse.BodyHandlers.discarding())
                .headers()
                .firstValue("Location")
                .orElseThrow()
                .endsWith("/login?erro"));
    }
}
