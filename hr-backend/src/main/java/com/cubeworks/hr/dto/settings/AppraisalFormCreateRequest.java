package com.cubeworks.hr.dto.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new appraisal form (FRD-08 §6 C15).
 *
 * <p>The new form is always created with {@code status = DRAFT}; name and usedFor are
 * the only fields accepted at creation time. Rating labels, sections and questions are
 * added afterward via C17–C23.</p>
 *
 * @param name    required; must be unique
 * @param usedFor required; one of STANDARD / LEADERSHIP / PROBATION
 */
public record AppraisalFormCreateRequest(
        @NotBlank(message = "This field is required.")
        @Size(max = 100)
        String name,

        @NotNull(message = "This field is required.")
        String usedFor) {
}
