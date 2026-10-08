package za.ac.cput.unitrade.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * An expected error that services throw on purpose (wrong password, not your listing, ...).
 * GlobalExceptionHandler turns it into the standard JSON error body with the right status code.
 * The message is written for the end user, so it is safe to show in the UI.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, String> fieldErrors;

    private ApiException(HttpStatus status, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.fieldErrors = fieldErrors;
    }

    /** 400 for one invalid field, so the form can show the message under that field. */
    public static ApiException badRequest(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message, Map.of(field, message));
    }

    /** 400 that is not tied to a single field. */
    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message, null);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message, null);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message, null);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message, null);
    }

    /** 402: the payment gateway refused the payment. */
    public static ApiException paymentDeclined(String message) {
        return new ApiException(HttpStatus.PAYMENT_REQUIRED, message, null);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message, null);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
