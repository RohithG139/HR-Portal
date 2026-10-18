package com.cubeworks.hr.common.error;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

/**
 * Domain exception that carries a typed error code and HTTP status, used throughout the
 * service layer to signal expected business errors to the caller.
 *
 * <p>Throw this — never a raw {@link RuntimeException} — so that
 * {@link GlobalExceptionHandler} can produce the correct RFC 7807-style error body
 * (R-API-3). The {@code errorCode} string becomes the {@code "code"} field in the JSON
 * response; callers switch on this code rather than parsing the message text.</p>
 *
 * <h2>Field-error format</h2>
 * <p>When the exception carries a non-null {@link #fieldErrors} list, the handler renders
 * {@code {"code":"VALIDATION_FAILED","errors":[...]}} instead of the single-message body.
 * Use {@link #withFieldErrors(List)} or the static factory {@link #fieldValidation(List)}
 * to build field-error exceptions in service code.</p>
 *
 * <h2>Extra response fields</h2>
 * <p>Subclasses can override {@link #getExtraFields()} to inject additional top-level keys
 * into the JSON body (e.g. {@code existingEmployeeId} for duplicate-email conflicts).
 * The handler merges these automatically.</p>
 *
 * <pre>{@code
 * // Example: resource not found
 * throw new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
 *         "Employee with id " + id + " does not exist");
 *
 * // Example: field-level validation error
 * throw ApiException.fieldValidation(List.of(
 *         Map.of("field", "readyBy", "message", "The ready-by date must be on or before the start date.")));
 *
 * // Example: global validation error (no field name)
 * throw ApiException.fieldValidation(List.of(
 *         Map.of("message", "Complete the equipment request before sending it to IT.")));
 * }</pre>
 */
public class ApiException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    /** Field-level errors for VALIDATION_FAILED responses; null when the single-message format is used. */
    private List<Map<String, String>> fieldErrors;

    /**
     * Creates an {@code ApiException}.
     *
     * @param errorCode a constant from {@link ErrorCodes} identifying the problem;
     *                  this string is returned to the caller in the {@code "code"} field
     * @param status    the HTTP status code to send; use {@link HttpStatus#NOT_FOUND} for 404,
     *                  {@link HttpStatus#CONFLICT} for 409, {@link HttpStatus#FORBIDDEN} for 403, etc.
     * @param message   a developer-readable description of the problem; returned as {@code "message"}
     *                  in 409/5xx bodies (R-API-3); not included in 403/404 responses
     */
    public ApiException(final String errorCode, final HttpStatus status, final String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    /**
     * Attaches a list of field-level errors to this exception and returns {@code this} for
     * fluent chaining.
     *
     * <p>When field errors are present the response body becomes
     * {@code {"code":"VALIDATION_FAILED","errors":[...]}} (R-API-3).</p>
     *
     * @param errors one entry per invalid field; each entry must have at least a {@code "message"}
     *               key and may optionally include a {@code "field"} key
     * @return this exception (mutated)
     */
    public ApiException withFieldErrors(final List<Map<String, String>> errors) {
        this.fieldErrors = errors;
        return this;
    }

    /**
     * Factory method that builds a 400 VALIDATION_FAILED exception carrying field-level errors.
     *
     * <p>Use this instead of {@code new ApiException(VALIDATION_FAILED, BAD_REQUEST, msg)} when
     * the response must be {@code {"code","errors":[...]}} rather than {@code {"code","message"}}.</p>
     *
     * @param errors list of field/message maps; each must have at least a {@code "message"} key
     * @return a new {@code ApiException} ready to throw
     */
    public static ApiException fieldValidation(final List<Map<String, String>> errors) {
        final ApiException ex = new ApiException(ErrorCodes.VALIDATION_FAILED,
                HttpStatus.BAD_REQUEST, "Validation failed");
        ex.fieldErrors = errors;
        return ex;
    }

    /**
     * Returns additional top-level keys to merge into the response body.
     *
     * <p>The base implementation returns an empty map. Subclasses override this to inject
     * extra fields (e.g. {@code existingEmployeeId} in a duplicate-email response).</p>
     *
     * @return extra key-value pairs, or an empty map if there are none
     */
    public Map<String, Object> getExtraFields() {
        return Map.of();
    }

    /**
     * Returns the typed error code used as the {@code "code"} field in the JSON response body.
     *
     * @return the error code string, guaranteed non-null
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the HTTP status that {@link GlobalExceptionHandler} will set on the response.
     *
     * @return the HTTP status, guaranteed non-null
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * Returns the attached field-error list, or {@code null} if this exception uses the
     * single-message format.
     *
     * @return field errors, or null
     */
    public List<Map<String, String>> getFieldErrors() {
        return fieldErrors;
    }
}
