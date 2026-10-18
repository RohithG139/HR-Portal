package com.cubeworks.hr.model.workspace;

/**
 * Whether a {@link ManagerChange} is a line-manager transfer or a project-manager change.
 *
 * <p>Stored as {@code varchar(8)} with a CHECK constraint in {@code hr.manager_change.scope}.
 * FRD-01 (Wave 3) only ever creates {@link #LINE} rows (Transfer manager, W10); {@link #PROJECT}
 * exists in the schema for a future feature (changing a project's PM) and is not written by
 * anything in this feature.</p>
 */
public enum ManagerChangeScope {
    /** A line-manager transfer — {@code employee_id} is set, {@code project_id} is null. */
    LINE,
    /** A project-manager change — {@code project_id} is set, {@code employee_id} is null. */
    PROJECT
}
