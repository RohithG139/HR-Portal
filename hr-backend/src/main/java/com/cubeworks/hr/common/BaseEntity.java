package com.cubeworks.hr.common;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * JPA mapped superclass that provides the standard primary-key and audit columns
 * for every {@code hr} schema table that uses {@code bigint generated always as identity} as its PK.
 *
 * <p>Convention from {@code 02-data-model.md}: every table carries {@code id} (bigint identity),
 * {@code created_at}, {@code created_by}, {@code updated_at}, {@code updated_by} and
 * {@code version} (optimistic lock). These five are declared once here and inherited by all
 * standard entities; entities with a non-identity PK (e.g. {@code setting}, {@code engine_task})
 * extend {@link AuditableEntity} instead and declare their own {@code @Id}.</p>
 *
 * <p>Audit-field discipline (R-CODE-1): service methods <strong>must</strong> set
 * {@code createdAt/updatedAt} using the injected {@link java.time.Clock} bean — never via
 * {@code LocalDate.now()} or Hibernate's {@code @CreationTimestamp} / {@code @UpdateTimestamp},
 * which bypass the clock bean and make time-sensitive tests non-deterministic.</p>
 *
 * <p>{@code version} triggers an {@link jakarta.persistence.OptimisticLockException} on a stale
 * update, which the service layer catches and re-throws as a {@code 409 STALE_VERSION} response
 * (R-DB-5).</p>
 */
@MappedSuperclass
public abstract class BaseEntity {

    /**
     * Surrogate primary key — {@code bigint generated always as identity} in PostgreSQL.
     * Never set this from application code; the database generates it on INSERT.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /**
     * Optimistic-lock counter (R-DB-5). Hibernate increments this on every UPDATE.
     * A stale-read conflict surfaces as {@link jakarta.persistence.OptimisticLockException};
     * the global exception handler maps it to {@code 409 STALE_VERSION}.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * Timestamp when this row was first inserted.
     * Service layer sets this from the injected {@code Clock} bean — never from Hibernate annotations.
     */
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    /**
     * ID of the {@code employee} who created this row (foreign key by value, not a JPA association,
     * to avoid a circular dependency in this shared superclass).
     */
    @Column(name = "created_by")
    private Long createdBy;

    /**
     * Timestamp of the most recent UPDATE on this row.
     * Service layer sets this from the injected {@code Clock} bean on every mutating operation.
     */
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /**
     * ID of the {@code employee} who last modified this row.
     */
    @Column(name = "updated_by")
    private Long updatedBy;

    // ── Accessors ────────────────────────────────────────────────────────────

    public Long getId() { return id; }

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
