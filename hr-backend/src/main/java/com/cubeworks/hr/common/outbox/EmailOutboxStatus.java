package com.cubeworks.hr.common.outbox;

/**
 * Delivery status of an outgoing e-mail in the transactional outbox.
 *
 * <p>Stored as {@code varchar(10)} with a CHECK constraint in
 * {@code hr.email_outbox.status}. The outbox pattern (R-ARC-6) ensures that e-mails are
 * written to this table inside the business transaction and sent by a background job:</p>
 * <ul>
 *   <li>Rows are inserted with {@link #PENDING} by the service layer inside the
 *       business transaction — atomically with the domain change.</li>
 *   <li>The background job picks up {@link #PENDING} rows, sends them via SMTP (Mailcatcher
 *       in dev, port 1025), and transitions to {@link #SENT}.</li>
 *   <li>If sending fails after all retries, the row transitions to {@link #FAILED} and an
 *       alert is raised.</li>
 * </ul>
 */
public enum EmailOutboxStatus {
    /** E-mail has been queued but not yet sent by the background job. */
    PENDING,
    /** E-mail was successfully delivered to the SMTP relay. */
    SENT,
    /** All send attempts failed; manual intervention required. */
    FAILED
}
