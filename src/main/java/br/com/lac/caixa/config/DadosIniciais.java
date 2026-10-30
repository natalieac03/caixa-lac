package br.com.lac.caixa.config;

import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.repositorio.CategoriaRepository;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import br.com.lac.caixa.servico.EstoqueService;
import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Produtos de exemplo na primeira execução, só no perfil local (desenvolvimento).
 * Em produção um banco vazio continua vazio: a LAC cadastra o próprio estoque.
 */
@Configuration
@Profile("local")
public class DadosIniciais {

    @Bean
    CommandLineRunner semear(ProdutoRepository produtos, CategoriaRepository categorias, EstoqueService estoque) {
        return args -> {
            if (produtos.count() > 0) {
                return;
            }
            Categoria boton = categorias.save(new Categoria("Bóton"));
            Categoria adesivo = categorias.save(new Categoria("Adesivo Grande"));

            // Pelo serviço, para cada produto já nascer com o movimento do estoque inicial.
            estoque.criarNovo("Bóton Capivara", new BigDecimal("6.00"), 30, boton);
            estoque.criarNovo("Bóton Logo LAC", new BigDecimal("6.00"), 30, boton);
            estoque.criarNovo("Adesivo Capivara Grande", new BigDecimal("8.00"), 25, adesivo);
        };
    }
}
