package com.cubeworks.hr.dto.workspace;

/**
 * A single task item as shown on role-specific dashboards (W01–W04, FRD-01 Wave 3).
 *
 * <p>Mapped from {@link com.cubeworks.hr.task.TaskService.TaskItemDto}; same data shape
 * reused rather than duplicated — the dashboard task list is a subset/alias of the My Tasks
 * list (A36/S12), intentionally sharing the same backend query (spec §5 W1–W4
 * "My tasks (list) — reuses F01's GET /api/tasks (A36)").</p>
 *
 * @param taskId     surrogate PK of the task row
 * @param title      task name or checklist item name
 * @param employeeId employee ID this task belongs to (for deep-link navigation)
 * @param dueDate    ISO {@code yyyy-MM-dd} due date string (null = no deadline)
 * @param dueState   {@code "OVERDUE"}, {@code "DUE_SOON"} (within 3 days), or {@code "UPCOMING"}
 */
public record DashboardTaskDto(
        Long taskId,
        String title,
        Long employeeId,
        String dueDate,
        String dueState,
        // "First Last" of the employee the task is about, for "Task · Name" rows; may be null
        String employeeName
) {}
