package com.cubeworks.hr.employee;

/**
 * Classification of a stored document.
 *
 * <p>Stored as {@code varchar(30)} with a CHECK constraint in {@code hr.document.doc_type}.
 * {@code POLICY} documents have a {@code NULL} {@code employee_id} because they are company-wide;
 * all other types are employee-specific (or temporarily unlinked during onboarding drafts).</p>
 */
public enum DocType {
    /** Signed employment contract PDF. */
    CONTRACT,
    /** Company policy document (no employee owner — employee_id is NULL). */
    POLICY,
    /** Employee acknowledgement of a policy (links to the POLICY document via checklist item). */
    POLICY_ACK,
    /** Resignation letter submitted by an employee. */
    RESIGNATION_LETTER,
    /** Attachment uploaded as evidence for a PIP. */
    PIP_ATTACHMENT,
    /** Attachment uploaded as part of a PIP dispute. */
    DISPUTE_ATTACHMENT,
    /** Attachment accompanying client feedback. */
    CLIENT_FEEDBACK_ATTACHMENT,
    /** Final document issued at the close of a process (e.g. end-of-PIP outcome letter). */
    FINAL_DOCUMENT,
    /** Any document that does not fit the above categories. */
    OTHER
}
