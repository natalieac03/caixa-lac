package br.com.lac.caixa;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Ponto de entrada: fixa o fuso de Brasília e sobe o Spring Boot. */
@SpringBootApplication
public class CaixaApplication {
    public static void main(String[] args) {
        fixarFuso();
        SpringApplication.run(CaixaApplication.class, args);
    }

    static void fixarFuso() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
    }
}
