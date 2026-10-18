package com.cubeworks.hr.model.settings;

import com.cubeworks.hr.common.BaseEntity;
import jakarta.persistence.*;

/**
 * A named section of an {@link AppraisalForm}, grouping one or more {@link FormQuestion}s and
 * declaring who completes it (FRD-08 §4.4).
 *
 * <p>Maps to {@code hr.form_section}. At least one of {@link #bySelf}, {@link #byPeer},
 * {@link #byManager}, {@link #byHr} must be {@code true} (DB CHECK) — a section with nobody
 * assigned to complete it could never be answered.</p>
 *
 * <p>The parent {@link AppraisalForm} uses {@code ON DELETE CASCADE}, so deleting a form
 * removes its sections (and, transitively, their questions) automatically.</p>
 */
@Entity
@Table(name = "form_section")
public class FormSection extends BaseEntity {

    /**
     * The form that owns this section.
     * Lazy: listing sections never requires materialising the parent form object.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_id", nullable = false)
    private AppraisalForm form;

    /** Section name, e.g. "Results & KPIs". */
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    /** Whether the employee completes this section about themselves. */
    @Column(name = "by_self", nullable = false)
    private boolean bySelf;

    /** Whether a chosen peer completes this section. */
    @Column(name = "by_peer", nullable = false)
    private boolean byPeer;

    /** Whether the employee's manager completes this section. */
    @Column(name = "by_manager", nullable = false)
    private boolean byManager;

    /** Whether HR completes this section. */
    @Column(name = "by_hr", nullable = false)
    private boolean byHr;

    /** Display order within the form. Lower values appear first. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Required no-arg constructor for JPA. */
    public FormSection() {}

    // ── Accessors ────────────────────────────────────────────────────────────

    public AppraisalForm getForm() { return form; }
    public void setForm(final AppraisalForm form) { this.form = form; }

    public String getName() { return name; }
    public void setName(final String name) { this.name = name; }

    public boolean isBySelf() { return bySelf; }
    public void setBySelf(final boolean bySelf) { this.bySelf = bySelf; }

    public boolean isByPeer() { return byPeer; }
    public void setByPeer(final boolean byPeer) { this.byPeer = byPeer; }

    public boolean isByManager() { return byManager; }
    public void setByManager(final boolean byManager) { this.byManager = byManager; }

    public boolean isByHr() { return byHr; }
    public void setByHr(final boolean byHr) { this.byHr = byHr; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(final int sortOrder) { this.sortOrder = sortOrder; }
}
