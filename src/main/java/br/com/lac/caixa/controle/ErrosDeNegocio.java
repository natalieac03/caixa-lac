package br.com.lac.caixa.controle;
import br.com.lac.caixa.servico.RegraDeNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Map;
@ControllerAdvice
public class ErrosDeNegocio {
    @ExceptionHandler({RegraDeNegocioException.class, IllegalArgumentException.class})
    public Object tratar(RuntimeException ex, HttpServletRequest request, RedirectAttributes flash) {
        String uri=request.getRequestURI();
        if (uri.equals("/venda/previa")) return ResponseEntity.badRequest().body(Map.of("erro", ex.getMessage()));
        flash.addFlashAttribute("erro", ex.getMessage());
        String destino = uri.startsWith("/estoque") ? "/estoque" : uri.startsWith("/descontos") ? "/descontos" : uri.startsWith("/historico") ? "/historico" : uri.startsWith("/fechamento") ? "/fechamento" : "/venda/nova";
        return "redirect:" + destino;
    }
}
