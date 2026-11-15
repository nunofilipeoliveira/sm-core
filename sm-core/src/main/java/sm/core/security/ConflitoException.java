package sm.core.security;

/**
 * Pedido em conflito com o estado atual (HTTP 409), ex.: username ja existe ou jogador ja tem conta.
 * Tratada pelo {@code GlobalExceptionHandler}.
 */
public class ConflitoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConflitoException(String message) {
        super(message);
    }
}
