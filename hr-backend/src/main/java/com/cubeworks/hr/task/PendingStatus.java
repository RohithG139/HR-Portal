package com.cubeworks.hr.task;

/**
 * State of an engine task's pending-completion (undo-window) record.
 *
 * <p>Stored as {@code varchar(10)} with a CHECK constraint in
 * {@code hr.engine_task.pending_status}. The undo window (G-03, R-API-6) works as follows:</p>
 * <ol>
 *   <li>When a user completes a task the backend sets {@code pending_status = PENDING} and
 *       returns {@code 202 {undoUntil}} — the engine task is NOT yet completed at the engine.</li>
 *   <li>A scheduler picks up {@code PENDING} rows whose {@code pending_execute_at} has passed
 *       and calls the engine to complete the task, then sets {@code EXECUTED}.</li>
 *   <li>If the user undoes within the window the row transitions to {@code UNDONE} and the
 *       engine call is skipped.</li>
 *   <li>{@code FAILED} marks a row where the engine call was attempted but threw an exception;
 *       it will be retried.</li>
 * </ol>
 */
public enum PendingStatus {
    /** Waiting for the undo window to expire before the engine call is made. */
    PENDING,
    /** User undid the completion within the undo window; engine call will be skipped. */
    UNDONE,
    /** Engine task has been successfully completed at the engine side. */
    EXECUTED,
    /** Engine call failed; will be retried by the scheduler (R-ARC-5). */
    FAILED
}
