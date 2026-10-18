package com.cubeworks.hr.model.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * Links a {@link ManagerChange} to one {@code engine_task} row HR ticked to move to the new
 * manager (FRD-01 spec §9 D-W3, §12).
 *
 * <p>Maps to {@code hr.manager_change_task} — a pure junction table with a composite primary
 * key, no surrogate id (R-DB-2's own carve-out for link tables like this one). Unticked tasks
 * simply have no row here; they are never otherwise referenced by the {@link ManagerChange}.</p>
 *
 * <p>{@link #engineTaskId} is a bigint FK to {@code engine_task}'s real surrogate PK ({@code id},
 * added by {@code V3__engine_task_surrogate_id.sql}) — named {@code engine_task_id} to match the
 * FRD-01 spec's own DDL, not the varchar natural-key column on {@code engine_task} that happens
 * to share that name.</p>
 */
@Entity
@Table(name = "manager_change_task")
@IdClass(ManagerChangeTask.Key.class)
public class ManagerChangeTask {

    @Id
    @Column(name = "change_id")
    private Long changeId;

    @Id
    @Column(name = "engine_task_id")
    private Long engineTaskId;

    /** Required no-arg constructor for JPA. */
    protected ManagerChangeTask() {}

    public ManagerChangeTask(final Long changeId, final Long engineTaskId) {
        this.changeId = changeId;
        this.engineTaskId = engineTaskId;
    }

    public Long getChangeId() { return changeId; }

    public Long getEngineTaskId() { return engineTaskId; }

    /** Composite key class for {@link ManagerChangeTask}, per JPA's {@code @IdClass} contract. */
    public static class Key implements Serializable {
        private Long changeId;
        private Long engineTaskId;

        public Key() {}

        public Key(final Long changeId, final Long engineTaskId) {
            this.changeId = changeId;
            this.engineTaskId = engineTaskId;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(changeId, key.changeId) && Objects.equals(engineTaskId, key.engineTaskId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(changeId, engineTaskId);
        }
    }
}
