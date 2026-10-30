package br.com.lac.caixa.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.lac.caixa.CaixaApplication;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

class DadosIniciaisTest {

    long produtosAoSubir(String perfil, String banco) {
        try (var contexto = new SpringApplicationBuilder(CaixaApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.profiles.active=" + perfil,
                        "--spring.datasource.url=jdbc:h2:mem:" + banco + ";DB_CLOSE_DELAY=-1",
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.config.import=",
                        "--APP_SENHA=senha-de-teste-lac")) {
            return contexto.getBean(ProdutoRepository.class).count();
        }
    }

    @Test
    @DisplayName("Perfil prod com tabela vazia: nenhum produto de exemplo é criado")
    void producaoNaoSemeia() {
        assertEquals(0, produtosAoSubir("prod", "seed_prod"));
    }

    @Test
    @DisplayName("Perfil local com tabela vazia: cria os produtos de exemplo")
    void localSemeia() {
        assertEquals(3, produtosAoSubir("local", "seed_local"));
    }
}
