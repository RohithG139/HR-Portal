package com.cubeworks.hr.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link EngineTask}.
 *
 * <p>After V3 migration the PK is a surrogate {@code bigint}, so standard
 * {@code findById(Long)} is used for the undo endpoint (A37). The engine task ID string
 * is a unique column used by engine API calls. Queries here back the My Tasks screen (A36)
 * and the undo-window scheduler.</p>
 */
public interface EngineTaskRepository extends JpaRepository<EngineTask, Long> {

    /**
     * Returns all open engine tasks for a given employee.
     *
     * <p>N+1 prevention: this single query loads all open tasks for the employee dashboard
     * without issuing separate queries for each task's employee association.</p>
     *
     * @param employeeId the employee whose tasks to return
     * @return open tasks ordered by due date
     */
    @Query("""
            SELECT t FROM EngineTask t
            WHERE t.employee.id = :employeeId
              AND t.completedAt IS NULL
            ORDER BY t.dueDate ASC
            """)
    List<EngineTask> findOpenByEmployeeId(@Param("employeeId") Long employeeId);

    /**
     * Returns pending completions whose undo window has expired.
     *
     * <p>Backed by the partial index {@code ix_engine_task_pending}: only
     * {@link PendingStatus#PENDING} rows whose {@code pending_execute_at} has passed are
     * returned. Called by the undo-window scheduler every 5 seconds.</p>
     *
     * @param now the current instant; rows with pending_execute_at &lt;= now are returned
     * @return expired pending completions ready to be sent to the engine
     */
    @Query("""
            SELECT t FROM EngineTask t
            WHERE t.pendingStatus IN ('PENDING', 'FAILED')
              AND t.pendingExecuteAt <= :now
            """)
    List<EngineTask> findExpiredPending(@Param("now") OffsetDateTime now);

    /**
     * Returns all open engine tasks across all employees, filtered by owner role.
     *
     * <p>Used by the IT admin My Tasks screen to show "Equipment request" and "Remaining setup"
     * tasks. Ownership is by role (tenant role), not by a specific employee user.</p>
     *
     * @param taskName substring pattern match for the task name
     * @return matching open tasks
     */
    @Query("""
            SELECT t FROM EngineTask t
            JOIN FETCH t.employee
            WHERE t.completedAt IS NULL
              AND (:taskName IS NULL OR LOWER(t.taskName) LIKE LOWER(CONCAT('%', :taskName, '%')))
            ORDER BY t.dueDate ASC
            """)
    List<EngineTask> findOpenByTaskName(@Param("taskName") String taskName);

    /**
     * Returns every open engine task, across every employee and task name.
     *
     * <p>Used by {@link com.cubeworks.hr.engine.EngineTaskWatcher} to reconcile all open tasks
     * against the live engine in one query per poll. Deliberately a separate method rather than
     * calling {@link #findOpenByTaskName} with a {@code null} argument: PostgreSQL cannot infer
     * a null bind parameter's type inside {@code LOWER(CONCAT('%', :taskName, '%'))} and defaults
     * it to {@code bytea}, which then fails with "function lower(bytea) does not exist" — found
     * live, 2026-10-16, not a theoretical concern.</p>
     *
     * @return every open task, employee eagerly loaded (N+1 prevention — the watcher always
     *         needs {@code employee.employeeCode})
     */
    @Query("""
            SELECT t FROM EngineTask t
            JOIN FETCH t.employee
            WHERE t.completedAt IS NULL
            """)
    List<EngineTask> findAllOpen();

    /**
     * Counts open engine tasks across all employees for a given task name.
     *
     * <p>Used by the IT admin dashboard (W04) to populate the Equipment requests and
     * Open setup items counts without loading full entity objects — one scalar query
     * instead of {@link #findOpenByTaskName} followed by {@code .size()}.</p>
     *
     * @param taskName the exact task name to count
     * @return number of open (uncompleted) tasks with this name
     */
    @Query("""
            SELECT COUNT(t) FROM EngineTask t
            WHERE t.taskName = :taskName
              AND t.completedAt IS NULL
            """)
    long countOpenByTaskName(@Param("taskName") String taskName);

    /**
     * Returns all open engine tasks for an employee that are currently owned by a given employee.
     *
     * <p>Used by W09 ({@code GET /api/employees/{id}/manager-tasks}) to populate the tick-list
     * in the Transfer manager modal: HR sees only tasks whose owner is the employee's CURRENT
     * manager, so that the tick-list shows what would actually transfer.</p>
     *
     * <p>Null-safe: tasks with no owner ({@code owner_employee_id IS NULL}) are excluded because
     * JPQL's {@code t.ownerEmployee.id = :managerId} treats a null association path as no-match.</p>
     *
     * @param employeeId the employee whose case tasks to query
     * @param managerId  the current manager's employee id
     * @return open tasks for this employee owned by this manager, due-date ordered
     */
    @Query("""
            SELECT t FROM EngineTask t
            WHERE t.employee.id = :employeeId
              AND t.ownerEmployee.id = :managerId
              AND t.completedAt IS NULL
            ORDER BY t.dueDate ASC
            """)
    List<EngineTask> findOpenByEmployeeAndOwner(
            @Param("employeeId") Long employeeId,
            @Param("managerId") Long managerId);

    /**
     * Finds the open engine task for a given employee with a specific task name.
     *
     * <p>Used when completing onboarding (A19) to find the "Onboarding checklist" task
     * and apply the undo-window.</p>
     *
     * @param employeeId the employee ID
     * @param taskName   the exact task name
     * @return the open task, if it exists
     */
    @Query("""
            SELECT t FROM EngineTask t
            WHERE t.employee.id = :employeeId
              AND t.taskName = :taskName
              AND t.completedAt IS NULL
              AND (t.pendingStatus IS NULL OR t.pendingStatus = 'UNDONE')
            """)
    Optional<EngineTask> findOpenTaskByName(
            @Param("employeeId") Long employeeId,
            @Param("taskName") String taskName);

    /**
     * Returns open engine tasks that are overdue by at least the configured reminder
     * window and have not yet been reminded (G-05).
     *
     * <p>N+1 prevention: JOIN FETCH loads the employee association that
     * {@link OverdueReminderScheduler} needs to raise the reminder notification.</p>
     *
     * @param cutoff tasks due on or before this date are remindable
     * @return remindable overdue tasks
     */
    /**
     * Open tasks with this exact name owned by one employee (a manager's "Probation review").
     *
     * @param ownerId  {@code engine_task.owner_employee_id}
     * @param taskName exact task name
     * @return open tasks, earliest due first
     */
    @Query("""
            SELECT t FROM EngineTask t
            JOIN FETCH t.employee
            WHERE t.ownerEmployee.id = :ownerId
              AND t.taskName = :taskName
              AND t.completedAt IS NULL
            ORDER BY t.dueDate ASC
            """)
    List<EngineTask> findOpenByOwnerAndTaskName(@Param("ownerId") Long ownerId,
                                                @Param("taskName") String taskName);

    /**
     * Open tasks with this exact name about one employee (the employee's own
     * "Create development plan").
     *
     * @param employeeId the employee the task is about
     * @param taskName   exact task name
     * @return open tasks, earliest due first
     */
    @Query("""
            SELECT t FROM EngineTask t
            JOIN FETCH t.employee
            WHERE t.employee.id = :employeeId
              AND t.taskName = :taskName
              AND t.completedAt IS NULL
            ORDER BY t.dueDate ASC
            """)
    List<EngineTask> findOpenByEmployeeAndTaskName(@Param("employeeId") Long employeeId,
                                                   @Param("taskName") String taskName);

    /** Whether any task (open or completed) with this name ever existed for the employee. */
    boolean existsByEmployeeIdAndTaskName(Long employeeId, String taskName);

    @Query("""
            SELECT t FROM EngineTask t
            JOIN FETCH t.employee
            WHERE t.completedAt IS NULL
              AND t.dueDate <= :cutoff
              AND t.overdueReminderAt IS NULL
            """)
    List<EngineTask> findRemindableOverdue(@Param("cutoff") LocalDate cutoff);
}
