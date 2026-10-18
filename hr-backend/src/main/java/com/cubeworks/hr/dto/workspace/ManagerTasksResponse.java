package com.cubeworks.hr.dto.workspace;

import java.util.List;

/**
 * Response wrapper for W09 ({@code GET /api/employees/{id}/manager-tasks}).
 *
 * @param openManagerTasks all open {@code engine_task} rows for this employee whose
 *                         {@code owner_employee_id} is the employee's CURRENT manager,
 *                         ordered by due date ascending
 */
public record ManagerTasksResponse(
        List<ManagerTaskItemDto> openManagerTasks
) {}
