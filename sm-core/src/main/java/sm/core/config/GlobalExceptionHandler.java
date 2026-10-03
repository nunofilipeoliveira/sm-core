package sm.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Respostas controladas para situacoes tipicas de abuso (evita 500/stacktrace
 * e devolve codigos HTTP que o cliente entende).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Upload maior que o limite configurado em multipart. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("GlobalExceptionHandler | upload rejeitado por exceder o tamanho maximo: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .header("Retry-After", "1")
                .body("{\"error\":\"payload_too_large\",\"message\":"
                        + "\"Ficheiro maior que o limite permitido.\"}");
    }
}
