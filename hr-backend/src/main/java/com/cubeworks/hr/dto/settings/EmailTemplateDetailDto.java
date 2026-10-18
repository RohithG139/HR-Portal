package com.cubeworks.hr.dto.settings;

/**
 * Full-detail response for a single e-mail template (FRD-08 §6 C26).
 *
 * <p>The {@code code} field is never editable (ADM-01) but is returned so the client can
 * use it as the path key for subsequent C27/C28 requests. The {@code version} is returned
 * so the client can supply it in C27 for optimistic-lock validation (R-DB-5).</p>
 *
 * @param code               machine-readable code; never editable
 * @param name               human-readable name
 * @param triggerDescription trigger event description
 * @param subject            the e-mail subject line (may contain placeholders)
 * @param body               the e-mail body (may contain placeholders)
 * @param cc                 optional CC addresses, comma-separated
 * @param toEmployee         recipient flag
 * @param toManager          recipient flag
 * @param toHr               recipient flag
 * @param toItAdmin          recipient flag
 * @param toTaskOwner        recipient flag
 * @param toPeer             recipient flag
 * @param active             whether the template is currently active
 * @param version            optimistic-lock version for C27
 */
public record EmailTemplateDetailDto(
        String code,
        String name,
        String triggerDescription,
        String subject,
        String body,
        String cc,
        boolean toEmployee,
        boolean toManager,
        boolean toHr,
        boolean toItAdmin,
        boolean toTaskOwner,
        boolean toPeer,
        boolean active,
        Integer version) {
}
