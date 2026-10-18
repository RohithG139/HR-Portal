package com.cubeworks.hr.dto.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating an appraisal form's header fields (FRD-08 §6 C17).
 *
 * <p>When {@code status} is changed to {@code ACTIVE}, the service enforces the
 * "Add at least one section with a question." rule (FRD-08 §5 S3/S4): the form must have
 * at least one {@code FormSection} and each section must have at least one
 * {@code FormQuestion}.</p>
 *
 * <p>The {@code version} field is used for optimistic-lock validation (R-DB-5). The
 * service compares it against the stored version and returns {@code 409 STALE_VERSION}
 * if they differ.</p>
 *
 * @param name               required; must be unique
 * @param usedFor            required
 * @param status             required; one of DRAFT / ACTIVE / ARCHIVED
 * @param overallScoreMethod required; one of WEIGHTED_AVERAGE / MANAGER_DECIDES
 * @param label1             rating label for score 1
 * @param label2             rating label for score 2
 * @param label3             rating label for score 3
 * @param label4             rating label for score 4
 * @param label5             rating label for score 5
 * @param version            the client's known version of this form (optimistic lock)
 */
public record AppraisalFormUpdateRequest(
        @NotBlank(message = "This field is required.")
        @Size(max = 100)
        String name,

        @NotNull(message = "This field is required.")
        String usedFor,

        @NotNull(message = "This field is required.")
        String status,

        @NotNull(message = "This field is required.")
        String overallScoreMethod,

        @Size(max = 60)
        String label1,
        @Size(max = 60)
        String label2,
        @Size(max = 60)
        String label3,
        @Size(max = 60)
        String label4,
        @Size(max = 60)
        String label5,

        @NotNull
        Integer version) {
}
