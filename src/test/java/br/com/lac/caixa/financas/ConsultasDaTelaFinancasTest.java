package br.com.lac.caixa.financas;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Quantas consultas ao banco cada pedido da tela de Finanças faz, com a massa fixa: cada uma vai uma vez só. */
@SpringBootTest(
        properties = {
            "spring.profiles.active=test",
            "spring.datasource.url=jdbc:h2:mem:consultas_financas;DB_CLOSE_DELAY=-1"
        })
@AutoConfigureMockMvc
class ConsultasDaTelaFinancasTest {
    static final AtomicInteger CONSULTAS = new AtomicInteger();
    static final String MARCO = "atalho=personalizado&inicio=2025-03-01&fim=2025-03-15";

    @TestConfiguration
    static class Contador {
        @Bean
        static BeanPostProcessor contarConsultas() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String nome) {
                    if (!(bean instanceof DataSource ds)) return bean;
                    return Proxy.newProxyInstance(
                            getClass().getClassLoader(), new Class<?>[] {DataSource.class}, (p, m, args) -> {
                                Object r = chamar(ds, m, args);
                                return r instanceof Connection c ? contando(c) : r;
                            });
                }
            };
        }

        static Connection contando(Connection c) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (p, m, a) -> {
                        if (m.getName().startsWith("prepare") || m.getName().equals("createStatement")) {
                            CONSULTAS.incrementAndGet();
                        }
                        return chamar(c, m, a);
                    });
        }

        static Object chamar(Object alvo, java.lang.reflect.Method m, Object[] args) throws Throwable {
            try {
                return m.invoke(alvo, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationContext contexto;

    @BeforeEach
    void massa() {
        new MassaDeTeste(contexto).montar();
    }

    int consultas(String url, MediaType tipo) throws Exception {
        CONSULTAS.set(0);
        mvc.perform(get(url).sessionAttr("autenticado", true).accept(tipo)).andExpect(status().isOk());
        return CONSULTAS.get();
    }

    @Test
    @DisplayName("A tela, o JSON dos gráficos e o CSV de uma tabela não repetem consulta")
    void cadaConsultaUmaVez() throws Exception {
        int tela = consultas("/financas?" + MARCO, MediaType.TEXT_HTML);
        int dados = consultas("/financas/dados?" + MARCO, MediaType.APPLICATION_JSON);
        int csv = consultas("/financas/exportar/produtos.csv?" + MARCO, MediaType.ALL);
        assertTrue(tela <= 32, "a tela fez " + tela + " consultas (antes: 34; +1 do aviso de Pix estornado no sino)");
        assertTrue(dados <= 4, "o JSON dos gráficos fez " + dados + " consultas (antes: 5)");
        assertTrue(csv <= 1, "o CSV de produtos fez " + csv + " consultas (antes: 21)");
    }
}
