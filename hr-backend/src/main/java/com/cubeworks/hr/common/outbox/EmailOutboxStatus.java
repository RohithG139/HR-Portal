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
 *
 * <h2>{@link #HELD} (added 2026-10-21, onboarding e-mails triggered by the case engine)</h2>
 * <p>Some rows are inserted {@link #HELD} instead of {@link #PENDING} — still durably queued
 * inside the same business transaction as the employee record (R-ARC-6's guarantee is
 * unaffected), but deliberately excluded from {@link EmailOutboxSenderJob}'s pickup query until
 * something explicitly releases them to {@link #PENDING}. Used for "Welcome new employee":
 * the case engine's own "Release onboarding emails" process task calls back into hr-backend the
 * moment the employee's case actually starts, and that callback is what releases the row —
 * making the engine the genuine trigger for when the e-mail goes out, not just Spring's own
 * assumption that case-start will succeed. {@link com.cubeworks.hr.onboarding.OnboardingEmailReleaseScheduler}
 * is the fallback: if that callback never arrives, held rows past a timeout are released anyway
 * so the e-mail is never silently lost.</p>
 */
public enum EmailOutboxStatus {
    /** E-mail has been queued but not yet sent by the background job. */
    PENDING,
    /** E-mail was successfully delivered to the SMTP relay. */
    SENT,
    /** All send attempts failed; manual intervention required. */
    FAILED,
    /** Durably queued, but withheld from the sender job until explicitly released. */
    HELD
}
