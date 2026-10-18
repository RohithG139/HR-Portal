package com.cubeworks.hr.employee;

/**
 * Lifecycle stage of an employee case — a cached copy of the CaseFabric engine's current stage.
 *
 * <p>Stored as {@code varchar(12)} with a CHECK constraint in {@code hr.employee.stage}.
 * This value is a <em>cache</em>: the engine decides transitions; Spring Boot writes the
 * result back here after a stage change so that the employee list does not require an engine
 * round-trip on every page load (R-ARC-1, R-ARC-2, data-model §5 deliberate copies).</p>
 *
 * <p>Note: {@code DRAFT} was removed in the F01 decisions — drafts live in
 * {@code onboarding_draft} until "Save &amp; continue" creates the employee row.</p>
 */
public enum EmployeeStage {
    /** Employee is going through onboarding; case is in the Onboarding stage. */
    ONBOARDING,
    /** Employee is fully onboarded and active. */
    ACTIVE,
    /** Employee is on a Performance Improvement Plan. */
    PIP,
    /** Employee is in the offboarding process. */
    OFFBOARDING,
    /** Case has been closed and archived (G-10); all UI screens are read-only. */
    ARCHIVED
}
