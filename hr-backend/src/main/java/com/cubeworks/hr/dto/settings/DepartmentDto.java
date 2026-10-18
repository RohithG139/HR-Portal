package com.cubeworks.hr.dto.settings;

/**
 * Response body for a single department entry (FRD-08 §6 C01).
 *
 * <p>The {@code employeeCount} field is the number of active employees currently
 * assigned to this department, shown as the "People" column in the Settings UI (S1).</p>
 *
 * @param id            the department's surrogate PK
 * @param name          the department's display name
 * @param employeeCount count of employees assigned to this department
 */
public record DepartmentDto(
        Long id,
        String name,
        long employeeCount) {
}
