package br.com.lac.caixa.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Recusa (403) POST que veio de outra página da internet: Sec-Fetch-Site
 * cross-site, ou Origin de outro host (ou "null"). Reforço do cookie
 * SameSite contra um formulário de outro site que manda o navegador do caixa
 * registrar ou cancelar venda. Sem os cabeçalhos (navegador antigo, curl), passa.
 * Compara só o host: atrás do proxy do Railway a porta e o esquema que o app
 * vê podem não ser os do navegador. O webhook do Mercado Pago fica de fora:
 * vem do servidor deles e tem a própria assinatura.
 */
@Component
public class OrigemDoPost extends OncePerRequestFilter {
    static final String WEBHOOK = "/webhooks/mercadopago";

    private static final Logger LOG = LoggerFactory.getLogger(OrigemDoPost.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || WEBHOOK.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (deOutroSite(request)) {
            LOG.warn("POST {} recusado: veio de outro site.", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/plain;charset=UTF-8");
            response.getOutputStream().write("Pedido recusado: veio de outro site.".getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean deOutroSite(HttpServletRequest request) {
        if ("cross-site".equalsIgnoreCase(request.getHeader("Sec-Fetch-Site"))) return true;
        String origem = request.getHeader("Origin");
        if (origem == null) return false;
        try {
            String host = URI.create(origem.trim()).getHost();
            return host == null || !host.equalsIgnoreCase(request.getServerName());
        } catch (IllegalArgumentException malFormada) {
            return true;
        }
    }
}
