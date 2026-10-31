package br.com.lac.caixa.migracao;

import java.io.IOException;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Quantas migrações versionadas existem, comuns e só do PostgreSQL. */
public final class Migracoes {
    private Migracoes() {}

    public static int quantidade() {
        return contar("classpath:db/migration/V*.sql");
    }

    public static int quantidadePostgres() {
        return quantidade() + contar("classpath:db/migration-postgresql/V*.sql");
    }

    private static int contar(String padrao) {
        try {
            return new PathMatchingResourcePatternResolver().getResources(padrao).length;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
