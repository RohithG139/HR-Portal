package com.cubeworks.hr.dto.workspace;

import java.util.List;

/**
 * Dashboard DTO for active-stage Employee users (W03, FRD-01 Wave 3).
 *
 * <p>Only returned for employees whose {@code stage = 'ACTIVE'}. Employees whose
 * {@code stage = 'ONBOARDING'} are served by F01's A25 endpoint, which is unchanged by this
 * feature (spec §7 / W01 notes).</p>
 *
 * <p>Deferred fields ({@code currentProjects}, {@code developmentPlan}, {@code appraisal})
 * are absent and will be added additively by F04/F05/F06 (additive DTO contract, D-W2).</p>
 *
 * @param equipment list of assets currently issued to the caller ({@code status = 'ISSUED'})
 * @param tasks     the employee's own open tasks (same shape as A36/S12 My Tasks)
 */
public record EmployeeDashboardDto(
        List<EquipmentDto> equipment,
        List<DashboardTaskDto> tasks
) {}
