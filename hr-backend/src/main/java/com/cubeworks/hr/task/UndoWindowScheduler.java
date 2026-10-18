package com.cubeworks.hr.task;

import com.cubeworks.hr.common.outbox.EmailOutboxService;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.employee.EmployeeRepository;
import com.cubeworks.hr.employee.EmployeeStage;
import com.cubeworks.hr.engine.CaseEngineClient;
import com.cubeworks.hr.onboarding.Onboarding;
import com.cubeworks.hr.onboarding.OnboardingRepository;
import com.cubeworks.hr.onboarding.OnboardingStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Polls for expired undo-window completions and submits them to the CaseFabric engine.
 *
 * <h2>Undo-window flow (R-API-6, G-03)</h2>
 * <ol>
 *   <li>When a user "completes" a task (e.g. POST /api/onboardings/{id}/complete), the service
 *       sets {@code engine_task.pending_status = PENDING} and returns {@code 202 {undoUntil}}.</li>
 *   <li>This scheduler runs every 2 seconds and finds expired-PENDING rows
 *       ({@code pending_execute_at <= now}).</li>
 *   <li>For each row it calls the engine, marks the row {@code EXECUTED}, and applies any
 *       post-commit side effects (e.g. setting {@code employee.stage = ACTIVE} after
 *       onboarding completes).</li>
 * </ol>
 *
 * <h2>Transaction boundary</h2>
 * <p>Each processed row is committed in its own transaction. If the engine call fails the row
 * is marked {@link PendingStatus#FAILED} and will be retried on the next scheduler tick.
 * Partial failure: one failing row does not block other rows in the same poll cycle.</p>
 *
 * <h2>Self-invocation and Spring AOP</h2>
 * <p>{@code processExpiredPending} must call {@code processSingleTask} through the Spring proxy
 * so that the {@code @Transactional} annotation on {@code processSingleTask} is applied. Calling
 * {@code this.processSingleTask(...)} bypasses the proxy — the transaction is never opened and
 * the loaded {@code EngineTask} entity becomes detached, causing
 * {@code LazyInitializationException} when {@code task.getEmployee()} is accessed.</p>
 *
 * <p>Fix: inject a {@code @Lazy} self-reference ({@code self}) and call
 * {@code self.processSingleTask(...)} instead. The {@code @Lazy} annotation breaks the circular
 * dependency that would otherwise prevent the context from starting.</p>
 *
 * <h2>Virtual-thread safety</h2>
 * <p>Spring Boot 3.2+ runs {@code @Scheduled} tasks on virtual threads when
 * {@code spring.threads.virtual.enabled=true}. This scheduler uses only
 * repository calls (no {@code synchronized} blocks), so there is no pinning risk.</p>
 */
@Component
public class UndoWindowScheduler {

    private static final Logger log = LoggerFactory.getLogger(UndoWindowScheduler.class);

    private final EngineTaskRepository engineTaskRepository;
    private final EmployeeRepository employeeRepository;
    private final OnboardingRepository onboardingRepository;
    private final EmailOutboxService emailOutboxService;
    private final CaseEngineClient caseEngineClient;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    /**
     * Self-reference injected lazily so that {@code processExpiredPending} can call
     * {@code processSingleTask} through the Spring proxy rather than on {@code this}.
     * Without this, the {@code @Transactional} on {@code processSingleTask} is never
     * applied (classic self-invocation bypass).
     *
     * <p>Setter injection is used instead of constructor injection because Spring cannot
     * resolve a circular dependency at construction time even with {@code @Lazy}.</p>
     */
    private UndoWindowScheduler self;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param engineTaskRepository repository for reading/updating engine tasks
     * @param employeeRepository   repository for updating employee stage after onboarding
     * @param onboardingRepository repository for marking onboarding COMPLETED (D-15)
     * @param emailOutboxService   queues the "Onboarding completed" e-mail (D-21)
     * @param caseEngineClient     CaseFabric engine client
     * @param clock                application-wide clock bean
     * @param objectMapper         deserializes {@code engine_task.pending_output} into the
     *                             map the engine's "complete task" call expects
     */
    public UndoWindowScheduler(final EngineTaskRepository engineTaskRepository,
                                final EmployeeRepository employeeRepository,
                                final OnboardingRepository onboardingRepository,
                                final EmailOutboxService emailOutboxService,
                                final CaseEngineClient caseEngineClient,
                                final Clock clock,
                                final ObjectMapper objectMapper) {
        this.engineTaskRepository = engineTaskRepository;
        this.employeeRepository = employeeRepository;
        this.onboardingRepository = onboardingRepository;
        this.emailOutboxService = emailOutboxService;
        this.caseEngineClient = caseEngineClient;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    /**
     * Injects the Spring proxy of this bean so that {@code processExpiredPending} can
     * call {@code processSingleTask} through it. {@code @Lazy} defers resolution until
     * first use, breaking the circular bean-creation dependency.
     *
     * @param self the Spring proxy of this scheduler bean
     */
    @Autowired
    public void setSelf(@Lazy final UndoWindowScheduler self) {
        this.self = self;
    }

    /**
     * Polls every 2 seconds for expired-PENDING engine tasks and processes them.
     *
     * <p>Transaction boundary: each task is processed in its own {@code @Transactional} call
     * ({@link #processSingleTask}) so that a failing task does not roll back the others.
     * The scheduler method itself is intentionally not {@code @Transactional}.
     * Calls go through {@code self} (the Spring proxy) so that {@code @Transactional}
     * on {@code processSingleTask} is properly intercepted.</p>
     */
    @Scheduled(fixedDelay = 2_000)
    public void processExpiredPending() {
        final OffsetDateTime now = OffsetDateTime.now(clock);
        final List<EngineTask> expired = engineTaskRepository.findExpiredPending(now);
        for (final EngineTask task : expired) {
            try {
                // Call through the Spring proxy so @Transactional on processSingleTask
                // is applied. Direct this.processSingleTask(...) would bypass the proxy
                // and leave the EngineTask detached → LazyInitializationException on
                // task.getEmployee().setStage(...).
                self.processSingleTask(task.getId());
            } catch (final Exception ex) {
                log.error("Failed to process expired pending task id={}; will retry", task.getId(), ex);
            }
        }
    }

    /**
     * Executes one expired-PENDING task: calls the engine, marks it EXECUTED, and applies
     * post-completion side effects.
     *
     * <p>Atomicity: the engine_task status update and any employee stage transition happen
     * in a single transaction. If the engine call fails the task is marked FAILED and the
     * transaction still commits (so the failure is persisted and retried rather than silently
     * lost).</p>
     *
     * @param taskId the numeric PK of the engine_task row
     */
    @Transactional
    public void processSingleTask(final Long taskId) {
        final EngineTask task = engineTaskRepository.findById(taskId).orElse(null);
        if (task == null || task.getPendingStatus() != PendingStatus.PENDING) {
            // Already processed or undone between the poll and this call
            return;
        }

        final OffsetDateTime now = OffsetDateTime.now(clock);

        // Double-check the window has actually expired (guard against race conditions)
        if (task.getPendingExecuteAt().isAfter(now)) {
            return;
        }

        try {
            // Call the engine to complete the task (R-ARC-5). pending_output is stored as a
            // JSON string (DB CHECK: non-null whenever pending_status is non-null) and must be
            // deserialized here — a previous version of this method passed Map.of() instead,
            // silently discarding the real completion payload (e.g. {"completed":true} for the
            // "Onboarding checklist" task), which meant the case-file output binding in the
            // CMMN model never actually ran, even though the business DB looked complete.
            @SuppressWarnings("unchecked")
            final java.util.Map<String, Object> output =
                    objectMapper.readValue(task.getPendingOutput(), java.util.Map.class);
            caseEngineClient.completeTask(task.getEngineTaskId(), output);
            task.setPendingStatus(PendingStatus.EXECUTED);
            task.setCompletedAt(now);
            log.info("Executed engine task id={} name='{}' for employee={}",
                    taskId, task.getTaskName(), task.getEmployee().getId());
        } catch (final Exception ex) {
            log.error("Engine completeTask failed for task id={}; marking FAILED for retry",
                    taskId, ex);
            task.setPendingStatus(PendingStatus.FAILED);
            task.setUpdatedAt(now);
            engineTaskRepository.save(task);
            return;
        }

        task.setUpdatedAt(now);
        engineTaskRepository.save(task);

        // Post-completion side effect: for Onboarding checklist → set stage = ACTIVE (AC-04),
        // mark onboarding COMPLETED (D-15 — this is what makes the untick-lock in
        // OnboardingService.checkNotCompleted actually take effect), and queue the
        // "Onboarding completed" e-mail to the employee and their manager (D-21).
        if ("Onboarding checklist".equals(task.getTaskName())) {
            final Employee emp = task.getEmployee();
            emp.setStage(EmployeeStage.ACTIVE);
            emp.setStageSince(now.toLocalDate());
            emp.setUpdatedAt(now);
            employeeRepository.save(emp);
            log.info("Employee id={} stage set to ACTIVE after onboarding completion", emp.getId());

            onboardingRepository.findByEmployeeId(emp.getId()).ifPresent(onb -> {
                onb.setStatus(OnboardingStatus.COMPLETED);
                onb.setCompletedAt(now);
                onb.setCompletedBy(task.getPendingBy());
                onb.setUpdatedAt(now);
                onboardingRepository.save(onb);
            });

            final String employeeName = emp.getFirstName() + " " + emp.getLastName();
            final String body = "All onboarding items for " + employeeName + " are complete.";
            emailOutboxService.enqueue("ONBOARDING_COMPLETED", emp.getPrivateEmail(),
                    "Onboarding completed for " + employeeName, body, emp);
            if (emp.getManager() != null && emp.getManager().getPrivateEmail() != null) {
                emailOutboxService.enqueue("ONBOARDING_COMPLETED", emp.getManager().getPrivateEmail(),
                        "Onboarding completed for " + employeeName, body, emp);
            }
        }
    }
}
