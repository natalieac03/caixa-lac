package br.com.lac.caixa.migracao;

import java.io.IOException;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Quantas migrações versionadas existem (para os testes não quebrarem a cada migração nova). */
public final class Migracoes {
    private Migracoes() {}

    /** As de db/migration, que rodam em todos os bancos (H2 inclusive). */
    public static int quantidade() {
        return contar("classpath:db/migration/V*.sql");
    }

    /** No PostgreSQL: as comuns mais as de db/migration-postgresql. */
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
