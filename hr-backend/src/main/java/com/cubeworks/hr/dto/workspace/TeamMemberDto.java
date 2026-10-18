package com.cubeworks.hr.dto.workspace;

/**
 * A single row in the manager's My Team list (W05) and the Manager dashboard's team card (W02).
 *
 * <p>The "Current projects", "Allocation" and "Development" columns in the wireframe ({@code
 * p-mg-team}) are deferred to F04/F05 — those fields are absent from this record and will be
 * added additively when the owning features ship (additive DTO contract, spec §7 D-W2). The
 * frontend renders placeholder text for the missing columns, same pattern as F01's Overview
 * tab placeholder cards.</p>
 *
 * @param employeeId   surrogate PK (used for the "View record" deep-link)
 * @param employeeCode the human-readable E-XXXX code
 * @param firstName    first name
 * @param lastName     last name
 * @param jobTitle     job title name (from {@code job_title.name})
 * @param stage        the employee's lifecycle stage code (e.g. {@code "ACTIVE"})
 */
public record TeamMemberDto(
        Long employeeId,
        String employeeCode,
        String firstName,
        String lastName,
        String jobTitle,
        String stage
) {}
