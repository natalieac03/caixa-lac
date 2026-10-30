package br.com.lac.caixa.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Relógio da aplicação (os testes trocam por um relógio controlado). */
@Configuration
class RelogioConfig {
    @Bean
    @ConditionalOnMissingBean
    Clock relogio() {
        return Clock.systemDefaultZone();
    }
}
