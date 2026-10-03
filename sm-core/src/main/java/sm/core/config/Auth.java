package sm.core.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuracao da autenticacao por token (JWT) nos endpoints.
 *
 * <p>Propriedades: {@code sm.core.security.auth.*}</p>
 *
 * <p>{@code mode=REPORT} (por omissao) registra em log os pedidos sem token
 * valido mas <b>nao bloqueia</b> — permite ativar a autenticacao sem risco de
 * partir clientes existentes. {@code mode=ENFORCE} passa a devolver
 * {@code 401} a todos os pedidos sem token valido fora de {@code publicPaths}.</p>
 */
public class Auth {

    private boolean enabled = true;

    /** REPORT (observa) ou ENFORCE (bloqueia). */
    private String mode = "REPORT";

    /**
     * Caminhos que nao exigem token (comparados por prefixo): autenticacao,
     * registo, ativacao de conta, sonda e monitorizacao.
     */
    private List<String> publicPaths = new ArrayList<>(List.of(
            "/sm/login",
            "/sm/isAuthenticated",
            "/sm/extendSession",
            "/sm/createUtilizador",
            "/sm/reenviarEmailAtivacao",
            "/sm/activateuser",
            "/sm/getcode",
            "/sm/sonda",
            "/sm/helloworld",
            "/actuator"));

    /** Cabecalhos onde o token pode ser enviado (por ordem de tentativa). */
    private List<String> tokenHeaders = new ArrayList<>(List.of(
            "Authorization", "X-Auth-Token", "Token"));

    /** Parametro de query aceite como alternativa (ex: ?token=xxx). */
    private String tokenQueryParameter = "token";

    /** Exigir token valido no handshake WebSocket (via ?token=xxx). */
    private boolean websocketEnforce = false;

    /** Intervalo minimo entre avisos de autenticacao no log (ms). */
    private long reportLogIntervalMillis = 5000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    /** Indica se os pedidos sem token valido devem ser bloqueados. */
    public boolean isEnforce() {
        return mode != null && "ENFORCE".equalsIgnoreCase(mode.trim());
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public List<String> getTokenHeaders() {
        return tokenHeaders;
    }

    public void setTokenHeaders(List<String> tokenHeaders) {
        this.tokenHeaders = tokenHeaders;
    }

    public String getTokenQueryParameter() {
        return tokenQueryParameter;
    }

    public void setTokenQueryParameter(String tokenQueryParameter) {
        this.tokenQueryParameter = tokenQueryParameter;
    }

    public boolean isWebsocketEnforce() {
        return websocketEnforce;
    }

    public void setWebsocketEnforce(boolean websocketEnforce) {
        this.websocketEnforce = websocketEnforce;
    }

    public long getReportLogIntervalMillis() {
        return reportLogIntervalMillis;
    }

    public void setReportLogIntervalMillis(long reportLogIntervalMillis) {
        this.reportLogIntervalMillis = reportLogIntervalMillis;
    }

    /** Verifica se o caminho esta isento de token. */
    public boolean isPublicPath(String path) {
        if (path == null || publicPaths == null) {
            return false;
        }
        for (String prefix : publicPaths) {
            if (prefix != null && !prefix.isBlank() && path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
