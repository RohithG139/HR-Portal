package com.cubeworks.hr.dto.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new job title (FRD-08 §6 C05).
 *
 * <p>Only the {@code name} field is accepted. Grade and department fields shown in the
 * wireframe's {@code m-job-add} modal are not built (FRD-08 §12 D-01: the data model
 * has no such columns on {@code job_title}).</p>
 *
 * @param name the job title display name; required and must be non-blank
 */
public record JobTitleRequest(
        @NotBlank(message = "This field is required.")
        @Size(max = 100)
        String name) {
}
