package br.com.lac.caixa.config;

import br.com.lac.caixa.CaixaApplication;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Sem APP_SENHA válida o contexto não sobe, em qualquer perfil, e a senha não vai para o log. */
@ExtendWith(OutputCaptureExtension.class)
class SenhaObrigatoriaTest {

    /** "ausente" = variável não definida; os outros valores vão como APP_SENHA. */
    @ParameterizedTest(name = "perfil {0}, APP_SENHA {1}")
    @CsvSource({"local, ausente", "prod, ausente", "local, vazia", "prod, vazia", "local, Sete7ch", "prod, Sete7ch"})
    void contextoNaoSobe(String perfil, String senha, CapturedOutput saida) {
        assumeTrue(System.getenv("APP_SENHA") == null, "APP_SENHA definida no ambiente de quem roda os testes");
        assumeFalse(perfil.equals("local") && Files.exists(Path.of("senha-local.properties")),
                "senha-local.properties existe nesta pasta");
        var args = new ArrayList<String>(java.util.List.of(
                "--spring.profiles.active=" + perfil,
                // Nunca abre o H2 em arquivo do desenvolvedor nem o Postgres.
                "--spring.datasource.url=jdbc:h2:mem:senha_" + perfil + "_" + senha.hashCode() + ";DB_CLOSE_DELAY=-1",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.datasource.username=sa", "--spring.datasource.password="));
        if (senha.equals("vazia")) args.add("--APP_SENHA=");
        else if (!senha.equals("ausente")) args.add("--APP_SENHA=" + senha);

        var erro = assertThrows(Exception.class, () -> new SpringApplicationBuilder(CaixaApplication.class)
                .web(WebApplicationType.NONE).run(args.toArray(String[]::new)).close());

        Throwable causa = erro;
        while (causa != null && !(causa instanceof SenhaDeAcesso.SenhaInvalidaException)) causa = causa.getCause();
        assertNotNull(causa, "a falha deveria ser a da senha: " + erro);
        assertTrue(saida.getAll().contains("Defina a variável APP_SENHA (mínimo 8 caracteres)"));
        if (senha.equals("Sete7ch")) assertFalse(saida.getAll().contains("Sete7ch"), "a senha apareceu no log");
    }

    @org.junit.jupiter.api.Test
    void oitoCaracteresBastamEEspacosNaoContamComoSenha() {
        var oito = new SenhaDeAcesso(new MockEnvironment().withProperty("app.senha", "12345678"));
        assertTrue(oito.confere("12345678"));
        assertFalse(oito.confere("1234567"));
        assertFalse(oito.confere(null));
        assertThrows(SenhaDeAcesso.SenhaInvalidaException.class,
                () -> new SenhaDeAcesso(new MockEnvironment().withProperty("app.senha", "        ")));
    }
}
