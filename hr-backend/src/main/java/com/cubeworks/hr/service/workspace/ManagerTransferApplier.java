package com.cubeworks.hr.service.workspace;

import com.cubeworks.hr.common.audit.AuditLogService;
import com.cubeworks.hr.common.outbox.EmailOutboxService;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.employee.EmployeeRepository;
import com.cubeworks.hr.model.workspace.ManagerChange;
import com.cubeworks.hr.model.workspace.ManagerChangeStatus;
import com.cubeworks.hr.model.workspace.ManagerChangeTask;
import com.cubeworks.hr.notification.Notification;
import com.cubeworks.hr.notification.NotificationRepository;
import com.cubeworks.hr.repository.workspace.ManagerChangeRepository;
import com.cubeworks.hr.repository.workspace.ManagerChangeTaskRepository;
import com.cubeworks.hr.task.EngineTask;
import com.cubeworks.hr.task.EngineTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Applies a scheduled manager transfer — the shared logic called both by the
 * {@link ManagerTransferScheduler} (when the effective date arrives) and by
 * {@link TransferManagerService} (immediately after commit when the effective date is today).
 *
 * <p>Extracting this into its own {@code @Service} bean avoids the self-injection pattern that
 * would otherwise be needed: both callers inject this bean directly and call
 * {@link #applyChange(Long)}, which starts its own transaction (or joins one if a transaction
 * is already active, which only happens from the scheduler's per-row {@code @Transactional}
 * method).</p>
 *
 * <h2>What applying a transfer does</h2>
 * <ol>
 *   <li>Updates {@code employee.manager_id} to the new manager.</li>
 *   <li>For each {@link ManagerChangeTask} (ticked task): updates
 *       {@code engine_task.owner_employee_id} to the new manager and queues a "Task assigned"
 *       e-mail to the new manager (reusing the existing {@code TASK_ASSIGNED} template,
 *       resolved per D-W4).</li>
 *   <li>Sets {@code manager_change.status = APPLIED} and {@code applied_at = now}.</li>
 *   <li>Writes an {@code audit_log} entry for the employee (entity type {@code "MANAGER_CHANGE"}).</li>
 *   <li>Sends in-app {@code notification} rows to the old manager, the new manager, and
 *       the employee (D-W4, in-app only — no dedicated e-mail template exists yet for the
 *       transfer event itself; see spec §10).</li>
 * </ol>
 *
 * <h2>Case-team update (deliberately skipped)</h2>
 * <p>The spec §7 mentions updating the case's Manager case-team role after a transfer.
 * This step is <strong>intentionally omitted</strong>: the service account holds all case-team
 * roles directly (see {@code RealCaseEngineClient}'s class Javadoc — "Case team (simplification,
 * documented, not hidden)"). There is no per-manager engine user to swap. Adding a
 * {@code CaseEngineClient} method for this would be dead code. If a future wave introduces
 * real per-user engine registration, the engine call should be added here.</p>
 *
 * <h2>Transaction boundary</h2>
 * <p>The entire apply — employee update, engine-task reassignment, status change, audit log, and
 * notifications — is committed atomically. A partial failure rolls everything back: the employee's
 * manager is not updated without the audit log, and no notification is sent without the status
 * being set to APPLIED. This is correct: a half-applied transfer is more confusing than a failed
 * one that is retried on the next scheduler tick (R-ARC-5).</p>
 */
@Service
public class ManagerTransferApplier {

    private static final Logger log = LoggerFactory.getLogger(ManagerTransferApplier.class);

    private final ManagerChangeRepository managerChangeRepository;
    private final ManagerChangeTaskRepository managerChangeTaskRepository;
    private final EmployeeRepository employeeRepository;
    private final EngineTaskRepository engineTaskRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogService auditLogService;
    private final EmailOutboxService emailOutboxService;
    private final Clock clock;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param managerChangeRepository     repository for {@code manager_change} rows
     * @param managerChangeTaskRepository repository for {@code manager_change_task} link rows
     * @param employeeRepository          repository for updating {@code employee.manager_id}
     * @param engineTaskRepository        repository for updating {@code engine_task.owner_employee_id}
     * @param notificationRepository      writes in-app notification rows (D-W4)
     * @param auditLogService             writes the mandatory audit entry (R-API-5 / G-06)
     * @param emailOutboxService          queues "Task assigned" e-mails for moved tasks (D-W4)
     * @param clock                       application-wide clock bean (never call {@code OffsetDateTime.now()} directly)
     */
    public ManagerTransferApplier(final ManagerChangeRepository managerChangeRepository,
                                   final ManagerChangeTaskRepository managerChangeTaskRepository,
                                   final EmployeeRepository employeeRepository,
                                   final EngineTaskRepository engineTaskRepository,
                                   final NotificationRepository notificationRepository,
                                   final AuditLogService auditLogService,
                                   final EmailOutboxService emailOutboxService,
                                   final Clock clock) {
        this.managerChangeRepository = managerChangeRepository;
        this.managerChangeTaskRepository = managerChangeTaskRepository;
        this.employeeRepository = employeeRepository;
        this.engineTaskRepository = engineTaskRepository;
        this.notificationRepository = notificationRepository;
        this.auditLogService = auditLogService;
        this.emailOutboxService = emailOutboxService;
        this.clock = clock;
    }

    /**
     * Applies one manager-change: reassigns the employee, moves ticked tasks, writes audit log
     * and sends in-app notifications. All operations are atomic in a single transaction.
     *
     * <p>Idempotent guard: if the change is already {@link ManagerChangeStatus#APPLIED} (e.g.
     * the after-commit hook and the scheduler fire concurrently), the method returns immediately
     * without making any further changes.</p>
     *
     * <p>Transaction propagation: {@link Propagation#REQUIRES_NEW} is mandatory here because
     * this method is called from two sites:</p>
     * <ol>
     *   <li>{@link TransferManagerService}'s {@code afterCommit()} callback — at that point the
     *       original business transaction is fully committed and its connection is closed. A plain
     *       {@code @Transactional} would try to join the already-committed transaction's stale
     *       resources and its writes would be silently lost. {@code REQUIRES_NEW} opens a brand-new
     *       connection, same pattern as {@code OnboardingService.createEngineTaskInNewTx} (whose
     *       Javadoc documents exactly this class of bug).</li>
     *   <li>{@link ManagerTransferScheduler}'s polling loop — that method is non-transactional,
     *       so {@code REQUIRES_NEW} is identical to plain {@code REQUIRED} in that context.</li>
     * </ol>
     *
     * <p>Transaction: covers the {@code employee}, {@code engine_task}, {@code manager_change},
     * {@code audit_log} and {@code notification} writes. Partial failure rolls back all of them —
     * atomicity preserved. The scheduler retries on the next tick if an exception escapes.</p>
     *
     * @param changeId the surrogate PK of the {@link ManagerChange} to apply
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyChange(final Long changeId) {
        final ManagerChange change = managerChangeRepository.findById(changeId).orElse(null);
        if (change == null) {
            log.warn("ManagerChange id={} not found; skipping apply", changeId);
            return;
        }
        // Idempotent guard: do not apply twice (after-commit + scheduler race condition)
        if (change.getStatus() != ManagerChangeStatus.SCHEDULED) {
            log.debug("ManagerChange id={} already in status {}; skipping", changeId, change.getStatus());
            return;
        }

        final OffsetDateTime now = OffsetDateTime.now(clock);
        final Employee employee = change.getEmployee();
        final Employee oldManager = change.getFromManager();
        final Employee newManager = change.getToManager();

        if (employee == null || newManager == null) {
            log.error("ManagerChange id={} is missing employee or toManager; skipping apply", changeId);
            return;
        }

        // 1. Reassign the employee's line manager.
        // Fix #4: use the HR user who requested the transfer as the audit actor, not the new manager.
        // The new manager took no action here — the request was initiated by an HR user (requestedBy).
        employee.setManager(newManager);
        employee.setUpdatedAt(now);
        employee.setUpdatedBy(change.getRequestedBy().getId());
        employeeRepository.save(employee);

        // 2. Move ticked engine tasks to the new manager and queue a "Task assigned" e-mail per task.
        //    D-W4 (resolved): reuse the "TASK_ASSIGNED" e-mail template for moved tasks — a task
        //    genuinely being reassigned to the new manager, same semantic as a new task assignment.
        //
        //    Fix #13 (N+1 prevention): collect all referenced engine_task surrogate PKs first, then
        //    load them in a single findAllById call. Without this, the loop would issue one SELECT
        //    per manager_change_task row (one query saved per moved task).
        final List<ManagerChangeTask> taskLinks = managerChangeTaskRepository.findByChangeId(changeId);
        final List<Long> referencedTaskIds = taskLinks.stream()
                .map(ManagerChangeTask::getEngineTaskId)
                .toList();
        final Map<Long, EngineTask> engineTaskMap = engineTaskRepository.findAllById(referencedTaskIds)
                .stream()
                .collect(Collectors.toMap(EngineTask::getId, t -> t));

        for (final ManagerChangeTask link : taskLinks) {
            final EngineTask engineTask = engineTaskMap.get(link.getEngineTaskId());
            if (engineTask == null) {
                log.warn("ManagerChangeTask references missing engine_task id={}; skipping", link.getEngineTaskId());
                continue;
            }
            // Fix #1 apply-time re-validation: a task could have been completed or reassigned
            // between the time the transfer was scheduled and the effective date. Skip it rather than
            // overwriting a legitimately completed or already-reassigned task.
            if (engineTask.getCompletedAt() != null
                    || engineTask.getEmployee() == null
                    || !engineTask.getEmployee().getId().equals(employee.getId())
                    || oldManager == null
                    || engineTask.getOwnerEmployee() == null
                    || !engineTask.getOwnerEmployee().getId().equals(oldManager.getId())) {
                log.warn("ManagerChangeTask id={} for changeId={} is no longer a valid open task owned by the "
                        + "outgoing manager; skipping reassignment", link.getEngineTaskId(), changeId);
                continue;
            }
            engineTask.setOwnerEmployee(newManager);
            engineTask.setUpdatedAt(now);
            // Fix #4: set updatedBy on the reassigned task — the HR user who requested the transfer.
            engineTask.setUpdatedBy(change.getRequestedBy().getId());
            engineTaskRepository.save(engineTask);

            // Queue "Task assigned" e-mail to the new manager for each moved task (R-ARC-6).
            if (newManager.getPrivateEmail() != null) {
                emailOutboxService.enqueue(
                        "TASK_ASSIGNED",
                        newManager.getPrivateEmail(),
                        "New task: " + engineTask.getTaskName(),
                        "Hi " + newManager.getFirstName() + ",\n\nThe task \"" + engineTask.getTaskName()
                                + "\" for " + employee.getFirstName() + " " + employee.getLastName()
                                + " has been transferred to you.",
                        employee);
            }
        }

        // 3. Mark the change as applied.
        change.setStatus(ManagerChangeStatus.APPLIED);
        change.setAppliedAt(now);
        change.setUpdatedAt(now);
        managerChangeRepository.save(change);

        // 4. Write audit log (R-API-5 / G-06) — inside the same transaction.
        auditLogService.record(employee, "MANAGER_CHANGE", changeId, "APPLIED",
                change.getRequestedBy(),
                oldManager != null ? "{\"managerId\":" + oldManager.getId() + "}" : null,
                "{\"managerId\":" + newManager.getId() + "}");

        // 5. In-app notifications to old manager, new manager and employee (D-W4).
        //    The FRD gives no exact copy for in-app notifications; plain factual text is used
        //    ([ASSUMPTION] per spec §12 D-W4). Upgradeable to e-mail when FRD-08 adds a template.
        final String employeeName = employee.getFirstName() + " " + employee.getLastName();
        final String newManagerName = newManager.getFirstName() + " " + newManager.getLastName();

        if (oldManager != null) {
            saveNotification(oldManager, employee,
                    employeeName + " has been transferred to " + newManagerName + "'s team.",
                    "/employees/" + employee.getId(), now);
        }
        saveNotification(newManager, employee,
                employeeName + " has been transferred to your team.",
                "/team/" + employee.getId(), now);
        saveNotification(employee, employee,
                "You now report to " + newManagerName + ".",
                "/me/profile", now);

        log.info("ManagerChange id={} applied: employee id={} moved from manager id={} to manager id={}",
                changeId, employee.getId(),
                oldManager != null ? oldManager.getId() : null, newManager.getId());
    }

    /**
     * Writes a single in-app notification row (G-04-adjacent, but for a transfer event rather
     * than a task creation). Not delegated to {@code TaskNotificationService} because that service
     * resolves recipients by {@code OwnerRole} — here we know the exact three recipients.
     *
     * @param recipient  the employee who will see the notification in their bell
     * @param subject    the employee the notification is about
     * @param title      the notification title text
     * @param linkRoute  the frontend route clicking the notification should open
     * @param now        the current timestamp
     */
    private void saveNotification(final Employee recipient, final Employee subject,
                                   final String title, final String linkRoute,
                                   final OffsetDateTime now) {
        final Notification n = new Notification();
        n.setRecipient(recipient);
        n.setEmployee(subject);
        n.setTitle(title);
        n.setLinkRoute(linkRoute);
        n.setCreatedAt(now);
        n.setUpdatedAt(now);
        notificationRepository.save(n);
    }
}
