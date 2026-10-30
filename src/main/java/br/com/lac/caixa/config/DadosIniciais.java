package br.com.lac.caixa.config;

import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.repositorio.CategoriaRepository;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

@Configuration
public class DadosIniciais {

    @Bean
    CommandLineRunner semear(ProdutoRepository produtos, CategoriaRepository categorias) {
        return args -> {
            if (produtos.count() > 0) {
                return;
            }
            Categoria boton = categorias.save(new Categoria("Bóton"));
            Categoria adesivo = categorias.save(new Categoria("Adesivo Grande"));

            Produto p1 = new Produto("Bóton Capivara", new BigDecimal("6.00"), 30);
            p1.setCategoria(boton);
            Produto p2 = new Produto("Bóton Logo LAC", new BigDecimal("6.00"), 30);
            p2.setCategoria(boton);
            Produto p3 = new Produto("Adesivo Capivara Grande", new BigDecimal("8.00"), 25);
            p3.setCategoria(adesivo);

            produtos.saveAll(List.of(p1, p2, p3));
        };
    }
}
