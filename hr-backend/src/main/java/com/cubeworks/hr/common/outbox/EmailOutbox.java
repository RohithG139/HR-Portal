package com.cubeworks.hr.common.outbox;

import com.cubeworks.hr.common.BaseEntity;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.settings.EmailTemplate;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * A queued outgoing e-mail in the transactional outbox.
 *
 * <p>Maps to {@code hr.email_outbox}. The outbox pattern (R-ARC-6) guarantees at-least-once
 * e-mail delivery: the service layer writes this row inside the business transaction, so
 * the e-mail is never lost even if the SMTP call fails. A background job picks up
 * {@link EmailOutboxStatus#PENDING} rows and delivers them via SMTP (Mailcatcher on port
 * 1025 in dev), incrementing {@link #attempts} on each try and transitioning to
 * {@link EmailOutboxStatus#SENT} or {@link EmailOutboxStatus#FAILED}.</p>
 *
 * <p>The {@link #subject} and {@link #body} fields hold the rendered e-mail text — a snapshot
 * of what was actually sent. This is intentional: the original template may be edited later
 * and the snapshot preserves the audit record (data-model §5).</p>
 *
 * <p>The {@link #templateCode} FK references {@link EmailTemplate#getCode()} rather than the
 * entity's surrogate {@code id} so that the outbox row remains legible even in a direct DB
 * query. The FK is to the {@code code} column which is also unique.</p>
 */
@Entity
@Table(name = "email_outbox")
public class EmailOutbox extends BaseEntity {

    /**
     * The template code used to generate this e-mail (e.g. {@code ONB_INVITE}).
     * Stored as a plain string FK to {@code email_template.code} for legibility.
     * Lazy: the full template object is rarely needed when processing the outbox.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_code", referencedColumnName = "code", nullable = false)
    private EmailTemplate emailTemplate;

    /** Resolved recipient e-mail address (after substituting employee data). */
    @Column(name = "to_address", length = 254, nullable = false)
    private String toAddress;

    /** Optional CC address(es) as a comma-separated list. */
    @Column(name = "cc", length = 1000)
    private String cc;

    /**
     * Rendered e-mail subject — snapshot of the template subject after variable substitution.
     * This snapshot is preserved for audit; the template subject may change later.
     */
    @Column(name = "subject", length = 200, nullable = false)
    private String subject;

    /**
     * Rendered e-mail body — snapshot of the rendered HTML/text.
     * Stored as {@code text} (unlimited length) to support HTML e-mails with tables.
     */
    @Column(name = "body", columnDefinition = "text", nullable = false)
    private String body;

    /** Current delivery status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private EmailOutboxStatus status = EmailOutboxStatus.PENDING;

    /**
     * Number of send attempts made so far. Zero until the background job first picks up
     * this row. The job increments this before each attempt and stops after a configured
     * maximum (e.g. 3), transitioning to {@link EmailOutboxStatus#FAILED} on exhaustion.
     */
    @Column(name = "attempts", nullable = false)
    private int attempts;

    /** Timestamp when the e-mail was successfully delivered to the SMTP relay. */
    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    /**
     * The employee contextually associated with this e-mail (e.g. the new hire for an
     * onboarding invitation). Used for audit queries: "show all e-mails sent about employee X".
     * Null for system-wide e-mails not tied to a specific employee.
     * Lazy: only needed in audit / admin screens.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    /** Required no-arg constructor for JPA. */
    protected EmailOutbox() {}

    // ── Accessors ────────────────────────────────────────────────────────────

    public EmailTemplate getEmailTemplate() { return emailTemplate; }
    public void setEmailTemplate(final EmailTemplate emailTemplate) { this.emailTemplate = emailTemplate; }

    public String getToAddress() { return toAddress; }
    public void setToAddress(final String toAddress) { this.toAddress = toAddress; }

    public String getCc() { return cc; }
    public void setCc(final String cc) { this.cc = cc; }

    public String getSubject() { return subject; }
    public void setSubject(final String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(final String body) { this.body = body; }

    public EmailOutboxStatus getStatus() { return status; }
    public void setStatus(final EmailOutboxStatus status) { this.status = status; }

    public int getAttempts() { return attempts; }
    public void setAttempts(final int attempts) { this.attempts = attempts; }

    public OffsetDateTime getSentAt() { return sentAt; }
    public void setSentAt(final OffsetDateTime sentAt) { this.sentAt = sentAt; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(final Employee employee) { this.employee = employee; }
}
