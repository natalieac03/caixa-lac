package br.com.lac.caixa.controle;

import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import br.com.lac.caixa.servico.CaixaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@SpringBootTest(properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:idempotencia;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class IdempotenciaHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ProdutoRepository produtos;
    @SpyBean CaixaService caixa;

    @Test
    @DisplayName("Mesmo token de novo: o serviço devolve a venda existente e a tela avisa que já estava registrada")
    void tokenRepetido() throws Exception {
        var p = produtos.save(new Produto("Idempotente", new BigDecimal("6"), 10));
        String token = UUID.randomUUID().toString();
        var venda = post("/venda").sessionAttr("autenticado", true).param("token", token)
                .param("qtd_" + p.getId(), "1").param("pag_forma_0", "PIX");
        mvc.perform(venda).andExpect(redirectedUrl("/historico"));

        mvc.perform(venda)
           .andExpect(redirectedUrl("/historico"))
           .andExpect(flash().attribute("sucesso", org.hamcrest.Matchers.endsWith("já estava registrada.")));
    }

    @Test
    @DisplayName("Corrida: índice único estoura e o token já está gravado -> volta ao histórico sem erro")
    void corridaComTokenGravado() throws Exception {
        String token = UUID.randomUUID().toString();
        doThrow(new DataIntegrityViolationException("uk_venda_token")).when(caixa)
                .registrarVenda(any(), any(), any(), any(), eq(token));
        doReturn(true).when(caixa).tokenRegistrado(token);

        mvc.perform(post("/venda").sessionAttr("autenticado", true).param("token", token).param("pag_forma_0", "PIX"))
           .andExpect(redirectedUrl("/historico"))
           .andExpect(flash().attribute("sucesso", "Venda já registrada."));
    }

    @Test
    @DisplayName("Violação de integridade sem o token gravado não é engolida")
    void outraViolacaoSobe() {
        String token = UUID.randomUUID().toString();
        doThrow(new DataIntegrityViolationException("outra")).when(caixa)
                .registrarVenda(any(), any(), any(), any(), eq(token));

        var erro = assertThrows(Exception.class, () -> mvc.perform(post("/venda").sessionAttr("autenticado", true)
                .param("token", token).param("pag_forma_0", "PIX")));
        assertInstanceOf(DataIntegrityViolationException.class, erro.getCause());
    }
}
