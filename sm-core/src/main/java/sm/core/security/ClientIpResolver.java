package sm.core.security;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolucao do IP do cliente e validacao de allowlists (IP exato ou CIDR IPv4).
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    /**
     * Determina a chave do cliente.
     *
     * @param request     pedido HTTP
     * @param trustHeader se o cabecalho de proxy deve ser considerado
     * @param headerName  nome do cabecalho (ex: X-Forwarded-For)
     */
    public static String resolve(HttpServletRequest request, boolean trustHeader, String headerName) {
        String socketIp = normalize(request.getRemoteAddr());
        if (!trustHeader) {
            return socketIp;
        }

        String header = request.getHeader(headerName);
        if (header == null || header.isBlank()) {
            header = request.getHeader("X-Real-IP");
        }
        if (header != null && !header.isBlank()) {
            int comma = header.indexOf(',');
            String candidate = (comma > 0 ? header.substring(0, comma) : header).trim();
            if (isPlausibleIp(candidate)) {
                return candidate;
            }
        }
        return socketIp;
    }

    /** Verifica se o cliente pertence a allowlist (vazia = sem restricao). */
    public static boolean isAllowed(String clientIp, List<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        if (clientIp == null) {
            return false;
        }
        for (String entry : allowed) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String pattern = entry.trim();
            if (pattern.equals(clientIp)) {
                return true;
            }
            int slash = pattern.indexOf('/');
            if (slash > 0 && matchesCidr(clientIp, pattern.substring(0, slash),
                    parsePrefix(pattern.substring(slash + 1)))) {
                return true;
            }
        }
        return false;
    }

    static boolean isPlausibleIp(String value) {
        if (value == null || value.isEmpty() || value.length() > 45) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean valid = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F') || c == '.' || c == ':' || c == '%';
            if (!valid) {
                return false;
            }
        }
        return value.indexOf('.') >= 0 || value.indexOf(':') >= 0;
    }

    private static String normalize(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        return isPlausibleIp(ip) ? ip : "unknown";
    }

    private static boolean matchesCidr(String clientIp, String network, int prefix) {
        if (prefix < 0 || prefix > 32) {
            return false;
        }
        long client = ipv4ToLong(clientIp);
        long net = ipv4ToLong(network);
        if (client < 0 || net < 0) {
            return false;
        }
        long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        return (client & mask) == (net & mask);
    }

    private static long ipv4ToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return -1L;
        }
        long value = 0L;
        for (String part : parts) {
            int octet;
            try {
                octet = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return -1L;
            }
            if (octet < 0 || octet > 255) {
                return -1L;
            }
            value = (value << 8) | octet;
        }
        return value;
    }

    private static int parsePrefix(String prefix) {
        try {
            return Integer.parseInt(prefix.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
