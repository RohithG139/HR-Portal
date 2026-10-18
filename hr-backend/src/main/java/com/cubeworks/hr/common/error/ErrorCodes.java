package com.cubeworks.hr.common.error;

/**
 * Typed error code constants used in API error responses (R-API-3).
 *
 * <p>Each constant is the value of the {@code "code"} field in the JSON error body.
 * Feature specs and client code switch on these strings to drive error handling.
 * Add a new constant here — with a Javadoc line — before first use in a service or handler.
 * Do <em>not</em> re-use or reinterpret existing codes for different meanings.</p>
 *
 * <h2>HTTP status mapping (R-API-3)</h2>
 * <pre>
 * 400 VALIDATION_FAILED        — one or more request fields failed Bean Validation
 * 403 FORBIDDEN                — authenticated but not authorized for this action
 * 404 NOT_FOUND                — the requested resource does not exist
 * 409 STALE_VERSION            — optimistic-lock conflict (R-DB-5): reload and retry
 * 409 DUPLICATE_PRIVATE_EMAIL  — private e-mail is already in use by another employee
 * 409 ASSET_NUMBER_IN_USE      — asset number already registered
 * 409 MANDATORY_ITEMS_OPEN     — cannot complete onboarding / setup: open mandatory items remain
 * 409 ALREADY_SENT             — IT request already sent; cannot send again
 * 409 UNDO_EXPIRED             — undo window has already closed
 * 409 ALREADY_LOGGED_IN        — cannot resend invite: employee has already logged in
 * </pre>
 */
public final class ErrorCodes {

    // ── 400 Bad Request ────────────────────────────────────────────────────────

    /**
     * One or more request fields failed Bean Validation.
     * The response body includes an {@code "errors"} array with per-field details.
     */
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    // ── 403 Forbidden ──────────────────────────────────────────────────────────

    /**
     * The authenticated user is not permitted to perform the requested action.
     * Returned when a role check (G-02) or self-action check (G-13) fails.
     */
    public static final String FORBIDDEN = "FORBIDDEN";

    // ── 404 Not Found ──────────────────────────────────────────────────────────

    /**
     * The requested resource does not exist (or is deliberately hidden from the caller).
     */
    public static final String NOT_FOUND = "NOT_FOUND";

    // ── 409 Conflict ───────────────────────────────────────────────────────────

    /**
     * Optimistic-lock conflict (R-DB-5): the supplied {@code version} is stale because
     * another request modified the resource between the caller's read and write.
     * The caller must reload the resource and retry.
     */
    public static final String STALE_VERSION = "STALE_VERSION";

    /**
     * Generic business rule conflict — the request is well-formed but violates a rule
     * that cannot be expressed as a field-level validation error.
     * Feature specs use this for state-machine violations (e.g. "cannot re-open a closed case").
     */
    public static final String CONFLICT = "CONFLICT";

    /**
     * A new employee was submitted with a {@code privateEmail} that is already in use by
     * another employee in the system (ONB-09).
     * The response body includes {@code "existingEmployeeId"} so the UI can offer a link.
     */
    public static final String DUPLICATE_PRIVATE_EMAIL = "DUPLICATE_PRIVATE_EMAIL";

    /**
     * The IT admin tried to register an asset with an {@code assetNumber} that already
     * exists in the asset register (S5).
     */
    public static final String ASSET_NUMBER_IN_USE = "ASSET_NUMBER_IN_USE";

    /**
     * HR tried to complete onboarding, or IT tried to complete setup, but at least one
     * mandatory checklist item is still open (ONB-05, S6/D-14).
     * Message: "Tick all mandatory items first." (S6) / triggered by A19 and A24.
     */
    public static final String MANDATORY_ITEMS_OPEN = "MANDATORY_ITEMS_OPEN";

    /**
     * HR tried to send the IT request again after it has already been sent (A14).
     */
    public static final String ALREADY_SENT = "ALREADY_SENT";

    /**
     * The user tried to undo a task completion after the undo window has already expired
     * (R-API-6, G-03). The task has been committed to the engine and cannot be reversed.
     */
    public static final String UNDO_EXPIRED = "UNDO_EXPIRED";

    /**
     * HR tried to resend the set-password e-mail to an employee who has already logged in
     * at least once (A34). The account is {@code ACTIVE}; resending would be confusing.
     */
    public static final String ALREADY_LOGGED_IN = "ALREADY_LOGGED_IN";

    /**
     * A resource with this name already exists in the system (FRD-08 Settings).
     * Returned when creating or renaming a department, job title, checklist template,
     * appraisal form or e-mail template whose name is already in use.
     * Message: "This name already exists." (exact per FRD-08 §5).
     */
    public static final String DUPLICATE_NAME = "DUPLICATE_NAME";

    /**
     * A resource that is still referenced by other rows cannot be removed.
     * Returned when attempting to delete a department or job title that still has employees
     * assigned (ADM-03), or a checklist template item that has been instantiated into
     * {@code checklist_item} rows (FRD-08 §12 D-02).
     * Message: "This item is in use and cannot be removed." (exact per FRD-08 §5 S1).
     */
    public static final String ITEM_IN_USE = "ITEM_IN_USE";

    private ErrorCodes() {
        // utility class — not instantiable
    }
}
