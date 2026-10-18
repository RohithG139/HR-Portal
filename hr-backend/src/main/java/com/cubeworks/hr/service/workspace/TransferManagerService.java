package com.cubeworks.hr.service.workspace;

import com.cubeworks.hr.common.audit.AuditLogService;
import com.cubeworks.hr.common.error.ApiException;
import com.cubeworks.hr.common.error.ErrorCodes;
import com.cubeworks.hr.dto.workspace.ManagerTaskItemDto;
import com.cubeworks.hr.dto.workspace.ManagerTasksResponse;
import com.cubeworks.hr.dto.workspace.TransferManagerRequest;
import com.cubeworks.hr.dto.workspace.TransferManagerResponse;
import com.cubeworks.hr.employee.AppRole;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.employee.EmployeeRepository;
import com.cubeworks.hr.employee.EmployeeStage;
import com.cubeworks.hr.model.workspace.ManagerChange;
import com.cubeworks.hr.model.workspace.ManagerChangeScope;
import com.cubeworks.hr.model.workspace.ManagerChangeStatus;
import com.cubeworks.hr.model.workspace.ManagerChangeTask;
import com.cubeworks.hr.repository.workspace.ManagerChangeRepository;
import com.cubeworks.hr.repository.workspace.ManagerChangeTaskRepository;
import com.cubeworks.hr.security.SelfActionGuard;
import com.cubeworks.hr.task.EngineTask;
import com.cubeworks.hr.task.EngineTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business logic for the Transfer manager feature (W09/W10, FRD-01 Wave 3).
 *
 * <h2>W09 — Manager tasks tick-list</h2>
 * <p>Returns the open {@code engine_task} rows for a given employee that are currently owned
 * by the employee's current manager. HR uses this list to select which tasks to move to the
 * new manager.</p>
 *
 * <h2>W10 — Create a manager transfer</h2>
 * <p>Validates, inserts one {@code manager_change} row and one {@code manager_change_task} row
 * per ticked task, writes {@code audit_log}, and returns {@code 201 {changeId}}.</p>
 * <p>If the effective date is today (or earlier), the change is applied immediately in an
 * {@code afterCommit} hook — same pattern as {@link com.cubeworks.hr.onboarding.OnboardingService}
 * — so the employee's manager changes the moment the transaction commits. If the effective date is
 * in the future, a scheduler ({@link ManagerTransferScheduler}) picks it up when the date arrives.</p>
 *
 * <h2>Transaction boundary</h2>
 * <p>{@link #createTransfer}: the {@code manager_change} row insert, {@code manager_change_task}
 * row inserts, and the {@code audit_log} entry are atomic. Partial failure (e.g. all manager_change
 * inserts succeed but audit_log insert fails) rolls all of them back — no orphaned transfer row
 * without a matching audit trail. Engine/notification side-effects happen after commit
 * (R-ARC-5 — via {@link ManagerTransferApplier}).</p>
 */
@Service
public class TransferManagerService {

    private static final Logger log = LoggerFactory.getLogger(TransferManagerService.class);

    private final EmployeeRepository employeeRepository;
    private final ManagerChangeRepository managerChangeRepository;
    private final ManagerChangeTaskRepository managerChangeTaskRepository;
    private final EngineTaskRepository engineTaskRepository;
    private final AuditLogService auditLogService;
    private final SelfActionGuard selfActionGuard;
    private final ManagerTransferApplier managerTransferApplier;
    private final Clock clock;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param employeeRepository          for looking up employees and managers
     * @param managerChangeRepository     for persisting {@code manager_change} rows
     * @param managerChangeTaskRepository for persisting {@code manager_change_task} link rows
     * @param engineTaskRepository        for querying open tasks by owner (W09)
     * @param auditLogService             mandatory audit trail (R-API-5 / G-06)
     * @param selfActionGuard             G-13: HR cannot transfer their own manager record
     * @param managerTransferApplier      shared apply logic used by both after-commit hook and scheduler
     * @param clock                       application-wide clock bean
     */
    public TransferManagerService(final EmployeeRepository employeeRepository,
                                   final ManagerChangeRepository managerChangeRepository,
                                   final ManagerChangeTaskRepository managerChangeTaskRepository,
                                   final EngineTaskRepository engineTaskRepository,
                                   final AuditLogService auditLogService,
                                   final SelfActionGuard selfActionGuard,
                                   final ManagerTransferApplier managerTransferApplier,
                                   final Clock clock) {
        this.employeeRepository = employeeRepository;
        this.managerChangeRepository = managerChangeRepository;
        this.managerChangeTaskRepository = managerChangeTaskRepository;
        this.engineTaskRepository = engineTaskRepository;
        this.auditLogService = auditLogService;
        this.selfActionGuard = selfActionGuard;
        this.managerTransferApplier = managerTransferApplier;
        this.clock = clock;
    }

    /**
     * W09 — Returns the open engine tasks for a given employee that are owned by their current
     * manager (used to populate the tick-list in the Transfer manager modal).
     *
     * <p>If the employee has no current manager, returns an empty list — nothing to tick.</p>
     *
     * @param employeeId the employee whose manager tasks to query
     * @param caller     the authenticated HR employee
     * @return response wrapper with the open manager task list
     * @throws ApiException 404 NOT_FOUND if the employee does not exist
     */
    @Transactional(readOnly = true)
    public ManagerTasksResponse getManagerTasks(final Long employeeId, final Employee caller) {
        final Employee employee = findOrThrow(employeeId);

        final Employee currentManager = employee.getManager();
        if (currentManager == null) {
            return new ManagerTasksResponse(List.of());
        }

        // JOIN FETCH is not needed here: we only access id, taskName and dueDate on each task,
        // which are direct columns on engine_task — no lazy associations are traversed.
        final List<ManagerTaskItemDto> tasks =
                engineTaskRepository.findOpenByEmployeeAndOwner(employeeId, currentManager.getId())
                        .stream()
                        .map(t -> new ManagerTaskItemDto(
                                t.getId(),
                                t.getTaskName(),
                                t.getDueDate() != null ? t.getDueDate().toString() : null))
                        .toList();

        return new ManagerTasksResponse(tasks);
    }

    /**
     * W10 — Validates and records a manager transfer request.
     *
     * <p>Validation rules (exact messages from spec §5 W6, R-SRC-3):</p>
     * <ul>
     *   <li>{@code newManagerId} required and must differ from the current manager:
     *       {@code "Choose a new manager."}</li>
     *   <li>{@code effectiveDate} required and not before today:
     *       {@code "The effective date cannot be in the past."}</li>
     * </ul>
     *
     * <p>Transaction: the {@code manager_change} insert, {@code manager_change_task} inserts
     * and the {@code audit_log} entry are committed together. If the effective date is today,
     * {@link ManagerTransferApplier#applyChange(Long)} is called in an {@code afterCommit} hook
     * so the employee's manager changes atomically after the scheduling record is visible (R-ARC-5).
     * A failed apply (e.g. DB error) does not roll back the scheduling record — the scheduler
     * will retry it on the next tick, same resilience model as engine-call failures.</p>
     *
     * @param employeeId the employee to transfer to a new manager
     * @param request    the transfer request body (validated in this method)
     * @param caller     the authenticated HR employee requesting the transfer (G-13 checked)
     * @return 201 response with the new {@code manager_change} id
     * @throws ApiException 400 VALIDATION_FAILED if validation fails
     * @throws ApiException 404 NOT_FOUND if the employee or new manager does not exist
     * @throws ApiException 403 FORBIDDEN if the caller is the subject employee (G-13)
     */
    @Transactional
    public TransferManagerResponse createTransfer(final Long employeeId,
                                                   final TransferManagerRequest request,
                                                   final Employee caller) {
        // G-13: HR cannot act on their own employee record (e.g. transfer their own management)
        selfActionGuard.forbidSelf(caller, employeeId);

        final Employee employee = findOrThrow(employeeId);
        final Employee currentManager = employee.getManager();

        // Fix #5a: cannot transfer an employee who is offboarding or already archived.
        // These employees are in a terminal or near-terminal state and their manager relationship
        // must not be mutated outside the offboarding workflow.
        if (employee.getStage() == EmployeeStage.OFFBOARDING
                || employee.getStage() == EmployeeStage.ARCHIVED) {
            throw new ApiException(ErrorCodes.CONFLICT, HttpStatus.CONFLICT,
                    "Cannot transfer manager for an employee who is offboarding or archived.");
        }

        // Fix #5b: block a second concurrent scheduled transfer for the same employee.
        // Only one SCHEDULED transfer may exist at a time — applying two in parallel would
        // produce unpredictable final state (race between the two apply operations).
        if (managerChangeRepository.existsByEmployeeIdAndStatus(employeeId, ManagerChangeStatus.SCHEDULED)) {
            throw new ApiException(ErrorCodes.CONFLICT, HttpStatus.CONFLICT,
                    "A manager transfer is already scheduled for this employee.");
        }

        // --- Validate newManagerId ---
        if (request.newManagerId() == null) {
            throw ApiException.fieldValidation(
                    List.of(Map.of("field", "newManagerId", "message", "Choose a new manager.")));
        }
        // Must differ from the current manager (null current manager → any manager is valid)
        if (currentManager != null && request.newManagerId().equals(currentManager.getId())) {
            throw ApiException.fieldValidation(
                    List.of(Map.of("field", "newManagerId", "message", "Choose a new manager.")));
        }

        final Employee newManager = employeeRepository.findById(request.newManagerId())
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "New manager with id " + request.newManagerId() + " not found"));

        // Fix #2a: the new manager cannot be the employee themselves (self-manager).
        // This would make the employee their own manager — violates the employee DB CHECK as well.
        if (newManager.getId().equals(employeeId)) {
            throw ApiException.fieldValidation(
                    List.of(Map.of("field", "newManagerId", "message", "Choose a new manager.")));
        }
        // Fix #2b: the new manager must actually be a MANAGER-role employee.
        // Transferring to an HR user, IT admin or regular employee would break the role model.
        if (newManager.getAppRole() != AppRole.MANAGER) {
            throw ApiException.fieldValidation(
                    List.of(Map.of("field", "newManagerId", "message", "Choose a new manager.")));
        }

        // --- Validate effectiveDate ---
        if (request.effectiveDate() == null || request.effectiveDate().isBlank()) {
            throw ApiException.fieldValidation(
                    List.of(Map.of("field", "effectiveDate",
                            "message", "The effective date cannot be in the past.")));
        }
        final LocalDate effectiveDate;
        try {
            effectiveDate = LocalDate.parse(request.effectiveDate());
        } catch (final Exception ex) {
            throw ApiException.fieldValidation(
                    List.of(Map.of("field", "effectiveDate",
                            "message", "The effective date cannot be in the past.")));
        }
        final LocalDate today = LocalDate.now(clock);
        if (effectiveDate.isBefore(today)) {
            throw ApiException.fieldValidation(
                    List.of(Map.of("field", "effectiveDate",
                            "message", "The effective date cannot be in the past.")));
        }

        // --- Fix #1: validate taskIdsToMove against the actual open tasks for this employee
        // owned by the current manager (IDOR guard — any engine_task id could otherwise be sent).
        // Deduplication first, then allowlist check. Validation happens BEFORE persisting any row
        // so that an invalid request leaves no partial write (fail fast).
        final List<Long> rawTaskIds = request.taskIdsToMove() != null ? request.taskIdsToMove() : List.of();
        // Deduplicate while preserving encounter order (LinkedHashSet).
        final List<Long> taskIds = new ArrayList<>(new LinkedHashSet<>(rawTaskIds));
        if (!taskIds.isEmpty()) {
            // Build the allowlist from tasks actually visible via W09 for this employee/manager pair.
            // If the employee has no current manager, no task id is valid.
            final Set<Long> allowedTaskIds;
            if (currentManager != null) {
                allowedTaskIds = engineTaskRepository
                        .findOpenByEmployeeAndOwner(employeeId, currentManager.getId())
                        .stream()
                        .map(EngineTask::getId)
                        .collect(Collectors.toSet());
            } else {
                allowedTaskIds = Set.of();
            }
            for (final Long taskId : taskIds) {
                if (!allowedTaskIds.contains(taskId)) {
                    throw ApiException.fieldValidation(
                            List.of(Map.of("field", "taskIdsToMove",
                                    "message", "One or more selected tasks are no longer available to move.")));
                }
            }
        }

        // --- Insert manager_change ---
        final OffsetDateTime now = OffsetDateTime.now(clock);
        final ManagerChange change = new ManagerChange();
        change.setScope(ManagerChangeScope.LINE);
        change.setEmployee(employee);
        change.setFromManager(currentManager);
        change.setToManager(newManager);
        change.setEffectiveDate(effectiveDate);
        change.setReason(request.reason());
        change.setStatus(ManagerChangeStatus.SCHEDULED);
        change.setRequestedBy(caller);
        change.setCreatedAt(now);
        change.setCreatedBy(caller.getId());
        change.setUpdatedAt(now);
        change.setUpdatedBy(caller.getId());
        final ManagerChange saved = managerChangeRepository.save(change);

        // --- Insert manager_change_task rows for the deduplicated, validated ticked tasks ---
        for (final Long taskId : taskIds) {
            managerChangeTaskRepository.save(new ManagerChangeTask(saved.getId(), taskId));
        }

        // --- Audit log (R-API-5 / G-06) — inside the same transaction ---
        // Atomicity: the manager_change row, manager_change_task rows and audit_log entry are
        // committed together. A failure in any of them rolls all three back — no scheduling record
        // without a matching audit trail.
        auditLogService.record(employee, "MANAGER_CHANGE", saved.getId(), "SCHEDULED",
                caller, null,
                "{\"newManagerId\":" + newManager.getId()
                        + ",\"effectiveDate\":\"" + effectiveDate + "\"}");

        log.info("Manager transfer scheduled: employee id={} from manager id={} to manager id={}, effective={}",
                employeeId, currentManager != null ? currentManager.getId() : null,
                newManager.getId(), effectiveDate);

        final Long changeId = saved.getId();

        // --- If effective date is today (or earlier), apply immediately after commit ---
        // (R-ARC-5: engine/notification side-effects happen after the business transaction commits.
        // The ManagerTransferApplier is a Spring bean whose applyChange method is @Transactional,
        // so it starts a new transaction in the afterCommit callback, which runs outside any
        // active transaction. A failure here logs but does NOT roll back the scheduling record —
        // the scheduler will retry on its next tick.)
        if (!effectiveDate.isAfter(today)) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        managerTransferApplier.applyChange(changeId);
                    } catch (final Exception ex) {
                        log.error("Immediate apply of manager transfer changeId={} failed after commit; "
                                + "scheduler will retry on next tick", changeId, ex);
                    }
                }
            });
        }

        return new TransferManagerResponse(changeId);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Returns the employee by ID, throwing 404 if not found.
     *
     * @param id the employee surrogate PK
     * @return the employee entity
     * @throws ApiException 404 NOT_FOUND if no employee exists with this id
     */
    private Employee findOrThrow(final Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Employee " + id + " not found"));
    }
}
