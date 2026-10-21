package com.cubeworks.hr.dto.workspace;

/**
 * A single row in the manager's My Team list (W05) and the Manager dashboard's team card (W02).
 *
 * <p>"Current projects" and "Allocation" come from {@code v_employee_allocation} (live
 * project assignments). The wireframe's "Development" column is still deferred to F05 — the
 * development plan has no goal/progress data to compute a percentage from yet.</p>
 *
 * @param employeeId   surrogate PK (used for the "View record" deep-link)
 * @param employeeCode the human-readable E-XXXX code
 * @param firstName    first name
 * @param lastName     last name
 * @param jobTitle     job title name (from {@code job_title.name})
 * @param stage        the employee's lifecycle stage code (e.g. {@code "ACTIVE"})
 * @param projects      current project names, comma-separated; null when on no project
 * @param allocationPct sum of current assignment allocations (0 when on no project)
 * @param teamStatus    My Team status: ONBOARDING, PIP or OFFBOARDING from the stage,
 *                      otherwise ON_PROJECT (allocation &gt; 0) or BENCH
 */
public record TeamMemberDto(
        Long employeeId,
        String employeeCode,
        String firstName,
        String lastName,
        String jobTitle,
        String stage,
        String projects,
        int allocationPct,
        String teamStatus
) {}
