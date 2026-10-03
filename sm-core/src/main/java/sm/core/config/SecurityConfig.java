package sm.core.config;

import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import sm.core.security.RateLimitFilter;
import sm.core.security.TokenAuthenticationFilter;

/**
 * Registo das protecoes HTTP: filtro de rate limiting/limites de carga,
 * autenticacao por token JWT e politica CORS configuravel.
 */
@Configuration
public class SecurityConfig {

    /**
     * O filtro e registado explicitamente (e nao como bean) para garantir que e
     * o primeiro a correr e que nao e registado duas vezes pelo Spring Boot.
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(SecurityProperties properties) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(properties));
        registration.setName("rateLimitFilter");
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    /**
     * Autenticacao por token JWT. Corre depois do rate limiting (para que os
     * pedidos sejam limitados antes de qualquer trabalho de validacao) e antes
     * dos controladores.
     */
    @Bean
    public FilterRegistrationBean<TokenAuthenticationFilter> tokenAuthenticationFilterRegistration(
            SecurityProperties properties) {
        FilterRegistrationBean<TokenAuthenticationFilter> registration =
                new FilterRegistrationBean<>(new TokenAuthenticationFilter(properties));
        registration.setName("tokenAuthenticationFilter");
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }

    /**
     * CORS global configuravel via {@code sm.core.security.cors.*}.
     *
     * <p>Esta e a unica fonte da politica CORS: as anotacoes {@code @CrossOrigin}
     * que existiam nos controladores foram removidas (equivaliam a permitir
     * qualquer origem e sobrepunham-se a esta configuracao). Para restringir as
     * origens basta configurar {@code sm.core.security.cors.allowed-origin-patterns}
     * — ver docs/DoS-PROTECTION.md.</p>
     */
    @Bean
    public WebMvcConfigurer corsConfigurer(SecurityProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                Cors cors = properties.getCors();
                if (cors == null || !cors.isEnabled()) {
                    return;
                }
                CorsRegistration registration = registry.addMapping("/**")
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .maxAge(cors.getMaxAgeSeconds());

                List<String> patterns = cors.getAllowedOriginPatterns();
                if (patterns == null || patterns.isEmpty() || patterns.contains("*")) {
                    registration.allowedOriginPatterns("*");
                } else {
                    registration.allowedOriginPatterns(patterns.toArray(new String[0]));
                }
            }
        };
    }
}
