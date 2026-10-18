package com.cubeworks.hr.common;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * JPA mapped superclass that provides only the audit columns (no {@code @Id}) for entities
 * whose primary key is not a standard {@code bigint generated always as identity} column.
 *
 * <p>Used by {@link com.cubeworks.hr.settings.Setting} (PK: {@code code varchar(60)}) and
 * {@link com.cubeworks.hr.task.EngineTask} (PK: {@code engine_task_id varchar(64)}).
 * Both entities declare their own {@code @Id} field; this superclass contributes the five
 * mandatory convention columns: {@code version}, {@code created_at}, {@code created_by},
 * {@code updated_at}, {@code updated_by} (R-DB-2, R-DB-5).</p>
 *
 * <p>Audit-field discipline: see {@link BaseEntity} for the Clock-bean rule that prohibits
 * {@code @CreationTimestamp} / {@code @UpdateTimestamp}.</p>
 */
@MappedSuperclass
public abstract class AuditableEntity {

    /**
     * Optimistic-lock counter. Stale updates surface as {@code 409 STALE_VERSION} (R-DB-5).
     */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /** Timestamp of row insertion — set by service layer via Clock bean. */
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    /** Employee ID who created this row (bare FK value — not a JPA association). */
    @Column(name = "created_by")
    private Long createdBy;

    /** Timestamp of last update — set by service layer via Clock bean. */
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** Employee ID who last modified this row. */
    @Column(name = "updated_by")
    private Long updatedBy;

    // ── Accessors ────────────────────────────────────────────────────────────

    public Integer getVersion() { return version; }
    public void setVersion(final Integer version) { this.version = version; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(final OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(final Long createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(final OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(final Long updatedBy) { this.updatedBy = updatedBy; }
}
