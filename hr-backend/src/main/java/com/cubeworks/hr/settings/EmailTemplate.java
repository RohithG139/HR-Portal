package com.cubeworks.hr.settings;

import com.cubeworks.hr.common.BaseEntity;
import jakarta.persistence.*;

/**
 * An e-mail template used by the outbox sender to generate rendered e-mails.
 *
 * <p>Maps to {@code hr.email_template}. There are 21 FRD-defined templates, each with
 * a unique {@code code} (e.g. {@code ONB_INVITE}, {@code PIP_NOTICE}). The code is used
 * as a foreign key in {@code hr.email_outbox} to link a queued e-mail to its template.</p>
 *
 * <p>The recipient flags ({@code to_employee}, {@code to_manager}, etc.) are stored as
 * booleans to avoid the separate recipient table from v1 (data-model §4). A DB CHECK
 * enforces that at least one recipient flag is {@code true}.</p>
 */
@Entity
@Table(name = "email_template")
public class EmailTemplate extends BaseEntity {

    /**
     * Machine-readable template code, e.g. {@code ONB_INVITE}.
     * Used as the foreign key in {@code email_outbox.template_code}.
     */
    @Column(name = "code", length = 40, nullable = false, unique = true)
    private String code;

    /** Human-readable template name shown in the Settings UI. */
    @Column(name = "name", length = 100, nullable = false, unique = true)
    private String name;

    /** Description of what event triggers this e-mail (shown in the Settings UI). */
    @Column(name = "trigger_description", length = 200, nullable = false)
    private String triggerDescription;

    /** E-mail subject line; may contain template placeholders. */
    @Column(name = "subject", length = 200, nullable = false)
    private String subject;

    /** E-mail body; may contain template placeholders. */
    @Column(name = "body", columnDefinition = "text", nullable = false)
    private String body;

    /** Optional static CC address(es) as a comma-separated list. */
    @Column(name = "cc", length = 1000)
    private String cc;

    /** Whether the employee who is the subject of this event receives the e-mail. */
    @Column(name = "to_employee", nullable = false)
    private boolean toEmployee;

    /** Whether the employee's line manager receives the e-mail. */
    @Column(name = "to_manager", nullable = false)
    private boolean toManager;

    /** Whether an HR administrator receives the e-mail. */
    @Column(name = "to_hr", nullable = false)
    private boolean toHr;

    /** Whether the IT administrator receives the e-mail. */
    @Column(name = "to_it_admin", nullable = false)
    private boolean toItAdmin;

    /** Whether the owner of the relevant task receives the e-mail. */
    @Column(name = "to_task_owner", nullable = false)
    private boolean toTaskOwner;

    /**
     * Whether a peer reviewer receives the e-mail (used for peer nomination notifications).
     */
    @Column(name = "to_peer", nullable = false)
    private boolean toPeer;

    /**
     * Whether this template is currently active. Inactive templates are not used by the
     * outbox sender, but their history is preserved.
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** Required no-arg constructor for JPA. */
    protected EmailTemplate() {}

    // ── Accessors ────────────────────────────────────────────────────────────

    public String getCode() { return code; }
    public void setCode(final String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(final String name) { this.name = name; }

    public String getTriggerDescription() { return triggerDescription; }
    public void setTriggerDescription(final String triggerDescription) { this.triggerDescription = triggerDescription; }

    public String getSubject() { return subject; }
    public void setSubject(final String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(final String body) { this.body = body; }

    public String getCc() { return cc; }
    public void setCc(final String cc) { this.cc = cc; }

    public boolean isToEmployee() { return toEmployee; }
    public void setToEmployee(final boolean toEmployee) { this.toEmployee = toEmployee; }

    public boolean isToManager() { return toManager; }
    public void setToManager(final boolean toManager) { this.toManager = toManager; }

    public boolean isToHr() { return toHr; }
    public void setToHr(final boolean toHr) { this.toHr = toHr; }

    public boolean isToItAdmin() { return toItAdmin; }
    public void setToItAdmin(final boolean toItAdmin) { this.toItAdmin = toItAdmin; }

    public boolean isToTaskOwner() { return toTaskOwner; }
    public void setToTaskOwner(final boolean toTaskOwner) { this.toTaskOwner = toTaskOwner; }

    public boolean isToPeer() { return toPeer; }
    public void setToPeer(final boolean toPeer) { this.toPeer = toPeer; }

    public boolean isActive() { return active; }
    public void setActive(final boolean active) { this.active = active; }
}
