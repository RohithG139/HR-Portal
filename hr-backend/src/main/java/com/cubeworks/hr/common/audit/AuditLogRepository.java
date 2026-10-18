package com.cubeworks.hr.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link AuditLog}.
 *
 * <p>{@link #findByEmployeeId} powers the History tab (A35) which merges audit entries
 * with engine milestones for a given employee. The query is ordered newest-first so that
 * HR sees recent changes at the top without additional sorting.</p>
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Returns a page of audit-log entries for a given employee, newest first.
     *
     * <p>Used by the History tab (A35). The result is merged in the service layer with
     * engine milestone events (retrieved from the engine task table).</p>
     *
     * @param employeeId the employee whose history is being viewed
     * @param pageable   paging parameters
     * @return audit entries, most recent first
     */
    @Query("""
            SELECT a FROM AuditLog a
            LEFT JOIN FETCH a.changedBy
            WHERE a.employee.id = :employeeId
            ORDER BY a.changedAt DESC
            """)
    Page<AuditLog> findByEmployeeId(@Param("employeeId") Long employeeId, Pageable pageable);
}
