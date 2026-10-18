package com.cubeworks.hr.repository.workspace;

import com.cubeworks.hr.model.workspace.ManagerChange;
import com.cubeworks.hr.model.workspace.ManagerChangeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Spring Data repository for {@link ManagerChange} (FRD-01 Wave 3, Transfer manager).
 */
public interface ManagerChangeRepository extends JpaRepository<ManagerChange, Long> {

    /**
     * Returns every scheduled change due to apply (effective date today or earlier) — the
     * scheduler's own poll query, same pattern as
     * {@code EngineTaskRepository#findExpiredPending}.
     *
     * @param status the status to match (always {@link ManagerChangeStatus#SCHEDULED} in practice)
     * @param today  the current date; rows with {@code effective_date <= today} are due
     * @return due changes, oldest effective date first
     */
    @Query("""
            SELECT m FROM ManagerChange m
            WHERE m.status = :status AND m.effectiveDate <= :today
            ORDER BY m.effectiveDate ASC
            """)
    List<ManagerChange> findDue(@Param("status") ManagerChangeStatus status, @Param("today") LocalDate today);

    /**
     * Returns {@code true} if at least one {@code manager_change} row exists for the given
     * employee with the given status.
     *
     * <p>Used by {@link com.cubeworks.hr.service.workspace.TransferManagerService#createTransfer}
     * to prevent scheduling a second concurrent transfer for the same employee when one is
     * already SCHEDULED (fix #5b — duplicate scheduled transfer guard). Spring Data resolves
     * {@code employeeId} as the nested property {@code employee.id} on the {@link ManagerChange}
     * entity, following the same nested-property convention as
     * {@code EmployeeRepository#findByManagerId}.</p>
     *
     * @param employeeId the employee's surrogate PK
     * @param status     the status to match (always {@link ManagerChangeStatus#SCHEDULED} in practice)
     * @return true if a matching row exists
     */
    boolean existsByEmployeeIdAndStatus(Long employeeId, ManagerChangeStatus status);
}
