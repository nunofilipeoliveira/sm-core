package sm.core.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;
import org.springframework.web.socket.server.HandshakeInterceptor;

import sm.core.config.SecurityProperties;
import sm.core.config.WebSocketLimits;

/**
 * Limita as ligacoes WebSocket/STOMP para evitar esgotamento de threads/memoria:
 * recusa handshakes quando o numero total de sessoes atinge o maximo e fecha
 * ligacoes acima do limite por cliente.
 */
@Component
public class WebSocketSessionGuard implements HandshakeInterceptor, WebSocketHandlerDecoratorFactory {

    /** Chave usada nos atributos da sessao para guardar o IP do cliente. */
    public static final String CLIENT_ATTR = "sm.clientIp";

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionGuard.class);

    private final SecurityProperties properties;
    private final Map<String, AtomicInteger> sessionsByClient = new ConcurrentHashMap<>();
    private final Map<String, String> clientBySession = new ConcurrentHashMap<>();
    private final AtomicInteger totalSessions = new AtomicInteger();

    public WebSocketSessionGuard(SecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {

        WebSocketLimits limits = properties.getWebsocket();
        if (totalSessions.get() >= Math.max(1, limits.getMaxTotalSessions())) {
            log.warn("WebSocketSessionGuard | limite global de sessoes atingido ({}); handshake recusado",
                    limits.getMaxTotalSessions());
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return false;
        }

        // Autenticacao do handshake (opcional): token via ?token=... ou cabecalho
        if (properties.getAuth().isWebsocketEnforce()) {
            String token = AuthTokens.extractFromQuery(request.getURI().getQuery(),
                    properties.getAuth().getTokenQueryParameter());
            if (AuthTokens.validate(token) == null) {
                log.warn("WebSocketSessionGuard | handshake sem token valido; recusado (uri={})",
                        request.getURI());
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
        }

        attributes.put(CLIENT_ATTR, resolveClient(request));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // Nada a fazer: a contagem e feita apenas para sessoes efetivamente abertas.
    }

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {

            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                String client = String.valueOf(session.getAttributes().getOrDefault(CLIENT_ATTR, "unknown"));
                if (!acquire(client)) {
                    log.warn("WebSocketSessionGuard | limite de sessoes por cliente atingido ({}); a encerrar sessao",
                            client);
                    session.close(CloseStatus.POLICY_VIOLATION);
                    return;
                }
                clientBySession.put(session.getId(), client);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                release(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                release(session.getId());
                super.handleTransportError(session, exception);
            }
        };
    }

    private boolean acquire(String client) {
        WebSocketLimits limits = properties.getWebsocket();
        int maxPerClient = Math.max(1, limits.getMaxSessionsPerClient());
        AtomicInteger counter = sessionsByClient.computeIfAbsent(client, key -> new AtomicInteger());
        if (counter.get() >= maxPerClient) {
            return false;
        }
        if (totalSessions.incrementAndGet() > Math.max(1, limits.getMaxTotalSessions())) {
            totalSessions.decrementAndGet();
            return false;
        }
        counter.incrementAndGet();
        return true;
    }

    private void release(String sessionId) {
        String client = clientBySession.remove(sessionId);
        if (client == null) {
            return;
        }
        AtomicInteger counter = sessionsByClient.get(client);
        if (counter != null && counter.decrementAndGet() <= 0) {
            sessionsByClient.remove(client, counter);
        }
        totalSessions.decrementAndGet();
    }

    private String resolveClient(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return ClientIpResolver.resolve(servletRequest.getServletRequest(),
                    properties.getRateLimit().isTrustClientIpHeader(),
                    properties.getRateLimit().getClientIpHeader());
        }
        return "unknown";
    }
}
