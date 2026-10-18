package com.cubeworks.hr.model.settings;

/**
 * Lifecycle status of an {@link AppraisalForm}.
 *
 * <p>Stored as {@code varchar(10)} with a CHECK constraint in {@code hr.appraisal_form.status}.
 * A form cannot be set to {@link #ACTIVE} unless it has at least one section with at least one
 * question (FRD-08 spec §5 S3/S4, "Add at least one section with a question."). {@link #ARCHIVED}
 * forms stop appearing in pickers for new reviews but stay on any review that already references
 * them (ADM-04).</p>
 */
public enum FormStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED
}
