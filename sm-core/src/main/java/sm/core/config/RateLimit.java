package sm.core.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Limites de trafego HTTP: token bucket por cliente (rajada + ritmo sustentado)
 * e limite de pedidos concorrentes (por cliente e global).
 *
 * <p>Propriedades: {@code sm.core.security.rate-limit.*}</p>
 */
public class RateLimit {

    private boolean enabled = true;

    /**
     * Usar o IP do cabecalho configurado em {@link #clientIpHeader} em vez do IP
     * do socket. ATENCAO: so ativar quando a aplicacao esta atras de um reverse
     * proxy fiavel e a porta da aplicacao NAO esta exposta diretamente; caso
     * contrario o atacante pode falsificar o cabecalho e contornar os limites.
     */
    private boolean trustClientIpHeader = false;

    private String clientIpHeader = "X-Forwarded-For";

    /** Numero maximo de clientes com estado em memoria (protege a heap). */
    private int maxTrackedClients = 20000;

    /** Tempo sem atividade apos o qual o estado de um cliente e removido. */
    private int bucketIdleSeconds = 900;

    /** Intervalo minimo entre limpezas de memoria. */
    private int cleanupIntervalSeconds = 120;

    /** Tamanho maximo aceite no corpo de pedidos que nao sejam multipart. */
    private long maxRequestBodyBytes = 1048576L;

    /** Pedidos HTTP em processamento simultaneo (protege threads/BD/heap). */
    private int maxConcurrentRequests = 100;

    /** Se preenchido, so estes IPs/CIDR podem aceder a {@code /actuator/**}. */
    private List<String> actuatorAllowedClients = new ArrayList<>();

    private Rule global = Rule.of("global", 300, 80, 20);
    private Rule login = Rule.of("login", 15, 0.5, 4,
            "/sm/login", "/sm/activateuser", "/sm/getcode", "/sm/resetPWD",
            "/sm/updateUser", "/sm/updateUserWithEscaloes", "/sm/createuser",
            "/sm/createUtilizador");
    private Rule session = Rule.of("session", 150, 15, 20,
            "/sm/isAuthenticated", "/sm/extendSession");
    private Rule email = Rule.of("email", 3, 0.02, 2, "/sm/reenviarEmailAtivacao");
    private Rule upload = Rule.of("upload", 30, 0.5, 3, "/sm/uploadfoto", "/sm/uploadLogo");
    private Rule probe = Rule.of("probe", 30, 1, 5, "/sm/sonda");
    private Rule actuator = Rule.of("actuator", 30, 2, 5, "/actuator");

    /** Todas as regras exceto a global (resolucao por caminho). */
    public List<Rule> specificRules() {
        List<Rule> rules = new ArrayList<>();
        rules.add(login);
        rules.add(session);
        rules.add(email);
        rules.add(upload);
        rules.add(probe);
        rules.add(actuator);
        rules.removeIf(rule -> rule == null);
        return rules;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTrustClientIpHeader() {
        return trustClientIpHeader;
    }

    public void setTrustClientIpHeader(boolean trustClientIpHeader) {
        this.trustClientIpHeader = trustClientIpHeader;
    }

    public String getClientIpHeader() {
        return clientIpHeader;
    }

    public void setClientIpHeader(String clientIpHeader) {
        this.clientIpHeader = clientIpHeader;
    }

    public int getMaxTrackedClients() {
        return maxTrackedClients;
    }

    public void setMaxTrackedClients(int maxTrackedClients) {
        this.maxTrackedClients = maxTrackedClients;
    }

    public int getBucketIdleSeconds() {
        return bucketIdleSeconds;
    }

    public void setBucketIdleSeconds(int bucketIdleSeconds) {
        this.bucketIdleSeconds = bucketIdleSeconds;
    }

    public int getCleanupIntervalSeconds() {
        return cleanupIntervalSeconds;
    }

    public void setCleanupIntervalSeconds(int cleanupIntervalSeconds) {
        this.cleanupIntervalSeconds = cleanupIntervalSeconds;
    }

    public long getMaxRequestBodyBytes() {
        return maxRequestBodyBytes;
    }

    public void setMaxRequestBodyBytes(long maxRequestBodyBytes) {
        this.maxRequestBodyBytes = maxRequestBodyBytes;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    public List<String> getActuatorAllowedClients() {
        return actuatorAllowedClients;
    }

    public void setActuatorAllowedClients(List<String> actuatorAllowedClients) {
        this.actuatorAllowedClients = actuatorAllowedClients;
    }

    public Rule getGlobal() {
        return global;
    }

    public void setGlobal(Rule global) {
        this.global = global;
    }

    public Rule getLogin() {
        return login;
    }

    public void setLogin(Rule login) {
        this.login = login;
    }

    public Rule getSession() {
        return session;
    }

    public void setSession(Rule session) {
        this.session = session;
    }

    public Rule getEmail() {
        return email;
    }

    public void setEmail(Rule email) {
        this.email = email;
    }

    public Rule getUpload() {
        return upload;
    }

    public void setUpload(Rule upload) {
        this.upload = upload;
    }

    public Rule getProbe() {
        return probe;
    }

    public void setProbe(Rule probe) {
        this.probe = probe;
    }

    public Rule getActuator() {
        return actuator;
    }

    public void setActuator(Rule actuator) {
        this.actuator = actuator;
    }
}
