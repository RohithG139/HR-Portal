package com.cubeworks.hr.model.workspace;

/**
 * Lifecycle status of a {@link ManagerChange}.
 *
 * <p>Stored as {@code varchar(10)} with a CHECK constraint in {@code hr.manager_change.status}.
 * {@link #SCHEDULED} rows with {@code effective_date <= today} are applied by a scheduler
 * (same pattern as {@code OverdueReminderScheduler}/the undo-window executor), moving them to
 * {@link #APPLIED}.</p>
 */
public enum ManagerChangeStatus {
    SCHEDULED,
    APPLIED,
    CANCELLED
}
