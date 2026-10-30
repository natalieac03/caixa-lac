package br.com.lac.caixa.controle;

import br.com.lac.caixa.servico.RegraDeNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Erros de regra de negócio e de validação viram mensagem na tela de origem,
 * em vez de página de erro. A prévia da venda (chamada pelo JS) recebe JSON.
 */
@ControllerAdvice
public class ErrosDeNegocio {

    @ExceptionHandler({RegraDeNegocioException.class, IllegalArgumentException.class})
    public Object regraDeNegocio(RuntimeException ex, HttpServletRequest request, RedirectAttributes flash) {
        return responder(ex.getMessage(), request, flash);
    }

    /** Parâmetros anotados nos controllers (ex.: @Min na quantidade). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public Object parametroInvalido(HandlerMethodValidationException ex, HttpServletRequest request,
                                    RedirectAttributes flash) {
        String mensagem = ex.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(" "));
        return responder(mensagem, request, flash);
    }

    /** Restrições das entidades, conferidas pelo Hibernate antes de gravar. */
    @ExceptionHandler(ConstraintViolationException.class)
    public Object entidadeInvalida(ConstraintViolationException ex, HttpServletRequest request,
                                   RedirectAttributes flash) {
        String mensagem = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(" "));
        return responder(mensagem, request, flash);
    }

    private Object responder(String mensagem, HttpServletRequest request, RedirectAttributes flash) {
        String uri = request.getRequestURI();
        if (uri.equals("/venda/previa")) {
            return ResponseEntity.badRequest().body(Map.of("erro", mensagem));
        }
        flash.addFlashAttribute("erro", mensagem);
        return "redirect:" + origem(uri);
    }

    private static String origem(String uri) {
        for (String tela : new String[]{"/estoque", "/descontos", "/historico", "/fechamento"}) {
            if (uri.startsWith(tela)) return tela;
        }
        return "/venda/nova";
    }
}
