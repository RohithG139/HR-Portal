package com.cubeworks.hr.dto.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new department (FRD-08 §6 C02).
 *
 * <p>Only the {@code name} field is accepted — HR-managed reference data that must be
 * unique across all departments (FRD-08 §5 S1: "This name already exists.").</p>
 *
 * @param name the department display name; required and must be non-blank
 */
public record DepartmentRequest(
        @NotBlank(message = "This field is required.")
        @Size(max = 100)
        String name) {
}
