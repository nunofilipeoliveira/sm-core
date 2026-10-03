package sm.core.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Politica CORS global (aplicada a todos os controladores).
 *
 * <p>Propriedades: {@code sm.core.security.cors.*}</p>
 */
public class Cors {

    private boolean enabled = true;
    private int maxAgeSeconds = 1800;
    private List<String> allowedOriginPatterns = new ArrayList<>(List.of("*"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxAgeSeconds() {
        return maxAgeSeconds;
    }

    public void setMaxAgeSeconds(int maxAgeSeconds) {
        this.maxAgeSeconds = maxAgeSeconds;
    }

    public List<String> getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }
}
