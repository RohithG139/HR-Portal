package com.cubeworks.hr.common.error;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central exception handler that translates domain exceptions into the API error contract
 * (R-API-3). All {@code @RestController} methods benefit automatically.
 *
 * <h2>Error format contract (R-API-3)</h2>
 * <pre>
 * 400 {"code":"VALIDATION_FAILED","errors":[{"field":"…","message":"…"}]}
 * 403 {"code":"FORBIDDEN"}
 * 404 {"code":"NOT_FOUND"}
 * 409 {"code":"&lt;SPECIFIC_CODE&gt;","message":"…"}
 * 500 {"code":"INTERNAL_ERROR","message":"An unexpected error occurred"}
 * </pre>
 *
 * <p>The {@code "message"} field in 5xx responses is intentionally generic: internal error
 * details must never leak to callers. Full stack traces are logged at ERROR level.</p>
 *
 * <p>New feature-specific exceptions should extend {@link ApiException}; they will be caught
 * by {@link #handleApiException(ApiException)} automatically.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles Bean Validation failures raised by {@code @Valid} on controller parameters.
     *
     * <p>Each {@link FieldError} in the binding result becomes one entry in the
     * {@code "errors"} array so the client can highlight the exact fields.</p>
     *
     * @param ex the exception thrown by Spring MVC when {@code @Valid} fails
     * @return 400 with the field-level error list
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            final MethodArgumentNotValidException ex) {

        final List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldErrorMap)
                .toList();

        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "code", ErrorCodes.VALIDATION_FAILED,
                        "errors", errors));
    }

    /**
     * Handles constraint violations raised by {@code @Validated} on service or controller method
     * parameters (path variables, query params, etc.).
     *
     * @param ex the constraint violation exception
     * @return 400 with the field-level error list
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            final ConstraintViolationException ex) {

        // PropertyPath includes the method name prefix; strip it so the client sees "fieldName"
        final List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> Map.of(
                        "field", v.getPropertyPath().toString(),
                        "message", v.getMessage()))
                .toList();

        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "code", ErrorCodes.VALIDATION_FAILED,
                        "errors", errors));
    }

    /**
     * Handles domain exceptions thrown by service methods.
     *
     * <p>The HTTP status is determined by {@link ApiException#getStatus()}, allowing services to
     * signal 403, 404, 409, etc. without hard-coding HTTP concerns in the domain layer.</p>
     *
     * <p>Three response shapes (R-API-3):</p>
     * <ul>
     *   <li>403/404 — {@code {"code":"..."}} with no message</li>
     *   <li>400 with field errors — {@code {"code":"VALIDATION_FAILED","errors":[...]}}</li>
     *   <li>409 / other — {@code {"code":"...","message":"..."}} plus any extra fields from
     *       {@link ApiException#getExtraFields()}</li>
     * </ul>
     *
     * @param ex the domain exception
     * @return a response whose status and body match the exception's code and status
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(final ApiException ex) {
        log.debug("ApiException [code={}, status={}]: {}", ex.getErrorCode(), ex.getStatus(), ex.getMessage());

        final Map<String, Object> body;

        if (ex.getFieldErrors() != null) {
            // Field-level validation error: {"code":"VALIDATION_FAILED","errors":[...]}
            final Map<String, Object> m = new HashMap<>();
            m.put("code", ex.getErrorCode());
            m.put("errors", ex.getFieldErrors());
            m.putAll(ex.getExtraFields());
            body = m;
        } else if (ex.getStatus() == HttpStatus.FORBIDDEN || ex.getStatus() == HttpStatus.NOT_FOUND) {
            // 403 and 404 carry no "message" field (R-API-3)
            body = Map.of("code", ex.getErrorCode());
        } else {
            // 409 and others include a "message" field plus any subclass-provided extras
            final Map<String, Object> m = new HashMap<>();
            m.put("code", ex.getErrorCode());
            m.put("message", ex.getMessage());
            m.putAll(ex.getExtraFields());
            body = m;
        }

        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    /**
     * Handles Spring Security access-denied exceptions thrown when {@code @PreAuthorize}
     * denies access.
     *
     * @param ex the access denied exception
     * @return 403 FORBIDDEN
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(final AccessDeniedException ex) {
        log.debug("AccessDeniedException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", ErrorCodes.FORBIDDEN));
    }

    /**
     * Handles JPA optimistic-lock failures raised when an entity's version is stale (R-DB-5).
     *
     * <p>This catches both {@link OptimisticLockingFailureException} (Spring) and
     * {@link ObjectOptimisticLockingFailureException} (Hibernate-specific subclass).</p>
     *
     * @param ex the optimistic-lock failure
     * @return 409 STALE_VERSION
     */
    @ExceptionHandler({OptimisticLockingFailureException.class,
            ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(
            final RuntimeException ex) {
        log.debug("Optimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", ErrorCodes.STALE_VERSION,
                        "message", "Version conflict — reload and retry"));
    }

    /**
     * Catch-all for unexpected exceptions.
     *
     * <p>Logs the full stack trace at ERROR level so that operators can diagnose the root cause.
     * Returns a deliberately vague 500 body so that internal implementation details are never
     * exposed to callers.</p>
     *
     * @param ex the unexpected exception
     * @return 500 with a generic error message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(final Exception ex) {
        log.error("Unhandled exception reached GlobalExceptionHandler", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "code", "INTERNAL_ERROR",
                        "message", "An unexpected error occurred"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Converts a Spring MVC {@link FieldError} to the JSON object shape the API contract requires.
     *
     * @param fe the field error from the binding result
     * @return a two-entry map with "field" and "message" keys
     */
    private Map<String, String> toFieldErrorMap(final FieldError fe) {
        return Map.of(
                "field", fe.getField(),
                "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value");
    }
}
