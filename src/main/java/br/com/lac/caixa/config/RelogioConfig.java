package br.com.lac.caixa.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Relógio da aplicação, sempre no horário de Brasília. */
@Configuration
class RelogioConfig {
    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Bean
    @ConditionalOnMissingBean
    Clock relogio() {
        return Clock.system(FUSO);
    }
}
