package br.com.lac.caixa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** docs/DEPLOY.md e railway.json acompanham o código. */
class DeployDocsTest {
    static final Path DEPLOY = Path.of("docs", "DEPLOY.md");

    @Test
    @DisplayName("Toda variável ${...} das properties está documentada no DEPLOY.md")
    void variaveisDocumentadas() throws Exception {
        String doc = Files.readString(DEPLOY);
        var variaveis = new TreeSet<String>();
        try (Stream<Path> arquivos = Files.list(Path.of("src", "main", "resources"))) {
            for (Path p :
                    arquivos.filter(f -> f.toString().endsWith(".properties")).toList()) {
                var m = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)").matcher(Files.readString(p));
                while (m.find()) variaveis.add(m.group(1));
            }
        }
        assertTrue(variaveis.contains("MP_ACCESS_TOKEN") && variaveis.contains("APP_SENHA"), variaveis.toString());
        for (String v : variaveis) assertTrue(doc.contains("`" + v + "`"), "DEPLOY.md sem a variável " + v);
    }

    @Test
    @DisplayName("Toda migração Flyway está na tabela do DEPLOY.md, em ordem")
    void migracoesDocumentadas() throws Exception {
        String doc = Files.readString(DEPLOY);
        int anterior = -1;
        try (Stream<Path> arquivos = Stream.concat(
                Files.list(Path.of("src", "main", "resources", "db", "migration")),
                Files.list(Path.of("src", "main", "resources", "db", "migration-postgresql")))) {
            var nomes = arquivos.map(p -> p.getFileName().toString())
                    .sorted((a, b) -> Integer.compare(versao(a), versao(b)))
                    .toList();
            for (String nome : nomes) {
                String linha =
                        "| V" + versao(nome) + " `" + nome.substring(nome.indexOf("__") + 2, nome.length() - 4) + "` |";
                int pos = doc.indexOf(linha);
                assertTrue(pos >= 0, "DEPLOY.md sem a migração " + nome);
                assertTrue(pos > anterior, "fora de ordem: " + nome);
                anterior = pos;
            }
        }
    }

    static int versao(String arquivo) {
        return Integer.parseInt(arquivo.substring(1, arquivo.indexOf("__")));
    }

    @Test
    @DisplayName("railway.json: healthcheck em /actuator/health com 300 s, 1 réplica e restart em falha")
    void railwayJson() throws Exception {
        var json = new JsonMapper().readTree(Path.of("railway.json").toFile());
        assertEquals(
                "https://railway.com/railway.schema.json", json.path("$schema").asString());
        var deploy = json.path("deploy");
        assertEquals("/actuator/health", deploy.path("healthcheckPath").asString());
        assertEquals(300, deploy.path("healthcheckTimeout").asInt());
        assertEquals(1, deploy.path("numReplicas").asInt());
        assertEquals("ON_FAILURE", deploy.path("restartPolicyType").asString());
    }
}
