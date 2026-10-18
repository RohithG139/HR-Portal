package com.cubeworks.hr.model.workspace;

import com.cubeworks.hr.common.BaseEntity;
import com.cubeworks.hr.employee.Employee;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A scheduled or applied manager change — a line-manager transfer ({@link ManagerChangeScope#LINE})
 * or a project-manager change ({@link ManagerChangeScope#PROJECT}) (FRD-01 §6/§7/§9, Wave 3).
 *
 * <p>Maps to {@code hr.manager_change}. FRD-01's Transfer manager flow (W10) only ever creates
 * {@code LINE} rows: {@link #employee} is the employee being transferred, {@link #projectId} and
 * {@link #parent} stay null. {@code PROJECT} rows and the {@link #parent} self-reference ("LINE
 * rows created by a PROJECT change, one per ticked member") belong to a future feature that
 * changes a project's PM — {@code project_id} is a plain column, not a JPA relationship, since no
 * {@code Project} entity exists yet (F04's concern, not this feature's).</p>
 *
 * <p>{@link #moveOpenTasks} is a legacy all-or-nothing flag kept for schema compatibility; the
 * real per-task selection this feature actually uses is {@link ManagerChangeTask}, one row per
 * ticked {@code engine_task} (spec §12 D-W3).</p>
 *
 * <p>A {@link ManagerChangeStatus#SCHEDULED} row with {@code effectiveDate <= today} is applied
 * by a scheduler, which updates the employee's {@link Employee#setManager(Employee)} directly
 * and every linked {@code engine_task.owner_employee_id}, then sets this row to
 * {@link ManagerChangeStatus#APPLIED}.</p>
 */
@Entity
@Table(name = "manager_change")
public class ManagerChange extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", length = 8, nullable = false)
    private ManagerChangeScope scope;

    /** The employee being transferred (LINE scope only). Null for PROJECT scope. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    /**
     * The project whose PM is changing (PROJECT scope only). Plain column, not a relationship —
     * no {@code Project} JPA entity exists yet (F04's concern). Always null for this feature's
     * LINE-scope rows.
     */
    @Column(name = "project_id")
    private Long projectId;

    /** The PROJECT-scope change that spawned this LINE row, if any. Not used by this feature. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ManagerChange parent;

    /** The employee's manager at request time (snapshot, nullable if they had none). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_manager_id")
    private Employee fromManager;

    /** The new manager this change moves the employee (or project) to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_manager_id", nullable = false)
    private Employee toManager;

    /** The date this change takes effect. Applied immediately if this is today or earlier. */
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    /** HR's free-text reason. Required for PROJECT scope; optional for LINE (DB CHECK). */
    @Column(name = "reason")
    private String reason;

    /**
     * Legacy all-or-nothing flag (DB default {@code true}); the real per-task selection is
     * {@link ManagerChangeTask} rows. Always left at its default by this feature.
     */
    @Column(name = "move_open_tasks", nullable = false)
    private boolean moveOpenTasks = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private ManagerChangeStatus status = ManagerChangeStatus.SCHEDULED;

    /** Set by the scheduler (or immediately, after-commit) when this change is applied. */
    @Column(name = "applied_at")
    private OffsetDateTime appliedAt;

    /** The HR user who requested this change. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private Employee requestedBy;

    /** Required no-arg constructor for JPA (public so service code in other packages can construct instances). */
    public ManagerChange() {}

    // ── Accessors ────────────────────────────────────────────────────────────

    public ManagerChangeScope getScope() { return scope; }
    public void setScope(final ManagerChangeScope scope) { this.scope = scope; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(final Employee employee) { this.employee = employee; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(final Long projectId) { this.projectId = projectId; }

    public ManagerChange getParent() { return parent; }
    public void setParent(final ManagerChange parent) { this.parent = parent; }

    public Employee getFromManager() { return fromManager; }
    public void setFromManager(final Employee fromManager) { this.fromManager = fromManager; }

    public Employee getToManager() { return toManager; }
    public void setToManager(final Employee toManager) { this.toManager = toManager; }

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(final LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }

    public String getReason() { return reason; }
    public void setReason(final String reason) { this.reason = reason; }

    public boolean isMoveOpenTasks() { return moveOpenTasks; }
    public void setMoveOpenTasks(final boolean moveOpenTasks) { this.moveOpenTasks = moveOpenTasks; }

    public ManagerChangeStatus getStatus() { return status; }
    public void setStatus(final ManagerChangeStatus status) { this.status = status; }

    public OffsetDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(final OffsetDateTime appliedAt) { this.appliedAt = appliedAt; }

    public Employee getRequestedBy() { return requestedBy; }
    public void setRequestedBy(final Employee requestedBy) { this.requestedBy = requestedBy; }
}
