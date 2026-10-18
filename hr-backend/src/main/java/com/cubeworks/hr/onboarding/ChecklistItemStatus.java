package com.cubeworks.hr.onboarding;

/**
 * Completion status of a {@link ChecklistItem}.
 *
 * <p>Stored as {@code varchar(10)} with a CHECK constraint in
 * {@code hr.checklist_item.status}. A DB CHECK enforces that
 * {@code (status = 'DONE') = (done_at IS NOT NULL AND done_by IS NOT NULL)}.</p>
 */
public enum ChecklistItemStatus {
    /** Item has not yet been completed. */
    OPEN,
    /** Item has been completed. */
    DONE,
    /** Item was cancelled (e.g. not applicable for this employee). */
    CANCELLED
}
