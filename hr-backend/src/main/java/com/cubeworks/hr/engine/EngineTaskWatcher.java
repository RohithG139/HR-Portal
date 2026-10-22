package com.cubeworks.hr.engine;

import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.employee.EmployeeRepository;
import com.cubeworks.hr.notification.TaskNotificationService;
import com.cubeworks.hr.onboarding.Onboarding;
import com.cubeworks.hr.onboarding.OnboardingRepository;
import com.cubeworks.hr.onboarding.OnboardingService;
import org.springframework.web.client.HttpClientErrorException;
import com.cubeworks.hr.settings.OwnerRole;
import com.cubeworks.hr.task.EngineTask;
import com.cubeworks.hr.task.EngineTaskRepository;
import com.cubeworks.hr.task.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Polls the live CaseFabric engine to reconcile {@code engine_task} with reality (added
 * 2026-10-16, Wave 1 — CLAUDE.md's repo layout names this file under {@code engine/} as
 * "task watcher"; it was a scaffold placeholder until now).
 *
 * <h2>Why this is needed — two distinct problems, one poller</h2>
 * <ol>
 *   <li><strong>Placeholder task ids.</strong> {@code OnboardingService.createEngineTaskInNewTx}
 *       must write its {@code engine_task} row inside the same business transaction that creates
 *       the employee/contract/onboarding rows (R-ARC-5 — the DB row is business data, committed
 *       before the engine is ever called). At that point the real engine case does not exist yet
 *       (it is started in {@code afterCommit}), so the engine has not assigned a real task id —
 *       Spring has no choice but to write a placeholder (a random UUID) and fix it up later. A
 *       real {@code completeTask} call against that placeholder 404s (confirmed live, 2026-10-16:
 *       "A task with id '...' cannot be found" — invisible before this wave because
 *       {@code NoOpCaseEngineClient} never validated the id against anything).</li>
 *   <li><strong>Engine-raised tasks Spring never created a row for.</strong> "Remaining setup" is
 *       raised by a CMMN sentry inside the engine itself (when {@code Onboarding.itSetupComplete}
 *       becomes false after "Equipment request" completes), not by a direct Spring action — no
 *       {@code OnboardingService} call site creates its {@code engine_task} row at all.</li>
 * </ol>
 *
 * <p>Both are the same underlying operation: compare each open case's real task list (the engine's
 * own source of truth) against what {@code engine_task} currently has, and fix up or insert rows
 * to match. One poll loop handles both.</p>
 *
 * <h2>Why polling, not a push/webhook</h2>
 * <p>The engine has no outbound webhook mechanism in this version — polling (same pattern as
 * {@code UndoWindowScheduler} and {@code OverdueReminderScheduler}) is the only option, and the
 * known query-side projection lag (confirmed in the original engine spike, roughly 1-2 seconds
 * for a case-file write; case creation can take a little longer) makes a short delay before a
 * task becomes queryable expected behaviour, not a bug — another reason this must be an
 * asynchronous poller rather than a synchronous lookup immediately after {@code startCase}.</p>
 */
@Component
public class EngineTaskWatcher {

    private static final Logger log = LoggerFactory.getLogger(EngineTaskWatcher.class);

    private final EngineTaskRepository engineTaskRepository;
    private final EmployeeRepository employeeRepository;
    private final OnboardingRepository onboardingRepository;
    private final CaseEngineClient caseEngineClient;
    private final TaskNotificationService taskNotificationService;
    private final OnboardingService onboardingService;
    private final Clock clock;

    public EngineTaskWatcher(final EngineTaskRepository engineTaskRepository,
                              final EmployeeRepository employeeRepository,
                              final OnboardingRepository onboardingRepository,
                              final CaseEngineClient caseEngineClient,
                              final TaskNotificationService taskNotificationService,
                              final OnboardingService onboardingService,
                              final Clock clock) {
        this.onboardingService = onboardingService;
        this.engineTaskRepository = engineTaskRepository;
        this.employeeRepository = employeeRepository;
        this.onboardingRepository = onboardingRepository;
        this.caseEngineClient = caseEngineClient;
        this.taskNotificationService = taskNotificationService;
        this.clock = clock;
    }

    /**
     * Polls every 10 seconds. Groups open {@code engine_task} rows by case (an employee's
     * {@code employee_code} is always the case id, per {@code 01-overview.md} §2's "golden
     * rules") so a case with multiple open tasks costs one engine query, not one per task.
     */
    @Scheduled(fixedDelay = 10_000)
    public void reconcile() {
        final List<EngineTask> openTasks = engineTaskRepository.findAllOpen();
        openTasks.stream()
                .map(t -> t.getEmployee().getEmployeeCode())
                .distinct()
                .forEach(caseId -> {
                    // One case's failure (engine error, optimistic-lock conflict) must not skip
                    // the remaining cases this tick; it is retried on the next poll.
                    try {
                        reconcileCase(caseId);
                    } catch (final Exception ex) {
                        log.error("Task reconcile failed for caseId={}; retrying next poll", caseId, ex);
                    }
                });
    }

    /**
     * Reconciles one case's open tasks against the engine's real task list.
     *
     * @param caseId the engine case id (= {@code employee.employee_code})
     */
    @Transactional
    public void reconcileCase(final String caseId) {
        final Employee employee = employeeRepository.findByEmployeeCode(caseId).orElse(null);
        if (employee == null) {
            return;
        }

        final List<Map<String, Object>> engineTasks;
        try {
            engineTasks = caseEngineClient.findOpenTasks(caseId);
        } catch (final HttpClientErrorException.NotFound missingCase) {
            // The case start never reached the engine (R-ARC-5: retry, never lose it silently).
            onboardingService.restartMissingCase(caseId);
            return;
        }
        if (!engineTasks.isEmpty() && employee.getCaseId() == null) {
            // employee.case_id caches "the engine has this case"; the engine just confirmed it.
            employee.setCaseId(caseId);
            employeeRepository.save(employee);
        }
        if (engineTasks.isEmpty()) {
            // Either the case isn't queryable yet (projection lag) or genuinely has no open
            // tasks right now — either way, nothing to reconcile this poll.
            return;
        }

        final List<EngineTask> dbTasks = engineTaskRepository.findOpenByEmployeeId(employee.getId());
        final OffsetDateTime now = OffsetDateTime.now(clock);

        // 1) Fix up placeholder ids for tasks Spring already knows about.
        for (final EngineTask dbTask : dbTasks) {
            findByName(engineTasks, dbTask.getTaskName())
                    .map(t -> (String) t.get("id"))
                    .filter(realId -> realId != null && !realId.equals(dbTask.getEngineTaskId()))
                    .ifPresent(realId -> {
                        log.info("Resolved real engine task id for '{}' (employee {}): {} -> {}",
                                dbTask.getTaskName(), employee.getId(), dbTask.getEngineTaskId(), realId);
                        dbTask.setEngineTaskId(realId);
                        dbTask.setUpdatedAt(now);
                        engineTaskRepository.save(dbTask);
                    });
        }

        // 2) Discover tasks the engine raised on its own (e.g. "Remaining setup") that have no
        //    engine_task row yet at all.
        final Onboarding onboarding = onboardingRepository.findByEmployeeId(employee.getId()).orElse(null);
        for (final Map<String, Object> engineTask : engineTasks) {
            final String taskName = (String) engineTask.get("taskName");
            final boolean alreadyMirrored = dbTasks.stream()
                    .anyMatch(t -> t.getTaskName().equals(taskName));
            if (alreadyMirrored || onboarding == null) {
                continue;
            }
            final String ownerRoleForTask = ownerRoleFor(taskName);
            if (ownerRoleForTask == null) {
                // A task name this app doesn't know how to route/own — not ours to mirror.
                continue;
            }

            final EngineTask newTask = new EngineTask();
            newTask.setEngineTaskId((String) engineTask.get("id"));
            newTask.setEmployee(employee);
            newTask.setTaskName(taskName);
            final String dueDateStr = (String) engineTask.get("dueDate");
            newTask.setDueDate(dueDateStr != null && !dueDateStr.isBlank()
                    ? LocalDate.parse(dueDateStr) : LocalDate.now(clock));
            if (TaskService.TASK_CREATE_DEVELOPMENT_PLAN.equals(taskName)) {
                // FRD-04's development_plan row doesn't exist yet; F05 links it.
                newTask.setRelatedEntity("DEVELOPMENT_PLAN");
                newTask.setOwnerEmployee(employee);
            } else {
                newTask.setRelatedEntity("ONBOARDING");
                newTask.setRelatedId(onboarding.getId());
            }
            newTask.setCreatedAt(now);
            newTask.setUpdatedAt(now);
            newTask.setNotifiedAt(now);
            engineTaskRepository.save(newTask);
            log.info("Discovered engine-raised task '{}' for employee {} (engine task id={})",
                    taskName, employee.getId(), newTask.getEngineTaskId());

            taskNotificationService.notifyTaskAssigned(
                    OwnerRole.valueOf(ownerRoleForTask), employee, taskName,
                    routeFor(ownerRoleForTask, employee.getId()));
        }
    }

    private Optional<Map<String, Object>> findByName(final List<Map<String, Object>> tasks, final String name) {
        return tasks.stream().filter(t -> name.equals(t.get("taskName"))).findFirst();
    }

    /**
     * Owner role for an engine-raised task name this app knows about. Mirrors
     * {@code OnboardingService}'s own task-name-to-role mapping for the two Spring-created task
     * names; "Remaining setup" is the one task name only the engine itself ever raises.
     *
     * @return the {@link OwnerRole} name, or {@code null} if this task name isn't one this app owns
     */
    private String ownerRoleFor(final String taskName) {
        return switch (taskName) {
            case "Onboarding checklist" -> "HR";
            case "Equipment request", "Remaining setup" -> "IT_ADMIN";
            // Raised by the engine when "Active employment" starts (FRD-02 §2, FRD-04 D-04-4).
            // "Onboarding overdue", "Probation review" and "Approve end of employment" are
            // always mirrored by Spring when it raises them, so only their ids are resolved here.
            case TaskService.TASK_CREATE_DEVELOPMENT_PLAN -> "EMPLOYEE";
            default -> null;
        };
    }

    /** Mirrors {@code OnboardingService.routeFor} — see that method's doc for why not shared. */
    private String routeFor(final String ownerRole, final Long employeeId) {
        return switch (ownerRole) {
            case "HR" -> "/employees/" + employeeId + "/onboarding";
            case "IT_ADMIN" -> "/it/equipment-requests/" + employeeId;
            // FRD-04 Screen 2 route (built by F05)
            case "EMPLOYEE" -> "/me/development/plan";
            default -> null;
        };
    }
}
