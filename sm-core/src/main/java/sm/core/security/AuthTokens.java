package sm.core.security;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import sm.core.utils.TokenValidator;

/**
 * Extracao e validacao dos tokens JWT recebidos nos pedidos.
 *
 * <p>A query string e analisada manualmente (em vez de {@code getParameter})
 * para nao forcar o parsing do corpo de pedidos multipart/JSON.</p>
 */
public final class AuthTokens {

    /** Atributo do pedido onde fica o utilizador autenticado. */
    public static final String USER_ATTRIBUTE = "sm.auth.user";

    private AuthTokens() {
    }

    /**
     * Extrai o token dos cabecalhos configurados e, em alternativa, da query
     * string ({@code ?token=...}).
     */
    public static String extract(HttpServletRequest request, List<String> headerNames, String queryParameter) {
        if (headerNames != null) {
            for (String header : headerNames) {
                if (header == null || header.isBlank()) {
                    continue;
                }
                String token = normalize(request.getHeader(header));
                if (token != null) {
                    return token;
                }
            }
        }
        return extractFromQuery(request.getQueryString(), queryParameter);
    }

    /** Extrai o token da query string (ex: {@code ?token=xxx}). */
    public static String extractFromQuery(String rawQuery, String parameterName) {
        if (rawQuery == null || rawQuery.isBlank() || parameterName == null || parameterName.isBlank()) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            if (separator <= 0 || !parameterName.equals(pair.substring(0, separator))) {
                continue;
            }
            return normalize(decode(pair.substring(separator + 1)));
        }
        return null;
    }

    /** Valida a assinatura/expiracao do token. */
    public static Claims validate(String token) {
        return TokenValidator.parseValidToken(token);
    }

    /** Remove o prefixo {@code Bearer } e valida o formato minimo. */
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String token = value.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }
        if (token.isEmpty() || token.length() > 4096) {
            return null;
        }
        return token;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
