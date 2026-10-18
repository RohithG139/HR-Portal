package com.cubeworks.hr.onboarding;

import com.cubeworks.hr.settings.OwnerRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Spring Data repository for {@link ChecklistItem}.
 *
 * <p>All queries use explicit {@code @Query} with JOIN FETCH where lazy associations are
 * needed, preventing the N+1 pattern on the checklist screens (R-CODE-1, CLAUDE.md Known Pitfalls).</p>
 */
public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {

    /**
     * Returns all onboarding checklist items for a given employee, with optional owner filter.
     *
     * <p>N+1 prevention: loads {@code doneBy} employee association in one JOIN FETCH, saving
     * one SELECT per done item when rendering the checklist (each item shows who marked it done).</p>
     *
     * @param employeeId the employee whose checklist is being loaded
     * @param phase      the checklist phase (e.g. ONBOARDING)
     * @return all checklist items ordered by section
     */
    @Query("""
            SELECT ci FROM ChecklistItem ci
            LEFT JOIN FETCH ci.doneBy
            WHERE ci.employee.id = :employeeId
              AND ci.phase = :phase
            ORDER BY ci.section, ci.id
            """)
    List<ChecklistItem> findByEmployeeIdAndPhase(
            @Param("employeeId") Long employeeId,
            @Param("phase") ChecklistPhase phase);

    /**
     * Returns all OPEN onboarding items owned by a specific role for a given employee.
     *
     * <p>Backed by the partial index {@code ix_checklist_open}.</p>
     *
     * @param employeeId the employee ID
     * @param phase      the checklist phase
     * @param ownerRole  the role that owns these items
     * @return all open items for the given owner role
     */
    @Query("""
            SELECT ci FROM ChecklistItem ci
            WHERE ci.employee.id = :employeeId
              AND ci.phase = :phase
              AND ci.ownerRole = :ownerRole
              AND ci.status = 'OPEN'
            """)
    List<ChecklistItem> findOpenByOwnerRole(
            @Param("employeeId") Long employeeId,
            @Param("phase") ChecklistPhase phase,
            @Param("ownerRole") OwnerRole ownerRole);

    /**
     * Counts how many mandatory checklist items are still open for a given employee.
     *
     * <p>Used by the "Complete onboarding" button logic (ONB-05, A19): if this count > 0,
     * the completion is rejected with {@code 409 MANDATORY_ITEMS_OPEN}.</p>
     *
     * @param employeeId the employee ID
     * @param phase      the checklist phase
     * @return the number of open mandatory items; 0 means completion is allowed
     */
    @Query("""
            SELECT COUNT(ci) FROM ChecklistItem ci
            WHERE ci.employee.id = :employeeId
              AND ci.phase = :phase
              AND ci.mandatory = true
              AND ci.status = 'OPEN'
            """)
    long countOpenMandatoryItems(
            @Param("employeeId") Long employeeId,
            @Param("phase") ChecklistPhase phase);

    /**
     * Counts how many mandatory IT_ADMIN-owned items are still open for a given employee.
     *
     * <p>Used when computing the {@code Onboarding.itSetupComplete} case-file field (D-18, §8):
     * if this count is 0, all IT work is done and the sentry for "Remaining setup" should not fire.</p>
     *
     * @param employeeId the employee ID
     * @param phase      the checklist phase
     * @return 0 when all IT mandatory items are done; > 0 when some remain open
     */
    @Query("""
            SELECT COUNT(ci) FROM ChecklistItem ci
            WHERE ci.employee.id = :employeeId
              AND ci.phase = :phase
              AND ci.ownerRole = 'IT_ADMIN'
              AND ci.mandatory = true
              AND ci.status = 'OPEN'
            """)
    long countOpenMandatoryItItems(
            @Param("employeeId") Long employeeId,
            @Param("phase") ChecklistPhase phase);

    /**
     * Returns all open checklist items for a given employee and phase, filtered to the
     * employee's own items only (ONB-07).
     *
     * @param employeeId the employee ID
     * @param phase      the checklist phase
     * @param ownerRole  the owner role matching the employee's app_role
     * @return the employee's own open checklist items
     */
    @Query("""
            SELECT ci FROM ChecklistItem ci
            LEFT JOIN FETCH ci.asset
            WHERE ci.employee.id = :employeeId
              AND ci.phase = :phase
              AND ci.ownerRole = :ownerRole
            ORDER BY ci.section, ci.id
            """)
    List<ChecklistItem> findByEmployeeIdAndPhaseAndOwnerRole(
            @Param("employeeId") Long employeeId,
            @Param("phase") ChecklistPhase phase,
            @Param("ownerRole") OwnerRole ownerRole);

    /**
     * Returns ALL open checklist items for a given phase and owner role, across all employees.
     *
     * <p>Used by the My Tasks screen for IT_ADMIN and EMPLOYEE roles: loads all pending work items
     * for every employee in a single query (N+1 prevention: JOIN FETCH loads the employee
     * association rather than issuing one SELECT per item).</p>
     *
     * @param phase     the checklist phase
     * @param ownerRole the role that owns these items
     * @return all open items for the given owner role across all employees, ordered by due date
     */
    @Query("""
            SELECT ci FROM ChecklistItem ci
            JOIN FETCH ci.employee
            WHERE ci.phase = :phase
              AND ci.ownerRole = :ownerRole
              AND ci.status = 'OPEN'
            ORDER BY ci.dueDate ASC, ci.id ASC
            """)
    List<ChecklistItem> findAllOpenByPhaseAndOwnerRole(
            @Param("phase") ChecklistPhase phase,
            @Param("ownerRole") OwnerRole ownerRole);

    /**
     * Returns all open MANAGER-owned checklist items for a manager's direct reports (OPEN-01,
     * S7M: the orientation items a manager must complete, FRD-02 §3 step 7 "HR and the manager
     * complete orientation items").
     *
     * <p>N+1 prevention: JOIN FETCH loads the employee association that the My Tasks DTO needs
     * for {@code employeeId} (used by the frontend to build the "/manager/team/{id}/orientation"
     * link).</p>
     *
     * @param phase     the checklist phase
     * @param managerId the manager's employee ID (WS-02: only their own direct reports)
     * @return open MANAGER-owned items across the manager's direct reports, ordered by due date
     */
    @Query("""
            SELECT ci FROM ChecklistItem ci
            JOIN FETCH ci.employee e
            WHERE ci.phase = :phase
              AND ci.ownerRole = 'MANAGER'
              AND ci.status = 'OPEN'
              AND e.manager.id = :managerId
            ORDER BY ci.dueDate ASC, ci.id ASC
            """)
    List<ChecklistItem> findOpenManagerItemsForManager(
            @Param("phase") ChecklistPhase phase,
            @Param("managerId") Long managerId);

    /**
     * Returns checklist items for the My Tasks view — all OPEN items for a given employee
     * and phase, regardless of owner role.
     *
     * @param employeeId the employee whose tasks are shown
     * @param phase      the checklist phase
     * @return open checklist items ordered by due date
     */
    @Query("""
            SELECT ci FROM ChecklistItem ci
            WHERE ci.employee.id = :employeeId
              AND ci.phase = :phase
              AND ci.status = 'OPEN'
            ORDER BY ci.dueDate ASC, ci.id ASC
            """)
    List<ChecklistItem> findOpenForEmployee(
            @Param("employeeId") Long employeeId,
            @Param("phase") ChecklistPhase phase);

    /**
     * Returns open checklist items (any phase — this is the cross-cutting G-05 rule, not
     * onboarding-specific) that are overdue by at least the configured reminder window and
     * have not yet been reminded.
     *
     * <p>N+1 prevention: JOIN FETCH loads the employee association that
     * {@link com.cubeworks.hr.task.OverdueReminderScheduler} needs to resolve the EMPLOYEE/
     * MANAGER owner-role recipient.</p>
     *
     * @param cutoff items due on or before this date are remindable
     * @return remindable overdue items
     */
    @Query("""
            SELECT ci FROM ChecklistItem ci
            JOIN FETCH ci.employee
            WHERE ci.status = 'OPEN'
              AND ci.dueDate <= :cutoff
              AND ci.overdueReminderAt IS NULL
            """)
    List<ChecklistItem> findRemindableOverdue(@Param("cutoff") LocalDate cutoff);
}
