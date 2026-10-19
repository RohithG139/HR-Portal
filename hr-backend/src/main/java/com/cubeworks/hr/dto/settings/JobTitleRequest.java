package com.cubeworks.hr.dto.settings;

import com.cubeworks.hr.employee.Grade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new job title (FRD-08 §6 C05).
 *
 * <p>Fields match the wireframe's {@code m-job-add} pop-up: name, grade and department
 * (grade/department added in V15, FRD-08 §12 D-01 reversed 2026-10-19).</p>
 *
 * @param name         the job title display name; required and must be non-blank
 * @param grade        the job title's grade; required
 * @param departmentId the job title's department; required and must exist
 */
public record JobTitleRequest(
        @NotBlank(message = "This field is required.")
        @Size(max = 100)
        String name,
        @NotNull(message = "This field is required.")
        Grade grade,
        @NotNull(message = "This field is required.")
        Long departmentId) {
}
