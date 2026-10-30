package br.com.lac.caixa.migracao;

import java.io.IOException;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Quantas migrações versionadas existem (para os testes não quebrarem a cada migração nova). */
public final class Migracoes {
    private Migracoes() {}

    public static int quantidade() {
        try {
            return new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*.sql").length;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
