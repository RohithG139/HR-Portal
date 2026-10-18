package com.cubeworks.hr.onboarding;

/**
 * Which lifecycle phase a {@link ChecklistItem} belongs to.
 *
 * <p>Stored as {@code varchar(12)} with a CHECK constraint in
 * {@code hr.checklist_item.phase}. A single {@code checklist_item} table holds both
 * onboarding and offboarding items; the phase column partitions them.</p>
 */
public enum ChecklistPhase {
    /** Item was created as part of the employee's onboarding checklist. */
    ONBOARDING,
    /** Item was created as part of the employee's offboarding checklist. */
    OFFBOARDING
}
