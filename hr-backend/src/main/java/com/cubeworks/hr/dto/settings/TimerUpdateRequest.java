package com.cubeworks.hr.dto.settings;

/**
 * Request body for updating a single timer/rule setting (FRD-08 §6 C30).
 *
 * <p>Only the writable fields are accepted; {@code code}, {@code category}, {@code module}
 * and {@code label} are never in the request body (ADM-01). {@code valueInt}/{@code valueText}/
 * {@code recipientRole}/{@code enabled} are optional — the service only updates the fields that
 * are non-null in the request. {@code version} is required (R-DB-5, spec §12 D-10) — the service
 * compares it against the stored version before saving and rejects a stale write with
 * {@code 409 STALE_VERSION}, the same pattern already used by the e-mail template and appraisal
 * form update endpoints.</p>
 *
 * <p>Validation rules from FRD-08 §5 S6:</p>
 * <ul>
 *   <li>{@code valueInt}: must be a whole number &gt; 0 for most settings. For
 *       {@code PASSWORD_MIN_LENGTH}, must be between 8 and 64.</li>
 *   <li>{@code valueText}: for {@code KPI_CYCLE} must be one of QUARTERLY/HALF_YEARLY/YEARLY.</li>
 * </ul>
 * <p>Code-specific validation is done in the service layer because the right rule
 * depends on the setting's {@code code}.</p>
 *
 * @param valueInt      updated numeric value; null means "do not change"
 * @param valueText     updated text value (KPI_CYCLE only); null means "do not change"
 * @param recipientRole updated recipient role; null means "do not change"
 * @param enabled       updated enabled flag; null means "do not change"
 * @param version       optimistic-lock version (R-DB-5, D-10) — required
 */
public record TimerUpdateRequest(
        Integer valueInt,
        String valueText,
        String recipientRole,
        Boolean enabled,
        Integer version) {
}
