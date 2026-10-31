package br.com.lac.caixa;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CaixaApplication {
    public static void main(String[] args) {
        fixarFuso();
        SpringApplication.run(CaixaApplication.class, args);
    }

    /**
     * A JVM em Brasília antes de subir o Spring: o que ainda usar o fuso padrão
     * (logs, bibliotecas) fica igual ao relógio da aplicação (RelogioConfig).
     */
    static void fixarFuso() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
    }
}
