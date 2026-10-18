package com.cubeworks.hr.settings;

import com.cubeworks.hr.common.BaseEntity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * A reusable onboarding or offboarding checklist template managed by HR (FRD-08, ADM-04).
 *
 * <p>Maps to {@code hr.checklist_template}. When an onboarding is created the relevant
 * template's items are instantiated as {@code checklist_item} rows — a snapshot is taken
 * so that subsequent edits or archiving of this template do not affect in-progress checklists
 * (ADM-04, data-model §5).</p>
 *
 * <p>A template may be optionally scoped to a specific {@link JobTitle}; when present,
 * the onboarding flow pre-selects it automatically. When null, HR selects the template
 * manually.</p>
 *
 * <p>Templates can be copied ({@link #copiedFrom}) to seed a new template from an existing
 * one without inheriting a live dependency.</p>
 *
 * <p>Archiving sets {@link #archivedAt}; archived templates cannot be assigned to new
 * onboardings but existing checklists built from them are unaffected (ADM-04).</p>
 */
@Entity
@Table(name = "checklist_template")
public class ChecklistTemplate extends BaseEntity {

    /** Human-readable template name. Must be unique. */
    @Column(name = "name", length = 100, nullable = false, unique = true)
    private String name;

    /**
     * Whether this template is for onboarding or offboarding checklists.
     * Determines which phase of an employee's lifecycle uses this template.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 12, nullable = false)
    private TemplateKind kind;

    /**
     * Optional job title scope. When set, this template is automatically offered
     * for employees hired into this job title.
     * Lazy: avoid N+1 when listing all templates — fetch only when the job-title detail is needed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_title_id")
    private JobTitle jobTitle;

    /**
     * The template this one was cloned from, or {@code null} if it was created from scratch.
     * Tracks provenance for audit purposes; does not create a runtime dependency.
     * Lazy: the source template is rarely needed alongside the copy.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "copied_from_id")
    private ChecklistTemplate copiedFrom;

    /**
     * Timestamp when this template was archived. A non-null value means the template
     * is archived and cannot be assigned to new onboardings (ADM-04).
     * Null means the template is active.
     */
    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    /** Required no-arg constructor for JPA. */
    public ChecklistTemplate() {}

    // ── Accessors ────────────────────────────────────────────────────────────

    public String getName() { return name; }
    public void setName(final String name) { this.name = name; }

    public TemplateKind getKind() { return kind; }
    public void setKind(final TemplateKind kind) { this.kind = kind; }

    public JobTitle getJobTitle() { return jobTitle; }
    public void setJobTitle(final JobTitle jobTitle) { this.jobTitle = jobTitle; }

    public ChecklistTemplate getCopiedFrom() { return copiedFrom; }
    public void setCopiedFrom(final ChecklistTemplate copiedFrom) { this.copiedFrom = copiedFrom; }

    public OffsetDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(final OffsetDateTime archivedAt) { this.archivedAt = archivedAt; }
}
