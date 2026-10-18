package com.cubeworks.hr.dto.workspace;

/**
 * A single open task in the "Move open tasks" tick-list for the Transfer manager modal (W09).
 *
 * <p>Maps from one open {@code engine_task} row where {@code owner_employee_id} is the
 * employee's CURRENT manager. HR ticks the tasks they want to move to the new manager;
 * the ticked task IDs are sent in the {@code taskIdsToMove} array of the W10 request.</p>
 *
 * @param id       surrogate PK of the {@code engine_task} row (used in W10's {@code taskIdsToMove})
 * @param taskName the task name displayed in the tick-list
 * @param dueDate  ISO {@code yyyy-MM-dd} due date, or null if not set
 */
public record ManagerTaskItemDto(
        Long id,
        String taskName,
        String dueDate
) {}
