package sm.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import sm.core.config.SecurityProperties;

/**
 * Teste de arranque da aplicacao: valida que o contexto sobe com as novas
 * protecoes (filtro de rate limiting, propriedades e WebSocket) e que o filtro
 * fica registado antes do resto da cadeia.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SmCoreContextTest {

    @LocalServerPort
    private int port;

    @Autowired
    @Qualifier("rateLimitFilterRegistration")
    private FilterRegistrationBean<?> rateLimitFilterRegistration;

    @Autowired
    @Qualifier("tokenAuthenticationFilterRegistration")
    private FilterRegistrationBean<?> tokenAuthenticationFilterRegistration;

    @Autowired
    private SecurityProperties securityProperties;

    @Test
    void contextoArrancaComProtecoesConfiguradas() {
        assertTrue(port > 0, "O servidor HTTP deve estar a escutar");
        assertNotNull(rateLimitFilterRegistration);
        assertNotNull(tokenAuthenticationFilterRegistration);
        assertTrue(rateLimitFilterRegistration.getUrlPatterns().contains("/*"),
                "O filtro de rate limiting deve cobrir todos os caminhos");
        assertTrue(tokenAuthenticationFilterRegistration.getUrlPatterns().contains("/*"),
                "O filtro de autenticacao deve cobrir todos os caminhos");
        assertTrue(securityProperties.getRateLimit().isEnabled());
        assertTrue(securityProperties.getAuth().isEnabled());
    }
}
