package com.cubeworks.hr.dto.probation;

/**
 * {@code 202} body of P03/P04 (R-API-6, G-03).
 *
 * @param undoUntil ISO-8601 UTC instant the undo window closes
 * @param taskId    engine_task id to pass to {@code POST /api/tasks/{taskId}/undo} (A37)
 */
public record UndoWindowResponse(String undoUntil, Long taskId) {
}
