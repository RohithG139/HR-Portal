package com.cubeworks.hr.dto.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating an e-mail template (FRD-08 §6 C27).
 *
 * <p>The {@code code} field is never accepted in the body (ADM-01); it is taken from the
 * URL path instead. Validation rules from FRD-08 §5 S5:</p>
 * <ul>
 *   <li>Name, trigger description, subject and body are required.</li>
 *   <li>At least one recipient flag must be {@code true} — checked in the service layer.</li>
 *   <li>CC must be empty or contain only valid, comma-separated e-mail addresses.</li>
 * </ul>
 *
 * @param name               human-readable name; required
 * @param triggerDescription trigger event description; required
 * @param subject            e-mail subject; required
 * @param body               e-mail body; required
 * @param cc                 optional CC addresses; validated if present
 * @param toEmployee         recipient flag
 * @param toManager          recipient flag
 * @param toHr               recipient flag
 * @param toItAdmin          recipient flag
 * @param toTaskOwner        recipient flag
 * @param toPeer             recipient flag
 * @param active             whether the template should be active
 * @param version            optimistic-lock version (R-DB-5)
 */
public record EmailTemplateUpdateRequest(
        @NotBlank(message = "This field is required.")
        @Size(max = 100)
        String name,

        @NotBlank(message = "This field is required.")
        @Size(max = 200)
        String triggerDescription,

        @NotBlank(message = "This field is required.")
        @Size(max = 200)
        String subject,

        @NotBlank(message = "This field is required.")
        String body,

        @Size(max = 1000)
        String cc,

        boolean toEmployee,
        boolean toManager,
        boolean toHr,
        boolean toItAdmin,
        boolean toTaskOwner,
        boolean toPeer,
        boolean active,

        @NotNull
        Integer version) {
}
