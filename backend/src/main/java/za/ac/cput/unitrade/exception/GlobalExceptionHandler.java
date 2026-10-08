package za.ac.cput.unitrade.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns every exception into the standard JSON error body, so the UI never receives a stack trace or an HTML page.
 * ResponseEntityExceptionHandler already covers Spring's own web errors (malformed JSON, wrong HTTP method,
 * unknown URL, ...); we only change the body it produces and add our own exception types.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Our own expected errors (see ApiException). */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getMessage(), request.getRequestURI(), ex.getFieldErrors());
    }

    /** A database rule was broken, e.g. two people registered the same email at the same moment. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "That action conflicts with existing data. Please refresh and try again.",
                request.getRequestURI(), null);
    }

    /**
     * Two requests changed the same row at the same moment (optimistic locking via the @Version column).
     * The loser gets a 409 instead of silently overwriting the winner, e.g. two buyers paying for one listing.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentChange(OptimisticLockingFailureException ex,
                                                                HttpServletRequest request) {
        log.warn("Concurrent change on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, "Someone else changed this at the same time. Please refresh and try again.",
                request.getRequestURI(), null);
    }

    /** Safety net: anything unexpected becomes a clean 500 (details go to the log, not to the user). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side. Please try again.",
                request.getRequestURI(), null);
    }

    /** Bean Validation failed on a request body: report each bad field. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return asObject(build(HttpStatus.BAD_REQUEST, "Please check the highlighted fields.", path(request), fieldErrors));
    }

    /** All other Spring web errors (400 malformed JSON, 404 unknown URL, 405, 415, ...) get the same body shape. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        return asObject(build(status, friendlyMessage(status), path(request), null));
    }

    private static String friendlyMessage(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "The request was not valid. Please check what you entered.";
            case NOT_FOUND -> "We could not find what you were looking for.";
            case METHOD_NOT_ALLOWED -> "That action is not supported here.";
            case UNSUPPORTED_MEDIA_TYPE -> "The request format is not supported.";
            default -> status.is5xxServerError()
                    ? "Something went wrong on our side. Please try again."
                    : "The request could not be completed.";
        };
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String message, String path,
                                                       Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message, path, fieldErrors));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ResponseEntity<Object> asObject(ResponseEntity<ErrorResponse> response) {
        return (ResponseEntity) response;
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest ? servletRequest.getRequest().getRequestURI() : "";
    }
}
