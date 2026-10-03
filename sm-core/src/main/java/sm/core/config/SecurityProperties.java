package sm.core.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriedades de endurecimento (hardening) contra abuso e ataques DoS.
 *
 * <p>Prefixo: {@code sm.core.security}. Os valores por omissao sao conservadores
 * para producao e podem ser afinados nos ficheiros {@code application-*.properties}
 * sem alterar codigo.</p>
 */
@Component
@ConfigurationProperties(prefix = "sm.core.security")
public class SecurityProperties {

    private RateLimit rateLimit = new RateLimit();
    private Cors cors = new Cors();
    private WebSocketLimits websocket = new WebSocketLimits();
    private Auth auth = new Auth();

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public WebSocketLimits getWebsocket() {
        return websocket;
    }

    public void setWebsocket(WebSocketLimits websocket) {
        this.websocket = websocket;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }
}
