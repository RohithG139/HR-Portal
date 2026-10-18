package com.cubeworks.hr.model.settings;

/**
 * Who an {@link AppraisalForm} is used for.
 *
 * <p>Stored as {@code varchar(12)} with a CHECK constraint in {@code hr.appraisal_form.used_for}.
 * {@code PROJECT} exists in the schema (FRD-08 v1.2's own cross-check confirms it) but has no
 * screen exposing it yet — spec FRD-08 §12 D-05, left {@code [OPEN]}.</p>
 */
public enum FormUsedFor {
    /** All employees. */
    STANDARD,
    /** Tech leads and managers. */
    LEADERSHIP,
    /** Probation reviews. */
    PROBATION,
    /** Project reviews — schema-only for now, see class doc. */
    PROJECT
}
