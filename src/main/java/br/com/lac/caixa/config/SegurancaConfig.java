package br.com.lac.caixa.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Liga o interceptor de login em tudo, menos login, logout, estáticos, health e webhook. */
@Configuration
public class SegurancaConfig implements WebMvcConfigurer {

    private final AutenticacaoInterceptor interceptor;

    public SegurancaConfig(AutenticacaoInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .excludePathPatterns(
                        "/login",
                        "/logout",
                        "/error",
                        "/actuator/health",
                        "/webhooks/mercadopago",
                        "/css/**",
                        "/img/**",
                        "/favicon.ico");
    }
}
