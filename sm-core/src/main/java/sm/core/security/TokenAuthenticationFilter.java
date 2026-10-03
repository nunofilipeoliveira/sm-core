package sm.core.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import sm.core.config.Auth;
import sm.core.config.SecurityProperties;

/**
 * Autenticacao dos endpoints por token JWT.
 *
 * <p>Todos os pedidos fora de {@code sm.core.security.auth.public-paths} tem de
 * apresentar um token valido (assinatura + expiracao, gerado pelo login).</p>
 *
 * <p>Modos ({@code sm.core.security.auth.mode}):</p>
 * <ul>
 *   <li>{@code REPORT} (por omissao) — registra em log os pedidos sem token
 *       valido mas nao bloqueia, permitindo ativar a autenticacao sem risco;</li>
 *   <li>{@code ENFORCE} — devolve {@code 401} a quem nao apresentar token valido.</li>
 * </ul>
 */
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TokenAuthenticationFilter.class);

    private final Auth auth;
    private final AtomicLong blockedSinceLastLog = new AtomicLong();
    private final AtomicLong lastLogMillis = new AtomicLong();

    public TokenAuthenticationFilter(SecurityProperties properties) {
        this.auth = properties.getAuth();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        // Preflight CORS (OPTIONS): por especificacao e lancado pelo navegador SEM
        // qualquer cabecalho de aplicacao (nunca leva o Authorization). Como o
        // front-end corre noutra origem, cada pedido com Authorization ou
        // Content-Type: application/json passa primeiro por aqui — logo este pedido
        // "sem token" e esperado e nao indica falta de autenticacao do cliente.
        // Bloqueá-lo devolveria 401 ao preflight e o navegador cancelava o pedido
        // real, dejando a aplicacao inutilizavel.
        if (isCorsPreflight(request)) {
            log.debug("TokenAuthenticationFilter | preflight CORS ignorado path={}",
                    request.getRequestURI());
            chain.doFilter(request, response);
            return;
        }

        if (!auth.isEnabled() || auth.isPublicPath(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String token = AuthTokens.extract(request, auth.getTokenHeaders(), auth.getTokenQueryParameter());
        Claims claims = AuthTokens.validate(token);

        if (claims != null) {
            request.setAttribute(AuthTokens.USER_ATTRIBUTE, claims.getSubject());
            log.debug("TokenAuthenticationFilter | token valido (user={}) path={}",
                    claims.getSubject(), request.getRequestURI());
            chain.doFilter(request, response);
            return;
        }

        String motivo = token == null ? "sem token" : "token invalido";
        if (auth.isEnforce()) {
            logBlocked(motivo, request);
            reject(response);
            return;
        }

        logBlocked(motivo + " (modo REPORT: pedido permitido)", request);
        chain.doFilter(request, response);
    }

    /**
     * Identifica um preflight CORS: pedido {@code OPTIONS} que declara a origem e o
     * metodo pretendido. Nao tem token por definicao, logo nao deve contar como
     * "pedido sem autenticacao valida".
     */
    private boolean isCorsPreflight(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                && request.getHeader("Origin") != null
                && request.getHeader("Access-Control-Request-Method") != null;
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("WWW-Authenticate", "Bearer realm=\"sm\"");
        response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\""
                + "Sessao invalida ou expirada. Volte a autenticar-se.\"}");
    }

    /** Log com throttling (nao pode ser o log a esgotar o disco em ataque). */
    private void logBlocked(String motivo, HttpServletRequest request) {
        long count = blockedSinceLastLog.incrementAndGet();
        long now = System.currentTimeMillis();
        long interval = Math.max(1000L, auth.getReportLogIntervalMillis());
        long last = lastLogMillis.get();
        if (now - last >= interval && lastLogMillis.compareAndSet(last, now)) {
            log.warn("TokenAuthenticationFilter | {} pedidos sem autenticacao valida ({}): "
                    + "exemplo path={} ip={}",
                    count, motivo, request.getRequestURI(), request.getRemoteAddr());
            blockedSinceLastLog.set(0L);
        } else {
            log.debug("TokenAuthenticationFilter | {} path={} ip={}",
                    motivo, request.getRequestURI(), request.getRemoteAddr());
        }
    }
}
