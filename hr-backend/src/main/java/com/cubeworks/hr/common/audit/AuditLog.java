package com.cubeworks.hr.common.audit;

import com.cubeworks.hr.common.BaseEntity;
import com.cubeworks.hr.employee.Employee;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;

/**
 * An immutable audit record of a state-changing operation (G-06, R-API-5, ADM-05).
 *
 * <p>Maps to {@code hr.audit_log}. Every service method that mutates business state must
 * write an {@code audit_log} row capturing who did what, when, and the before/after values.
 * This table is effectively append-only: rows are inserted, never updated.</p>
 *
 * <p>Asset history (formerly {@code asset_event} in v1) is also tracked here with
 * {@code entity_type = 'ASSET'} (data-model §4).</p>
 *
 * <p>The {@code changed_at} column records the business-event timestamp; the convention
 * {@code created_at} from {@link BaseEntity} records when this audit row was inserted into
 * the DB — they are distinct and both exist in the schema.</p>
 *
 * <p>Before/after state is stored as {@code jsonb} for flexibility: the schema of the
 * captured state can evolve without migration changes to the audit table itself.</p>
 */
@Entity
@Table(name = "audit_log")
public class AuditLog extends BaseEntity {

    /**
     * The employee whose data was modified (the subject), or {@code null} for operations
     * that are not employee-specific (e.g. system settings changes).
     * Lazy: the employee object is not needed when reading audit logs for a different purpose.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    /**
     * The table or domain object type being audited (e.g. {@code "EMPLOYEE"},
     * {@code "CONTRACT"}, {@code "ASSET"}, {@code "ONBOARDING"}).
     */
    @Column(name = "entity_type", length = 40, nullable = false)
    private String entityType;

    /**
     * The surrogate ID of the audited row, or {@code null} for audit records that
     * do not correspond to a single-row operation (e.g. bulk status changes).
     */
    @Column(name = "entity_id")
    private Long entityId;

    /**
     * The type of action performed (e.g. {@code "CREATE"}, {@code "UPDATE"},
     * {@code "COMPLETE_ONBOARDING"}, {@code "ASSET_ISSUED"}).
     */
    @Column(name = "action", length = 40, nullable = false)
    private String action;

    /**
     * The employee who performed the action.
     * Non-null for user-initiated operations; null for system-triggered operations
     * (e.g. timer-fired actions where no user is the actor).
     * Lazy: the actor details are only needed in the audit detail view.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private Employee changedBy;

    /**
     * Timestamp of the business event being audited.
     * Distinct from the convention {@code created_at} (when this row was inserted);
     * for real-time audit they are usually identical but differ for back-dated imports.
     */
    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    /**
     * JSON snapshot of the entity's state before the change.
     * Null for {@code CREATE} operations (there is no "before" state).
     *
     * <p>Using {@code @JdbcTypeCode(SqlTypes.JSON)} for Hibernate 6 jsonb type mapping.</p>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_value", columnDefinition = "jsonb")
    private String beforeValue;

    /**
     * JSON snapshot of the entity's state after the change.
     * Null for {@code DELETE} operations (there is no "after" state).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_value", columnDefinition = "jsonb")
    private String afterValue;

    /**
     * Human-readable description of special circumstances (V5 migration).
     * Used when the action needs additional context beyond the before/after snapshots.
     * Example: "ticked on behalf of IT_ADMIN" for AC-24 on-behalf tick.
     * Null for ordinary audit entries where the action and snapshots are self-explanatory.
     */
    @Column(name = "details", columnDefinition = "text")
    private String details;

    /** Required no-arg constructor for JPA. */
    protected AuditLog() {}

    // ── Accessors ────────────────────────────────────────────────────────────

    public Employee getEmployee() { return employee; }
    public void setEmployee(final Employee employee) { this.employee = employee; }

    public String getEntityType() { return entityType; }
    public void setEntityType(final String entityType) { this.entityType = entityType; }

    public Long getEntityId() { return entityId; }
    public void setEntityId(final Long entityId) { this.entityId = entityId; }

    public String getAction() { return action; }
    public void setAction(final String action) { this.action = action; }

    public Employee getChangedBy() { return changedBy; }
    public void setChangedBy(final Employee changedBy) { this.changedBy = changedBy; }

    public OffsetDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(final OffsetDateTime changedAt) { this.changedAt = changedAt; }

    public String getBeforeValue() { return beforeValue; }
    public void setBeforeValue(final String beforeValue) { this.beforeValue = beforeValue; }

    public String getAfterValue() { return afterValue; }
    public void setAfterValue(final String afterValue) { this.afterValue = afterValue; }

    public String getDetails() { return details; }
    public void setDetails(final String details) { this.details = details; }
}
