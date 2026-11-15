package sm.core.security;

/**
 * Pedido com dados invalidos (HTTP 400).
 * Tratada pelo {@code GlobalExceptionHandler}.
 */
public class PedidoInvalidoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PedidoInvalidoException(String message) {
        super(message);
    }
}
