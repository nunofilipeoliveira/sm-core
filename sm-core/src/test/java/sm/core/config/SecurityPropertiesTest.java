package sm.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verifica o binding das propriedades {@code sm.core.security.*} e os valores
 * por omissao aplicados quando nada esta configurado.
 */
class SecurityPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(SecurityProperties.class);

    @Test
    void valoresPorOmissaoSaoSeguros() {
        runner.run(context -> {
            SecurityProperties props = context.getBean(SecurityProperties.class);
            RateLimit limits = props.getRateLimit();

            assertTrue(limits.isEnabled());
            assertFalse(limits.isTrustClientIpHeader(),
                    "Por omissao nao se confia em cabecalhos de proxy (evita contornar limites)");
            assertTrue(limits.getMaxConcurrentRequests() > 0, "Deve existir limite de pedidos simultaneos");
            assertTrue(limits.getMaxRequestBodyBytes() > 0, "Deve existir limite de corpo de pedido");
            assertTrue(limits.getMaxTrackedClients() > 0, "O estado em memoria deve ser limitado");
            assertTrue(props.getWebsocket().getMaxTotalSessions() > 0);
            assertTrue(props.getWebsocket().getMaxSessionsPerClient() > 0);
        });
    }

    @Test
    void deveLigarPropriedadesDeRateLimit() {
        runner.withPropertyValues(
                "sm.core.security.rate-limit.login.capacity=7",
                "sm.core.security.rate-limit.login.refill-per-second=2.5",
                "sm.core.security.rate-limit.global.max-concurrent-requests=9",
                "sm.core.security.rate-limit.max-concurrent-requests=44",
                "sm.core.security.rate-limit.actuator-allowed-clients[0]=10.0.0.0/8",
                "sm.core.security.websocket.max-total-sessions=42")
                .run(context -> {
                    SecurityProperties props = context.getBean(SecurityProperties.class);

                    assertEquals(7L, props.getRateLimit().getLogin().getCapacity());
                    assertEquals(2.5d, props.getRateLimit().getLogin().getRefillPerSecond(), 0.0001d);
                    assertEquals(9, props.getRateLimit().getGlobal().getMaxConcurrentRequests());
                    assertEquals(44, props.getRateLimit().getMaxConcurrentRequests());
                    assertEquals(List.of("10.0.0.0/8"), props.getRateLimit().getActuatorAllowedClients());
                    assertEquals(42, props.getWebsocket().getMaxTotalSessions());
                });
    }

    @Test
    void caminhosPorOmissaoClassificamOsEndpoints() {
        runner.run(context -> {
            RateLimit limits = context.getBean(SecurityProperties.class).getRateLimit();

            assertTrue(limits.getLogin().matches("/sm/login"));
            assertTrue(limits.getLogin().matches("/sm/resetPWD/3"));
            assertFalse(limits.getLogin().matches("/sm/getAllJogadores/1"));
            assertTrue(limits.getSession().matches("/sm/extendSession"));
            assertTrue(limits.getEmail().matches("/sm/reenviarEmailAtivacao/1"));
            assertTrue(limits.getUpload().matches("/sm/uploadfoto/12/3"));
            assertTrue(limits.getProbe().matches("/sm/sonda"));
            assertTrue(limits.getActuator().matches("/actuator/prometheus"));
            assertFalse(limits.getGlobal().matches("/sm/login"));
        });
    }
}
