package com.cubeworks.hr.dto.settings;

/**
 * Response body for one row in the e-mail template list (FRD-08 §6 C25).
 *
 * <p>The {@code code} field is the stable, machine-readable identifier used as the URL
 * path key in C26–C28 (ADM-01: the code is never editable).</p>
 *
 * @param code               machine-readable template code (e.g. "WELCOME_NEW_EMPLOYEE")
 * @param name               human-readable template name
 * @param triggerDescription what event causes this template to be sent
 * @param recipientsSummary  comma-separated list of enabled recipient roles for display
 * @param active             whether this template is currently active
 */
public record EmailTemplateListDto(
        String code,
        String name,
        String triggerDescription,
        String recipientsSummary,
        boolean active) {
}
