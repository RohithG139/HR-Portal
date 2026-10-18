package com.cubeworks.hr.common.audit;

import com.cubeworks.hr.employee.Employee;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Writes immutable audit-log entries recording every state-changing operation (G-06, R-API-5).
 *
 * <h2>Calling convention</h2>
 * <p>This service must be called inside the same {@code @Transactional} boundary as the business
 * change it records. If the business transaction rolls back, the audit row disappears with it —
 * which is correct because the change did not happen. Never call this after the transaction
 * has committed.</p>
 *
 * <h2>JSON snapshots</h2>
 * <p>The {@code beforeValue} and {@code afterValue} fields are free-form JSON strings. Callers
 * are responsible for serialising the relevant state into JSON (typically with
 * {@code Jackson ObjectMapper}) before calling this service. Keeping serialisation outside this
 * service makes the audit rows readable without a running application.</p>
 */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param auditLogRepository the repository for persisting audit entries
     * @param clock              the application-wide clock bean (never call Instant.now() directly)
     */
    public AuditLogService(final AuditLogRepository auditLogRepository, final Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    /**
     * Records a state change in the audit log.
     *
     * <p>Must be called inside the same {@code @Transactional} method as the business change.
     * Atomicity: this row is committed in the same transaction as the business rows it documents,
     * so a rollback removes both — there are no orphaned audit entries.</p>
     *
     * @param subjectEmployee the employee whose data is being changed (the subject of the change;
     *                        null for non-employee-specific operations such as system settings)
     * @param entityType      the table or domain type being audited (e.g. {@code "EMPLOYEE"})
     * @param entityId        the surrogate PK of the changed row (null for bulk changes)
     * @param action          the operation type (e.g. {@code "CREATE"}, {@code "UPDATE"})
     * @param actor           the employee performing the change (null for system-triggered changes)
     * @param beforeValue     JSON snapshot of state before the change (null for CREATE operations)
     * @param afterValue      JSON snapshot of state after the change (null for DELETE operations)
     */
    public void record(final Employee subjectEmployee,
                       final String entityType,
                       final Long entityId,
                       final String action,
                       final Employee actor,
                       final String beforeValue,
                       final String afterValue) {

        final AuditLog entry = new AuditLog();
        entry.setEmployee(subjectEmployee);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setChangedBy(actor);
        entry.setChangedAt(OffsetDateTime.now(clock));
        entry.setBeforeValue(beforeValue);
        entry.setAfterValue(afterValue);
        entry.setCreatedAt(OffsetDateTime.now(clock));
        entry.setCreatedBy(actor != null ? actor.getId() : null);
        entry.setUpdatedAt(OffsetDateTime.now(clock));
        entry.setUpdatedBy(actor != null ? actor.getId() : null);

        auditLogRepository.save(entry);
    }

    /**
     * Records a state change with an additional human-readable details note.
     *
     * <p>Use this overload when the action requires supplementary context beyond the before/after
     * snapshots — for example, "ticked on behalf of IT_ADMIN" (AC-24, D-04). The {@code details}
     * text is stored in the {@code audit_log.details} column (V5 migration) and is searchable
     * in the History tab.</p>
     *
     * @param subjectEmployee the employee whose data is being changed
     * @param entityType      the table or domain type being audited
     * @param entityId        the surrogate PK of the changed row
     * @param action          the operation type
     * @param actor           the employee performing the change
     * @param beforeValue     JSON snapshot before the change (null for CREATE)
     * @param afterValue      JSON snapshot after the change (null for DELETE)
     * @param details         human-readable circumstance note; null for ordinary entries
     */
    public void record(final Employee subjectEmployee,
                       final String entityType,
                       final Long entityId,
                       final String action,
                       final Employee actor,
                       final String beforeValue,
                       final String afterValue,
                       final String details) {

        final AuditLog entry = new AuditLog();
        entry.setEmployee(subjectEmployee);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setChangedBy(actor);
        entry.setChangedAt(OffsetDateTime.now(clock));
        entry.setBeforeValue(beforeValue);
        entry.setAfterValue(afterValue);
        entry.setDetails(details);
        entry.setCreatedAt(OffsetDateTime.now(clock));
        entry.setCreatedBy(actor != null ? actor.getId() : null);
        entry.setUpdatedAt(OffsetDateTime.now(clock));
        entry.setUpdatedBy(actor != null ? actor.getId() : null);

        auditLogRepository.save(entry);
    }
}
