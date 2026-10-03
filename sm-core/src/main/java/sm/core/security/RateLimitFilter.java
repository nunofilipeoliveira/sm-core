package sm.core.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import sm.core.config.RateLimit;
import sm.core.config.Rule;
import sm.core.config.SecurityProperties;

/**
 * Filtro de protecao contra abuso e ataques DoS.
 *
 * <p>Aplica, antes de qualquer trabalho da aplicacao:</p>
 * <ol>
 *   <li>limite de tamanho do corpo do pedido (evita esgotar heap com JSON enorme);</li>
 *   <li>token bucket por IP e por tipo de endpoint (rajada + ritmo sustentado),
 *       respondendo 429 "Too Many Requests" com {@code Retry-After};</li>
 *   <li>limite de pedidos concorrentes por cliente e global (respondendo 503),
 *       para que a saturacao de threads/BD/heap seja limitada mesmo em ataques
 *       distribuidos;</li>
 *   <li>restricao opcional de {@code /actuator/**} a uma allowlist de IPs.</li>
 * </ol>
 *
 * <p>O estado em memoria e limitado ({@code max-tracked-clients}) e limpo
 * periodicamente, pelo que o proprio filtro nao pode ser usado para esgotar
 * memoria.</p>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final SecurityProperties properties;
    private final List<Rule> specificRules;

    private final Map<String, BucketHolder> buckets = new ConcurrentHashMap<>();
    private final Map<String, BucketHolder> overflowBuckets = new ConcurrentHashMap<>();

    private final Semaphore inFlight;
    private final AtomicLong lastCleanupNanos = new AtomicLong(System.nanoTime());
    private final AtomicLong blockedSinceLastLog = new AtomicLong();
    private final AtomicLong lastBlockedLogMillis = new AtomicLong();

    public RateLimitFilter(SecurityProperties properties) {
        this.properties = properties;
        RateLimit config = properties.getRateLimit();
        this.specificRules = config.specificRules();
        this.inFlight = new Semaphore(Math.max(1, config.getMaxConcurrentRequests()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        RateLimit config = properties.getRateLimit();
        if (!config.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        maybeCleanup(config);

        String path = request.getRequestURI();
        String client = ClientIpResolver.resolve(request, config.isTrustClientIpHeader(),
                config.getClientIpHeader());
        Rule rule = ruleFor(path, config);

        if (isActuatorBlocked(path, client, config)) {
            reject(response, HttpStatus.FORBIDDEN.value(), "forbidden",
                    "Acesso a este recurso nao esta autorizado.", null, rule, client, path);
            return;
        }

        long declaredLength = request.getContentLengthLong();
        if (declaredLength > config.getMaxRequestBodyBytes() && !isMultipart(request)) {
            reject(response, HttpStatus.PAYLOAD_TOO_LARGE.value(), "payload_too_large",
                    "Corpo do pedido excede o limite permitido.", null, rule, client, path);
            return;
        }

        BucketHolder holder = bucketFor(rule, client, config);
        if (!holder.tryConsume()) {
            reject(response, HttpStatus.TOO_MANY_REQUESTS.value(), "too_many_requests",
                    "Demasiados pedidos. Aguarde alguns instantes e tente novamente.",
                    holder.retryAfterSeconds(), rule, client, path);
            return;
        }

        Semaphore clientSemaphore = holder.concurrency();
        if (clientSemaphore != null && !clientSemaphore.tryAcquire()) {
            reject(response, HttpStatus.SERVICE_UNAVAILABLE.value(), "client_busy",
                    "Demasiados pedidos simultaneos. Tente novamente.", 1L, rule, client, path);
            return;
        }

        if (!inFlight.tryAcquire()) {
            if (clientSemaphore != null) {
                clientSemaphore.release();
            }
            reject(response, HttpStatus.SERVICE_UNAVAILABLE.value(), "service_busy",
                    "Servico sob carga elevada. Tente novamente dentro de instantes.", 2L,
                    rule, client, path);
            return;
        }

        try {
            log.debug("RateLimitFilter | client={} rule={} path={}", client, rule.getName(), path);
            chain.doFilter(request, response);
        } finally {
            inFlight.release();
            if (clientSemaphore != null) {
                clientSemaphore.release();
            }
        }
    }

    /** Regra mais especifica que corresponde ao caminho; caso contrario a global. */
    private Rule ruleFor(String path, RateLimit config) {
        for (Rule rule : specificRules) {
            if (rule.matches(path)) {
                return rule;
            }
        }
        return config.getGlobal();
    }

    private boolean isActuatorBlocked(String path, String client, RateLimit config) {
        if (!path.startsWith("/actuator")) {
            return false;
        }
        return !ClientIpResolver.isAllowed(client, config.getActuatorAllowedClients());
    }

    private boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }

    /**
     * Obtem (ou cria) o estado do cliente para a regra, respeitando o limite de
     * clientes em memoria. Acima do limite usa um bucket partilhado por regra
     * (mais generoso) para nunca crescer sem controlo.
     */
    private BucketHolder bucketFor(Rule rule, String client, RateLimit config) {
        String key = rule.getName() + '|' + client;
        BucketHolder holder = buckets.get(key);
        if (holder != null) {
            return holder;
        }
        if (buckets.size() >= Math.max(1, config.getMaxTrackedClients())) {
            cleanupBuckets(config);
            if (buckets.size() >= Math.max(1, config.getMaxTrackedClients())) {
                log.warn("RateLimitFilter | limite de clientes em memoria atingido ({}). "
                        + "A usar bucket partilhado para a regra {}.",
                        config.getMaxTrackedClients(), rule.getName());
                return overflowBuckets.computeIfAbsent(rule.getName(),
                        name -> new BucketHolder(rule, 2d));
            }
        }
        return buckets.computeIfAbsent(key, k -> new BucketHolder(rule, 1d));
    }

    /** Remove estado de clientes inativos (evita crescimento de memoria). */
    private void maybeCleanup(RateLimit config) {
        long intervalNanos = Math.max(1L, config.getCleanupIntervalSeconds()) * 1_000_000_000L;
        long now = System.nanoTime();
        long last = lastCleanupNanos.get();
        if (now - last >= intervalNanos && lastCleanupNanos.compareAndSet(last, now)) {
            cleanupBuckets(config);
        }
    }

    private void cleanupBuckets(RateLimit config) {
        long idleNanos = Math.max(1L, config.getBucketIdleSeconds()) * 1_000_000_000L;
        long now = System.nanoTime();
        int removed = 0;
        Iterator<Map.Entry<String, BucketHolder>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            BucketHolder holder = iterator.next().getValue();
            if (holder.isIdle(now, idleNanos)) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("RateLimitFilter | estado removido para {} clientes inativos (em memoria: {})",
                    removed, buckets.size());
        }
    }

    private void reject(HttpServletResponse response, int status, String code, String message,
            Long retryAfterSeconds, Rule rule, String client, String path) throws IOException {
        logBlocked(status, rule, client, path);
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (retryAfterSeconds != null) {
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        }
        StringBuilder body = new StringBuilder(96);
        body.append("{\"error\":\"").append(code).append("\",\"message\":\"").append(message).append('"');
        if (retryAfterSeconds != null) {
            body.append(",\"retryAfterSeconds\":").append(retryAfterSeconds);
        }
        body.append('}');
        response.getWriter().write(body.toString());
    }

    /**
     * Log com throttling: em ataque nao pode ser o proprio log a esgotar o disco.
     */
    private void logBlocked(int status, Rule rule, String client, String path) {
        long count = blockedSinceLastLog.incrementAndGet();
        long now = System.currentTimeMillis();
        long last = lastBlockedLogMillis.get();
        if (now - last >= 1000L && lastBlockedLogMillis.compareAndSet(last, now)) {
            log.warn("RateLimitFilter | {} pedidos bloqueados (status={} regra={} exemplo={} path={})",
                    count, status, rule.getName(), client, path);
            blockedSinceLastLog.set(0L);
        } else {
            log.debug("RateLimitFilter | pedido bloqueado status={} regra={} client={} path={}",
                    status, rule.getName(), client, path);
        }
    }

    /** Estado de um cliente para uma regra: token bucket + concorrencia. */
    private static final class BucketHolder {

        private final TokenBucket bucket;
        private final Semaphore concurrency;
        private final int permits;
        private volatile long lastActivityNanos = System.nanoTime();

        BucketHolder(Rule rule, double multiplier) {
            this.bucket = new TokenBucket(rule.getCapacity() * multiplier,
                    rule.getRefillPerSecond() * multiplier);
            this.permits = rule.getMaxConcurrentRequests();
            this.concurrency = permits > 0 ? new Semaphore(permits) : null;
        }

        boolean tryConsume() {
            lastActivityNanos = System.nanoTime();
            return bucket.tryConsume();
        }

        long retryAfterSeconds() {
            return bucket.retryAfterSeconds();
        }

        Semaphore concurrency() {
            return concurrency;
        }

        boolean isIdle(long nowNanos, long idleNanos) {
            if (nowNanos - lastActivityNanos <= idleNanos) {
                return false;
            }
            return concurrency == null || concurrency.availablePermits() >= permits;
        }
    }

    /** Token bucket thread-safe (rajada + reposicao continua). */
    private static final class TokenBucket {

        private final double capacity;
        private final double refillPerNano;
        private double tokens;
        private long lastRefillNanos;

        TokenBucket(double capacity, double refillPerSecond) {
            this.capacity = Math.max(1d, capacity);
            this.refillPerNano = Math.max(0d, refillPerSecond) / 1_000_000_000d;
            this.tokens = this.capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            refill(System.nanoTime());
            if (tokens >= 1d) {
                tokens -= 1d;
                return true;
            }
            return false;
        }

        synchronized long retryAfterSeconds() {
            refill(System.nanoTime());
            if (refillPerNano <= 0d) {
                return 60L;
            }
            double missingTokens = 1d - tokens;
            long seconds = (long) Math.ceil(missingTokens / (refillPerNano * 1_000_000_000d));
            return Math.max(1L, seconds);
        }

        private void refill(long nowNanos) {
            long delta = nowNanos - lastRefillNanos;
            if (delta <= 0L) {
                return;
            }
            lastRefillNanos = nowNanos;
            tokens = Math.min(capacity, tokens + (delta * refillPerNano));
        }
    }
}
