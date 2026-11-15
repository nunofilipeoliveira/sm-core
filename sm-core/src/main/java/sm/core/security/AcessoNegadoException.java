package sm.core.security;

/**
 * Recusa por falta de permissao sobre o recurso (HTTP 403, D5).
 * Tratada pelo {@code GlobalExceptionHandler}.
 */
public class AcessoNegadoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AcessoNegadoException() {
        super("Sem permissão para este recurso.");
    }

    public AcessoNegadoException(String message) {
        super(message);
    }
}
