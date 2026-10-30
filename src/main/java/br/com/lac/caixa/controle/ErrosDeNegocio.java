package br.com.lac.caixa.controle;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Erros viram mensagem na tela de origem, em vez de página de erro. A prévia
 * da venda (chamada pelo JS) e quem pede JSON recebem {"erro": "..."}.
 * - RegraDeNegocioException e validação: a mensagem é para a pessoa no caixa.
 * - IllegalArgumentException (inclui NumberFormatException): erro de programa;
 *   vai para o log com a pilha e a tela mostra só "Algo deu errado".
 */
@ControllerAdvice
public class ErrosDeNegocio {

    static final String ALGO_DEU_ERRADO = "Algo deu errado. Tente de novo.";

    private static final Logger LOG = LoggerFactory.getLogger(ErrosDeNegocio.class);

    @ExceptionHandler(RegraDeNegocioException.class)
    public Object regraDeNegocio(RegraDeNegocioException ex, HttpServletRequest request, RedirectAttributes flash) {
        return responder(ex.getMessage(), HttpStatus.BAD_REQUEST, request, flash);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Object erroDePrograma(IllegalArgumentException ex, HttpServletRequest request, RedirectAttributes flash) {
        LOG.error("Erro inesperado em {} {}", request.getMethod(), request.getRequestURI(), ex);
        return responder(ALGO_DEU_ERRADO, HttpStatus.INTERNAL_SERVER_ERROR, request, flash);
    }

    /** Parâmetros anotados nos controllers (ex.: @Min na quantidade). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public Object parametroInvalido(
            HandlerMethodValidationException ex, HttpServletRequest request, RedirectAttributes flash) {
        String mensagem = ex.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(" "));
        return responder(mensagem, HttpStatus.BAD_REQUEST, request, flash);
    }

    private Object responder(String mensagem, HttpStatus status, HttpServletRequest request, RedirectAttributes flash) {
        if (querJson(request)) {
            return ResponseEntity.status(status).body(Map.of("erro", mensagem));
        }
        flash.addFlashAttribute("erro", mensagem);
        return "redirect:" + origem(request);
    }

    static boolean querJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return request.getRequestURI().equals("/venda/previa")
                || (accept != null && accept.contains("application/json"));
    }

    /**
     * Volta para a página de onde veio (o Referer), mantendo filtros como os do
     * histórico, se ela for do próprio site. Só o caminho e a busca são usados,
     * nunca o endereço de outro site. Sem Referer válido, a lista da área.
     */
    static String origem(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                int porta = uri.getPort() != -1 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
                String caminho = uri.getRawPath();
                if (uri.getHost() != null
                        && uri.getHost().equalsIgnoreCase(request.getServerName())
                        && porta == request.getServerPort()
                        && caminho != null
                        && caminho.startsWith("/")
                        && !caminho.startsWith("//")) {
                    return caminho + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
                }
            } catch (IllegalArgumentException refererMalFormado) {
                // cai na lista da área
            }
        }
        String uri = request.getRequestURI();
        for (String tela : new String[] {"/estoque", "/descontos", "/historico", "/fechamento"}) {
            if (uri.startsWith(tela)) return tela;
        }
        return "/venda/nova";
    }
}
