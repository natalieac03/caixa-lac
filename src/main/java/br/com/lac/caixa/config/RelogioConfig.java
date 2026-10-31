package br.com.lac.caixa.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio da aplicação, sempre no horário de Brasília, qualquer que seja o fuso
 * da máquina (o contêiner do Railway roda em UTC). As colunas de data são
 * TIMESTAMP sem fuso e LocalDateTime: o que se grava é a hora de Brasília.
 * Os testes trocam por um relógio controlado.
 */
@Configuration
class RelogioConfig {
    /** O fuso de todas as datas e horas do caixa. */
    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Bean
    @ConditionalOnMissingBean
    Clock relogio() {
        return Clock.system(FUSO);
    }
}
