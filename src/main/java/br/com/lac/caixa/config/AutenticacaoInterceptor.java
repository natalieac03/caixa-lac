package br.com.lac.caixa.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Exige login antes de cada tela; chamadas do JS sem sessão recebem 401 em JSON. */
@Component
public class AutenticacaoInterceptor implements HandlerInterceptor {

    static final String SESSAO_EXPIRADA = "{\"erro\":\"Sessão expirada\"}";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        boolean autenticado = session != null && Boolean.TRUE.equals(session.getAttribute("autenticado"));
        if (autenticado) {
            return true;
        }
        if (querJson(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(SESSAO_EXPIRADA);
            return false;
        }
        response.sendRedirect(request.getContextPath() + "/login");
        return false;
    }

    private static boolean querJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return request.getRequestURI().equals(request.getContextPath() + "/venda/previa")
                || request.getRequestURI().startsWith(request.getContextPath() + "/venda/pix")
                || (accept != null && accept.contains("application/json"));
    }
}
