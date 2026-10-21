package com.cubeworks.hr.task;

import com.cubeworks.hr.common.error.ApiException;
import com.cubeworks.hr.common.error.ErrorCodes;
import com.cubeworks.hr.employee.AppRole;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.employee.EmployeeRepository;
import com.cubeworks.hr.onboarding.ChecklistItem;
import com.cubeworks.hr.onboarding.ChecklistItemRepository;
import com.cubeworks.hr.onboarding.ChecklistPhase;
import com.cubeworks.hr.settings.OwnerRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Business logic for the My Tasks screen (A36, A37) and task count badge (bell area, R-UI-3).
 *
 * <h2>Task sources</h2>
 * <p>Tasks come from two sources, merged by role:</p>
 * <ul>
 *   <li><strong>Engine tasks</strong> ({@code hr.engine_task}): HR's "Onboarding checklist" task;
 *       IT admin's "Equipment request" and "Remaining setup" tasks. These are completed via the
 *       undo window (R-API-6).</li>
 *   <li><strong>Checklist items</strong> ({@code hr.checklist_item}): IT_ADMIN-owned items appear
 *       in the IT admin's task list; EMPLOYEE-owned items appear in the employee's task list.</li>
 * </ul>
 *
 * <h2>Undo window (R-API-6)</h2>
 * <p>Completing an engine task sets {@code pending_status = PENDING}. The task remains "open"
 * until the scheduler executes it (setting {@code completed_at}). The count therefore does not
 * decrease until the undo window expires.</p>
 *
 * <h2>Due-state colours (R-UI-6)</h2>
 * <ul>
 *   <li>Overdue ({@code dueDate < today}) → {@code "OVERDUE"}</li>
 *   <li>Due soon ({@code dueDate within 3 days}) → {@code "DUE_SOON"}</li>
 *   <li>Otherwise → {@code "UPCOMING"}</li>
 * </ul>
 */
@Service
public class TaskService {

    private final EngineTaskRepository engineTaskRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final EmployeeRepository employeeRepository;
    private final Clock clock;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param engineTaskRepository    repository for engine-side tasks
     * @param checklistItemRepository repository for checklist item tasks
     * @param clock                   application-wide clock bean
     */
    public TaskService(final EngineTaskRepository engineTaskRepository,
                       final ChecklistItemRepository checklistItemRepository,
                       final EmployeeRepository employeeRepository,
                       final Clock clock) {
        this.engineTaskRepository = engineTaskRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.employeeRepository = employeeRepository;
        this.clock = clock;
    }

    // ── A36 My Tasks list ──────────────────────────────────────────────────────

    /**
     * Returns the task list for the calling user's role (A36).
     *
     * <p>No transaction boundary needed: all reads are independent queries; no data is modified.
     * Uses {@code @Transactional(readOnly = true)} for Hibernate session consistency and
     * potential read-replica routing.</p>
     *
     * @param caller the authenticated employee
     * @param module optional module filter (e.g. "ONBOARDING"); null = all modules
     * @param q      optional free-text search on task title; null = no filter
     * @param page   zero-based page index (unused in gate 2; full pagination in gate 4)
     * @return task page DTO with {@code content} array
     */
    @Transactional(readOnly = true)
    public TaskPageDto getMyTasks(final Employee caller,
                                   final String module,
                                   final String q,
                                   final int page) {
        final List<TaskItemDto> items = new ArrayList<>();
        final LocalDate today = LocalDate.now(clock);
        // created_by id -> display name; few distinct creators, so look each up once
        final Map<Long, String> creatorNames = new HashMap<>();

        // -- Engine tasks: by role ------------------------------------------------
        // G-13: HR/IT never see their own onboarding's tasks in their own HR/IT queue —
        // otherwise an HR or IT_ADMIN new hire could complete their own onboarding/equipment
        // tasks. (An EMPLOYEE's own tasks, below, are legitimately their own to see.)
        if (caller.getAppRole() == AppRole.HR) {
            // HR sees all open "Onboarding checklist" engine tasks (completedAt IS NULL)
            engineTaskRepository.findOpenByTaskName("Onboarding checklist").stream()
                    .filter(t -> t.getCompletedAt() == null)
                    .filter(t -> t.getEmployee() == null || !caller.getId().equals(t.getEmployee().getId()))
                    .map(t -> toTaskItem(t, today, creatorNames))
                    .forEach(items::add);
        } else if (caller.getAppRole() == AppRole.IT_ADMIN) {
            // IT admin sees open Equipment request and Remaining setup engine tasks
            engineTaskRepository.findOpenByTaskName("Equipment request").stream()
                    .filter(t -> t.getCompletedAt() == null)
                    .filter(t -> t.getEmployee() == null || !caller.getId().equals(t.getEmployee().getId()))
                    .map(t -> toTaskItem(t, today, creatorNames))
                    .forEach(items::add);
            engineTaskRepository.findOpenByTaskName("Remaining setup").stream()
                    .filter(t -> t.getCompletedAt() == null)
                    .filter(t -> t.getEmployee() == null || !caller.getId().equals(t.getEmployee().getId()))
                    .map(t -> toTaskItem(t, today, creatorNames))
                    .forEach(items::add);
        }

        // -- Checklist items: by role ---------------------------------------------
        if (caller.getAppRole() == AppRole.IT_ADMIN) {
            // IT admin sees all open IT_ADMIN-owned checklist items across all employees,
            // except their own (G-13, same reasoning as the engine tasks above).
            checklistItemRepository
                    .findAllOpenByPhaseAndOwnerRole(ChecklistPhase.ONBOARDING, OwnerRole.IT_ADMIN)
                    .stream()
                    .filter(ci -> ci.getEmployee() == null || !caller.getId().equals(ci.getEmployee().getId()))
                    .map(ci -> toTaskItem(ci, today, creatorNames))
                    .forEach(items::add);
        } else if (caller.getAppRole() == AppRole.EMPLOYEE) {
            // Employee sees their own EMPLOYEE-owned open items
            checklistItemRepository
                    .findByEmployeeIdAndPhaseAndOwnerRole(caller.getId(),
                            ChecklistPhase.ONBOARDING, OwnerRole.EMPLOYEE)
                    .stream()
                    .filter(ci -> ci.getStatus().name().equals("OPEN"))
                    .map(ci -> toTaskItem(ci, today, creatorNames))
                    .forEach(items::add);
        } else if (caller.getAppRole() == AppRole.MANAGER) {
            // Manager sees their own open MANAGER-owned orientation items across their direct
            // reports (OPEN-01, S7M, FRD-02 §3 step 7 "HR and the manager complete orientation
            // items"). No manager-specific engine tasks are defined (D-08).
            checklistItemRepository
                    .findOpenManagerItemsForManager(ChecklistPhase.ONBOARDING, caller.getId())
                    .stream()
                    .map(ci -> toTaskItem(ci, today, creatorNames))
                    .forEach(items::add);
        }

        // Apply module filter
        List<TaskItemDto> filtered = items;
        if (module != null && !module.isBlank()) {
            filtered = items.stream()
                    .filter(t -> module.equalsIgnoreCase(t.module()))
                    .toList();
        }
        // Apply free-text search
        if (q != null && !q.isBlank()) {
            final String lower = q.toLowerCase();
            filtered = filtered.stream()
                    .filter(t -> t.title() != null && t.title().toLowerCase().contains(lower))
                    .toList();
        }

        return new TaskPageDto(filtered, filtered.size());
    }

    // ── A36 Task count badge ───────────────────────────────────────────────────

    /**
     * Returns the number of open tasks for the calling user's role (badge count in R-UI-3).
     *
     * <p>Read-only: counts are derived from the same query as the list, then aggregated.
     * Not {@code @Transactional} to avoid keeping a session open for a simple count.</p>
     *
     * @param caller the authenticated employee
     * @return number of open tasks
     */
    @Transactional(readOnly = true)
    public long countMyTasks(final Employee caller) {
        return getMyTasks(caller, null, null, 0).totalElements();
    }

    // ── A37 Undo task ─────────────────────────────────────────────────────────

    /**
     * Cancels a pending engine task completion within the undo window (A37, R-API-6).
     *
     * <p>Atomicity: the {@code pending_status} update to UNDONE must commit together with the
     * audit trail. If the engine_task row cannot be updated (e.g. already EXECUTED by the scheduler
     * in a race), the caller sees 409 UNDO_EXPIRED — this is correct because the business effect
     * has already been committed.</p>
     *
     * @param taskId the engine_task surrogate PK (returned by complete endpoints as the DB id)
     * @param caller the authenticated employee attempting the undo
     * @throws ApiException 409 UNDO_EXPIRED if the window has expired or task is not PENDING
     * @throws ApiException 403 FORBIDDEN if the caller did not submit the pending completion
     */
    @Transactional
    public void undoTask(final Long taskId, final Employee caller) {
        final EngineTask task = engineTaskRepository.findById(taskId)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Task " + taskId + " not found"));

        // Must be PENDING to undo (R-API-6)
        if (task.getPendingStatus() != PendingStatus.PENDING) {
            throw new ApiException(ErrorCodes.UNDO_EXPIRED, HttpStatus.CONFLICT,
                    "The undo window has expired. The task has already been completed.");
        }

        // Ownership check (G-13): only the user who submitted the pending completion can undo
        if (task.getPendingBy() == null || !task.getPendingBy().getId().equals(caller.getId())) {
            throw new ApiException(ErrorCodes.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Only the user who submitted the task completion can undo it.");
        }

        final OffsetDateTime now = OffsetDateTime.now(clock);

        // Undo window expiry check (must come AFTER ownership check to prevent timing attacks)
        if (task.getPendingExecuteAt() != null && !task.getPendingExecuteAt().isAfter(now)) {
            throw new ApiException(ErrorCodes.UNDO_EXPIRED, HttpStatus.CONFLICT,
                    "The undo window has expired. The task has already been completed.");
        }

        // Cancel the pending completion
        task.setPendingStatus(PendingStatus.UNDONE);
        task.setUpdatedAt(now);
        task.setUpdatedBy(caller.getId());
        engineTaskRepository.save(task);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Converts an engine task to a task item DTO.
     *
     * @param t     the engine task
     * @param today today's date for due-state calculation
     * @return the task item DTO
     */
    private TaskItemDto toTaskItem(final EngineTask t, final LocalDate today,
                                   final Map<Long, String> creatorNames) {
        return new TaskItemDto(
                t.getId(),
                "ONBOARDING",
                t.getTaskName(),
                t.getEmployee() != null ? t.getEmployee().getId() : null,
                t.getDueDate() != null ? t.getDueDate().toString() : null,
                computeDueState(t.getDueDate(), today),
                fullName(t.getEmployee()),
                creatorName(t.getCreatedBy(), creatorNames)
        );
    }

    /**
     * Converts a checklist item to a task item DTO.
     *
     * @param ci    the checklist item
     * @param today today's date for due-state calculation
     * @return the task item DTO
     */
    private TaskItemDto toTaskItem(final ChecklistItem ci, final LocalDate today,
                                   final Map<Long, String> creatorNames) {
        return new TaskItemDto(
                ci.getId(),
                "ONBOARDING",
                ci.getName(),
                ci.getEmployee() != null ? ci.getEmployee().getId() : null,
                ci.getDueDate() != null ? ci.getDueDate().toString() : null,
                computeDueState(ci.getDueDate(), today),
                fullName(ci.getEmployee()),
                creatorName(ci.getCreatedBy(), creatorNames)
        );
    }

    /** "First Last", or null when there is no employee. */
    private static String fullName(final Employee e) {
        return e != null ? e.getFirstName() + " " + e.getLastName() : null;
    }

    /**
     * Name of the employee who created a task, cached per request; null for system-created
     * tasks (created_by is null for engine callbacks and template-instantiated items).
     */
    private String creatorName(final Long createdBy, final Map<Long, String> creatorNames) {
        if (createdBy == null) {
            return null;
        }
        return creatorNames.computeIfAbsent(createdBy, id -> employeeRepository.findById(id)
                .map(TaskService::fullName)
                .orElse(null));
    }

    /**
     * Computes the due-state string for a task (R-UI-6).
     *
     * @param dueDate the task's due date (may be null for tasks with no deadline)
     * @param today   today's date
     * @return "OVERDUE", "DUE_SOON" (within 3 days), or "UPCOMING"
     */
    private String computeDueState(final LocalDate dueDate, final LocalDate today) {
        if (dueDate == null) return "UPCOMING";
        if (dueDate.isBefore(today)) return "OVERDUE";
        if (!dueDate.isAfter(today.plusDays(3))) return "DUE_SOON";
        return "UPCOMING";
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    /**
     * A single item in the My Tasks list.
     *
     * @param taskId     surrogate PK (engine_task.id for engine tasks; checklist_item.id for items)
     * @param module     feature module this task belongs to (e.g. "ONBOARDING")
     * @param title      the task name or checklist item name
     * @param employeeId the employee this task is associated with
     * @param dueDate    ISO date string for the due date (null = no deadline)
     * @param dueState   "OVERDUE", "DUE_SOON", or "UPCOMING" (R-UI-6)
     */
    public record TaskItemDto(Long taskId, String module, String title,
                               Long employeeId, String dueDate, String dueState,
                               // My Tasks "Employee" and "Raised by" columns; raisedBy is
                               // null when the task was created by the system (no created_by)
                               String employeeName, String raisedBy) {}

    /**
     * Paginated wrapper for the My Tasks list.
     *
     * @param content       the list of task items on this page
     * @param totalElements total number of tasks across all pages
     */
    public record TaskPageDto(java.util.List<TaskItemDto> content, long totalElements) {}
}
