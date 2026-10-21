package com.cubeworks.hr.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Spring Data repository for {@link EmailOutbox}.
 *
 * <p>{@link #findByStatus} is used by {@link EmailOutboxSenderJob} to load all pending rows
 * on each scheduler tick. For gate 2 the volume is small so a simple derived query suffices.
 * A later optimisation can add {@code SELECT FOR UPDATE SKIP LOCKED} to prevent duplicate
 * processing across multiple scheduler threads or JVM instances.</p>
 */
public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    /**
     * Returns all outbox rows with the given delivery status.
     *
     * <p>Used by the background sender job to fetch {@link EmailOutboxStatus#PENDING} rows
     * that have not yet been delivered to the SMTP relay.</p>
     *
     * @param status the status to filter by
     * @return all matching outbox rows; empty list if none
     */
    List<EmailOutbox> findByStatus(EmailOutboxStatus status);

    /**
     * Returns outbox rows for a given employee with the given status — used to find a specific
     * employee's {@link EmailOutboxStatus#HELD} "Welcome new employee" row when the case
     * engine's "Release onboarding emails" callback (or its fallback scheduler) fires.
     *
     * @param employeeId the employee the e-mail is about
     * @param status     the status to filter by
     * @return matching outbox rows; empty list if none
     */
    List<EmailOutbox> findByEmployeeIdAndStatus(Long employeeId, EmailOutboxStatus status);

    /**
     * Returns all outbox rows still in a given status with {@code created_at} older than the
     * cutoff — used by {@link com.cubeworks.hr.onboarding.OnboardingEmailReleaseScheduler} to
     * find {@link EmailOutboxStatus#HELD} rows whose engine callback never arrived.
     *
     * <p>N+1 prevention / lazy-session safety: {@code JOIN FETCH} loads the {@code employee}
     * association eagerly, in the same query. Without it, the scheduler's own call (a separate,
     * non-transactional method) accessing {@code row.getEmployee().getIdpSubject()} later throws
     * {@code LazyInitializationException: no session} — found live via this exact test, since
     * this repository method's own implicit transaction has already closed by the time the
     * scheduler loop runs (same pattern already used by
     * {@code EngineTaskRepository.findRemindableOverdue}).</p>
     *
     * @param status the status to filter by (always {@link EmailOutboxStatus#HELD} in practice)
     * @param cutoff rows created at or before this instant are returned
     * @return matching outbox rows, employee eagerly loaded; empty list if none
     */
    @Query("""
            SELECT eo FROM EmailOutbox eo
            JOIN FETCH eo.employee
            WHERE eo.status = :status AND eo.createdAt <= :cutoff
            """)
    List<EmailOutbox> findByStatusAndCreatedAtLessThanEqual(
            @Param("status") EmailOutboxStatus status,
            @Param("cutoff") OffsetDateTime cutoff);
}
