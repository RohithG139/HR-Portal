package com.cubeworks.hr.service.probation;

import com.cubeworks.hr.common.audit.AuditLogService;
import com.cubeworks.hr.common.error.ApiException;
import com.cubeworks.hr.common.error.ErrorCodes;
import com.cubeworks.hr.common.outbox.EmailOutboxService;
import com.cubeworks.hr.dto.probation.HrDecisionRequest;
import com.cubeworks.hr.dto.probation.ProbationQuestionDto;
import com.cubeworks.hr.dto.probation.ProbationReviewDto;
import com.cubeworks.hr.dto.probation.SaveProbationReviewRequest;
import com.cubeworks.hr.dto.probation.UndoWindowResponse;
import com.cubeworks.hr.employee.AppRole;
import com.cubeworks.hr.employee.Contract;
import com.cubeworks.hr.employee.ContractRepository;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.employee.EmployeeRepository;
import com.cubeworks.hr.employee.EmployeeStage;
import com.cubeworks.hr.engine.CaseEngineClient;
import com.cubeworks.hr.model.probation.EmployeeExit;
import com.cubeworks.hr.model.probation.HrDecision;
import com.cubeworks.hr.model.probation.ProbationOutcome;
import com.cubeworks.hr.model.probation.Review;
import com.cubeworks.hr.model.probation.ReviewAnswer;
import com.cubeworks.hr.model.probation.ReviewAnswerId;
import com.cubeworks.hr.model.probation.ReviewStatus;
import com.cubeworks.hr.model.settings.AppraisalForm;
import com.cubeworks.hr.model.settings.FormAnswerType;
import com.cubeworks.hr.model.settings.FormQuestion;
import com.cubeworks.hr.model.settings.FormSection;
import com.cubeworks.hr.model.settings.FormStatus;
import com.cubeworks.hr.model.settings.FormUsedFor;
import com.cubeworks.hr.notification.Notification;
import com.cubeworks.hr.notification.NotificationRepository;
import com.cubeworks.hr.notification.TaskNotificationService;
import com.cubeworks.hr.onboarding.ChecklistItemRepository;
import com.cubeworks.hr.onboarding.ChecklistItemStatus;
import com.cubeworks.hr.onboarding.ChecklistPhase;
import com.cubeworks.hr.repository.probation.EmployeeExitRepository;
import com.cubeworks.hr.repository.probation.ReviewAnswerRepository;
import com.cubeworks.hr.repository.probation.ReviewRepository;
import com.cubeworks.hr.repository.settings.AppraisalFormRepository;
import com.cubeworks.hr.repository.settings.FormQuestionRepository;
import com.cubeworks.hr.repository.settings.FormSectionRepository;
import com.cubeworks.hr.security.SelfActionGuard;
import com.cubeworks.hr.settings.EmailTemplateRepository;
import com.cubeworks.hr.settings.OwnerRole;
import com.cubeworks.hr.settings.SettingRepository;
import com.cubeworks.hr.task.EngineTask;
import com.cubeworks.hr.task.EngineTaskRepository;
import com.cubeworks.hr.task.PendingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Probation review (FRD-02 §2 "Probation review", §4.7 P01–P04, ONB-08).
 *
 * <h2>Flow</h2>
 * <ol>
 *   <li>{@link #createReviewIfDue} (called by {@link ProbationReviewScheduler}): once
 *       {@code v_contract.probation_end_date − PROBATION_REVIEW days <= today}, creates the
 *       {@code review} (PROBATION, DRAFT), the manager's "Probation review" {@code engine_task},
 *       the G-04 notification and the {@code PROBATION_REVIEW_DUE} e-mail; after commit writes
 *       case file {@code Probation {reviewId, reviewDate}} and starts the plan item (E1 fallback,
 *       no native timer — {@code 03-case-file.md} §1).</li>
 *   <li>P02 saves a draft; P03 submits: {@code review} SUBMITTED + answers, engine task through the
 *       undo window with output {@code {outcome}}. The engine raises "Probation passed" (CONFIRM)
 *       or HR's "Approve end of employment" (END) — R-ARC-1, Spring only reports the fact.</li>
 *   <li>P04 (HR, only when the outcome is END): {@code review.hr_decision}, engine task through the
 *       undo window with output {@code {hrDecision}}. When the window closes,
 *       {@link #onTaskExecuted} writes {@code employee_exit} (PROBATION_NOT_PASSED, SYSTEM) for
 *       APPROVED and moves the stage to OFFBOARDING; REJECTED confirms the employee (spec D-P5:
 *       the engine raises "Probation passed").</li>
 * </ol>
 *
 * <p>Undo (A37) reverts the review fields through {@link #onTaskUndone}.</p>
 */
@Service
public class ProbationReviewService {

    private static final Logger log = LoggerFactory.getLogger(ProbationReviewService.class);

    /** {@code review.review_type} of a probation review. */
    public static final String REVIEW_TYPE = "PROBATION";
    /** Engine task name of the manager's review (R-CM-3: equals the spec's task name). */
    public static final String TASK_PROBATION_REVIEW = "Probation review";
    /** Engine task name of HR's approval of "End employment" (FRD-02 Screen 13). */
    public static final String TASK_APPROVE_END = "Approve end of employment";
    /** {@code engine_task.related_entity} for both probation tasks; related_id = review id. */
    public static final String RELATED_ENTITY = "REVIEW";

    /** FRD-02 §4 validation table, row "Probation summary and outcome" (R-SRC-3, exact). */
    public static final String MSG_SUMMARY_OUTCOME = "Add a summary and choose an outcome.";
    /** FRD-02 §4 generic required-field message, used for each unanswered rating. */
    public static final String MSG_REQUIRED = "This field is required.";
    /** [ASSUMPTION] no FRD wording; 409 when a submitted review is changed (spec §12 D-P8). */
    public static final String MSG_ALREADY_SUBMITTED = "This probation review has already been submitted.";
    /** [ASSUMPTION] no FRD wording; 409 when no HR decision is pending (spec §12 D-P8). */
    public static final String MSG_NOT_AWAITING = "This probation review is not awaiting an HR decision.";

    static final String CODE_ALREADY_SUBMITTED = "REVIEW_ALREADY_SUBMITTED";
    static final String CODE_NOT_AWAITING = "NOT_AWAITING_DECISION";

    private final ReviewRepository reviewRepository;
    private final ReviewAnswerRepository reviewAnswerRepository;
    private final EmployeeExitRepository employeeExitRepository;
    private final ContractRepository contractRepository;
    private final EmployeeRepository employeeRepository;
    private final AppraisalFormRepository appraisalFormRepository;
    private final FormSectionRepository formSectionRepository;
    private final FormQuestionRepository formQuestionRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final EngineTaskRepository engineTaskRepository;
    private final SettingRepository settingRepository;
    private final NotificationRepository notificationRepository;
    private final TaskNotificationService taskNotificationService;
    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailOutboxService emailOutboxService;
    private final AuditLogService auditLogService;
    private final CaseEngineClient caseEngineClient;
    private final SelfActionGuard selfActionGuard;
    private final Clock clock;
    private final String frontendBaseUrl;

    /** Constructor injection (R-CODE-1). */
    public ProbationReviewService(final ReviewRepository reviewRepository,
                                  final ReviewAnswerRepository reviewAnswerRepository,
                                  final EmployeeExitRepository employeeExitRepository,
                                  final ContractRepository contractRepository,
                                  final EmployeeRepository employeeRepository,
                                  final AppraisalFormRepository appraisalFormRepository,
                                  final FormSectionRepository formSectionRepository,
                                  final FormQuestionRepository formQuestionRepository,
                                  final ChecklistItemRepository checklistItemRepository,
                                  final EngineTaskRepository engineTaskRepository,
                                  final SettingRepository settingRepository,
                                  final NotificationRepository notificationRepository,
                                  final TaskNotificationService taskNotificationService,
                                  final EmailTemplateRepository emailTemplateRepository,
                                  final EmailOutboxService emailOutboxService,
                                  final AuditLogService auditLogService,
                                  final CaseEngineClient caseEngineClient,
                                  final SelfActionGuard selfActionGuard,
                                  final Clock clock,
                                  @Value("${cubeworks.frontend-base-url:http://localhost:5173}") final String frontendBaseUrl) {
        this.reviewRepository = reviewRepository;
        this.reviewAnswerRepository = reviewAnswerRepository;
        this.employeeExitRepository = employeeExitRepository;
        this.contractRepository = contractRepository;
        this.employeeRepository = employeeRepository;
        this.appraisalFormRepository = appraisalFormRepository;
        this.formSectionRepository = formSectionRepository;
        this.formQuestionRepository = formQuestionRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.engineTaskRepository = engineTaskRepository;
        this.settingRepository = settingRepository;
        this.notificationRepository = notificationRepository;
        this.taskNotificationService = taskNotificationService;
        this.emailTemplateRepository = emailTemplateRepository;
        this.emailOutboxService = emailOutboxService;
        this.auditLogService = auditLogService;
        this.caseEngineClient = caseEngineClient;
        this.selfActionGuard = selfActionGuard;
        this.clock = clock;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    // ── Routes (hr-frontend router.tsx) ──────────────────────────────────────

    /** Manager's review page (FRD-02 Screen 12 route + the review id it loads). */
    public static String managerRoute(final Employee employee, final Long reviewId) {
        return "/team/" + employee.getEmployeeCode() + "/probation-review?reviewId=" + reviewId;
    }

    /** HR's decision page (FRD-02 Screen 13 route + the review id it loads). */
    public static String hrRoute(final Employee employee, final Long reviewId) {
        return "/employees/" + employee.getEmployeeCode() + "/probation-review/decision?reviewId=" + reviewId;
    }

    // ── Review creation (timer "Probation review") ───────────────────────────

    /** {@code setting.PROBATION_REVIEW}: days before probation end (R-ARC-7; default 14, FRD-08). */
    public int reviewDaysBefore() {
        return settingRepository.findById("PROBATION_REVIEW")
                .map(s -> s.getValueInt() != null ? s.getValueInt() : 14).orElse(14);
    }

    /**
     * Creates the probation review for one contract if it is due and none exists yet.
     *
     * @param contractId the current contract (from {@code v_contract})
     * @param today      the current date
     * @return true when a review was created
     */
    @Transactional
    public boolean createReviewIfDue(final Long contractId, final LocalDate today) {
        final Contract contract = contractRepository.findById(contractId).orElse(null);
        if (contract == null || !contract.isCurrent() || contract.getProbationMonths() == null
                || contract.getProbationMonths() <= 0
                || reviewRepository.existsByContractIdAndReviewType(contractId, REVIEW_TYPE)) {
            return false;
        }
        final Employee employee = contract.getEmployee();
        if (employee.getArchivedAt() != null
                || employee.getStage() == EmployeeStage.OFFBOARDING
                || employee.getStage() == EmployeeStage.ARCHIVED) {
            return false;
        }
        final Employee manager = employee.getManager();
        if (manager == null) {
            // Screen 12 is "the employee's current manager" — nobody to assign it to yet.
            log.warn("Probation review due for employee id={} but no manager is set; retrying later",
                    employee.getId());
            return false;
        }
        final LocalDate probationEnd = probationEndDate(contract);
        final LocalDate reviewDate = probationEnd.minusDays(reviewDaysBefore());
        // Only while probation is still running: a probation that already ended (e.g. an employee
        // imported long after their start date) gets no retroactive review.
        if (reviewDate.isAfter(today) || probationEnd.isBefore(today)) {
            return false;
        }
        final AppraisalForm form = activeProbationForm();
        if (form == null) {
            log.error("No ACTIVE appraisal form with used_for=PROBATION; cannot create probation review");
            return false;
        }

        final OffsetDateTime now = OffsetDateTime.now(clock);
        final Review review = new Review();
        review.setReviewType(REVIEW_TYPE);
        review.setForm(form);
        review.setReviewer(manager);
        review.setContract(contract);
        review.setDueDate(probationEnd);
        review.setStatus(ReviewStatus.DRAFT);
        review.setCreatedAt(now);
        review.setUpdatedAt(now);
        final Review saved = reviewRepository.save(review);

        createEngineTask(employee, manager, TASK_PROBATION_REVIEW, probationEnd, saved.getId(), now);
        notifyManager(employee, manager, saved, now);
        auditLogService.record(employee, "REVIEW", saved.getId(), "PROBATION_REVIEW_CREATED", null,
                null, json(Map.of("reviewId", saved.getId(), "dueDate", probationEnd.toString())));

        final String caseId = employee.getEmployeeCode();
        final Map<String, Object> probationItem = Map.of(
                "reviewId", saved.getId(),
                "reviewDate", reviewDate + "T09:00:00Z");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                driveEngine(caseId, probationItem);
            }
        });
        return true;
    }

    /**
     * Retry for R-ARC-5: for a draft review whose engine task was never seen, either records the
     * engine's real task id (the task exists) or writes the case file and starts the plan item again.
     *
     * @param reviewId the draft review
     */
    @Transactional
    public void reconcileEngine(final Long reviewId) {
        final Review review = reviewRepository.findById(reviewId).orElse(null);
        if (review == null || review.getStatus() != ReviewStatus.DRAFT || review.getEngineTaskId() != null) {
            return;
        }
        final Employee employee = review.getContract().getEmployee();
        final String caseId = employee.getEmployeeCode();
        final String realId = caseEngineClient.findOpenTasks(caseId).stream()
                .filter(t -> TASK_PROBATION_REVIEW.equals(t.get("taskName")))
                .map(t -> (String) t.get("id"))
                .findFirst().orElse(null);
        if (realId != null) {
            review.setEngineTaskId(realId);
            review.setUpdatedAt(OffsetDateTime.now(clock));
            reviewRepository.save(review);
            return;
        }
        final LocalDate reviewDate = review.getDueDate().minusDays(reviewDaysBefore());
        driveEngine(caseId, Map.of("reviewId", review.getId(), "reviewDate", reviewDate + "T09:00:00Z"));
    }

    /** Case file {@code Probation} + start of "Probation review"; failures are logged and retried. */
    private void driveEngine(final String caseId, final Map<String, Object> probationItem) {
        try {
            try {
                caseEngineClient.createCaseFileItem(caseId, "Probation", probationItem);
            } catch (final Exception createFailed) {
                // Already present (a retry after a partial failure) — update instead.
                caseEngineClient.updateCaseFile(caseId, "Probation", probationItem);
            }
            caseEngineClient.startPlanItem(caseId, TASK_PROBATION_REVIEW);
        } catch (final Exception ex) {
            log.error("Engine call for probation review failed for caseId={}; retried by the scheduler",
                    caseId, ex);
        }
    }

    // ── P01 Get ──────────────────────────────────────────────────────────────

    /**
     * P01 — the review form with saved answers and context (Screen 12) or, for HR, the same page
     * with the decision state (Screen 13, spec D-P4 "same page + HR action bar").
     *
     * @param reviewId the review
     * @param caller   the employee's current manager, or HR
     * @return the review
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('MANAGER','HR')")
    public ProbationReviewDto getReview(final Long reviewId, final Employee caller) {
        final Review review = findOrThrow(reviewId);
        final Employee employee = review.getContract().getEmployee();
        checkReadAccess(employee, caller);
        return toDto(review, employee, caller);
    }

    // ── P02 Save draft ───────────────────────────────────────────────────────

    /**
     * P02 — saves answers and the chosen outcome without submitting. Nothing is required yet.
     *
     * @param reviewId the review
     * @param req      answers and outcome
     * @param caller   the employee's current manager
     * @return the updated review
     */
    @Transactional
    @PreAuthorize("hasRole('MANAGER')")
    public ProbationReviewDto saveDraft(final Long reviewId, final SaveProbationReviewRequest req,
                                        final Employee caller) {
        final Review review = findOrThrow(reviewId);
        final Employee employee = review.getContract().getEmployee();
        checkWriteAccess(review, employee, caller, req.version());
        final String before = snapshot(review);
        final Map<Long, FormQuestion> questions = questionsById(review.getForm());
        final String outcome = parseOutcome(req.outcome());
        saveAnswers(review, req, questions, caller);
        review.setOutcome(outcome);
        review.setReviewer(caller);
        touch(review, caller);
        reviewRepository.save(review);
        auditLogService.record(employee, "REVIEW", review.getId(), "PROBATION_REVIEW_DRAFT_SAVED",
                caller, before, snapshot(review));
        return toDto(review, employee, caller);
    }

    // ── P03 Submit ───────────────────────────────────────────────────────────

    /**
     * P03 — submits the review: every required answer and the outcome must be present
     * ("Add a summary and choose an outcome." / "This field is required."). Completes the
     * "Probation review" engine task through the undo window (R-API-6).
     *
     * @param reviewId the review
     * @param req      answers and outcome
     * @param caller   the employee's current manager
     * @return {@code {undoUntil, taskId}} for the 202 response
     */
    @Transactional
    @PreAuthorize("hasRole('MANAGER')")
    public UndoWindowResponse submit(final Long reviewId, final SaveProbationReviewRequest req,
                                     final Employee caller) {
        final Review review = findOrThrow(reviewId);
        final Employee employee = review.getContract().getEmployee();
        checkWriteAccess(review, employee, caller, req.version());
        final Map<Long, FormQuestion> questions = questionsById(review.getForm());
        validateForSubmit(req, questions);

        final String before = snapshot(review);
        final OffsetDateTime now = OffsetDateTime.now(clock);
        final String outcome = parseOutcome(req.outcome());
        saveAnswers(review, req, questions, caller);
        review.setOutcome(outcome);
        review.setReviewer(caller);
        review.setStatus(ReviewStatus.SUBMITTED);
        review.setSubmittedAt(now);
        touch(review, caller);
        reviewRepository.save(review);

        final EngineTask task = engineTaskRepository.findOpenTaskByName(employee.getId(), TASK_PROBATION_REVIEW)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Probation review task not found for employee " + employee.getId()));
        final OffsetDateTime undoUntil = startUndoWindow(task, caller, json(Map.of("outcome", outcome)), now);

        auditLogService.record(employee, "REVIEW", review.getId(), "PROBATION_REVIEW_SUBMITTED",
                caller, before, snapshot(review));
        return new UndoWindowResponse(undoUntil.toString(), task.getId());
    }

    // ── P04 HR decision ──────────────────────────────────────────────────────

    /**
     * P04 — HR approves or rejects an "End employment" outcome (ONB-08). Only possible while HR's
     * "Approve end of employment" task is open; completes it through the undo window.
     *
     * @param reviewId the review
     * @param req      APPROVED / REJECTED
     * @param caller   HR (never the reviewed employee, G-13)
     * @return {@code {undoUntil, taskId}} for the 202 response
     */
    @Transactional
    @PreAuthorize("hasRole('HR')")
    public UndoWindowResponse decide(final Long reviewId, final HrDecisionRequest req, final Employee caller) {
        final Review review = findOrThrow(reviewId);
        final Employee employee = review.getContract().getEmployee();
        selfActionGuard.forbidSelf(caller, employee.getId());
        final HrDecision decision = parseDecision(req.decision());
        if (decision == null) {
            throw ApiException.fieldValidation(List.of(Map.of("field", "decision", "message", MSG_REQUIRED)));
        }
        final EngineTask task = awaitingDecisionTask(review, employee);
        if (task == null) {
            throw new ApiException(CODE_NOT_AWAITING, HttpStatus.CONFLICT, MSG_NOT_AWAITING);
        }

        final String before = snapshot(review);
        final OffsetDateTime now = OffsetDateTime.now(clock);
        review.setHrDecision(decision.name());
        review.setHrDecidedBy(caller);
        review.setHrDecidedAt(now);
        touch(review, caller);
        reviewRepository.save(review);
        final OffsetDateTime undoUntil = startUndoWindow(task, caller,
                json(Map.of("hrDecision", decision.name())), now);

        auditLogService.record(employee, "REVIEW", review.getId(), "PROBATION_HR_DECISION",
                caller, before, snapshot(review));
        return new UndoWindowResponse(undoUntil.toString(), task.getId());
    }

    // ── Undo window hooks (TaskService / UndoWindowScheduler) ────────────────

    /** Whether an engine task is one of the two probation tasks. */
    public static boolean isProbationTask(final EngineTask task) {
        return RELATED_ENTITY.equals(task.getRelatedEntity())
                && (TASK_PROBATION_REVIEW.equals(task.getTaskName()) || TASK_APPROVE_END.equals(task.getTaskName()));
    }

    /**
     * Reverts the review when the caller undoes a pending submit/decision within the window (A37).
     * Runs inside {@code TaskService.undoTask}'s transaction.
     *
     * @param task   the undone engine task
     * @param caller who undid it
     */
    public void onTaskUndone(final EngineTask task, final Employee caller) {
        final Review review = reviewRepository.findById(task.getRelatedId()).orElse(null);
        if (review == null) {
            return;
        }
        final String before = snapshot(review);
        if (TASK_PROBATION_REVIEW.equals(task.getTaskName())) {
            review.setStatus(ReviewStatus.DRAFT);
            review.setSubmittedAt(null);
        } else {
            review.setHrDecision(null);
            review.setHrDecidedBy(null);
            review.setHrDecidedAt(null);
        }
        touch(review, caller);
        reviewRepository.save(review);
        auditLogService.record(review.getContract().getEmployee(), "REVIEW", review.getId(),
                "PROBATION_UNDO", caller, before, snapshot(review));
    }

    /**
     * Side effects once the engine accepted a completed probation task (undo window closed).
     * Runs inside {@code UndoWindowScheduler.processSingleTask}'s transaction.
     *
     * @param task the executed engine task
     */
    public void onTaskExecuted(final EngineTask task) {
        final Review review = reviewRepository.findById(task.getRelatedId()).orElse(null);
        if (review == null) {
            return;
        }
        final Employee employee = review.getContract().getEmployee();
        final OffsetDateTime now = OffsetDateTime.now(clock);
        if (TASK_PROBATION_REVIEW.equals(task.getTaskName())) {
            if (ProbationOutcome.END.name().equals(review.getOutcome())) {
                // ONB-08: the engine raises HR's approval task (sentry on Probation.outcome);
                // mirror it now so it shows in HR's My Tasks at once (EngineTaskWatcher resolves
                // the real engine task id by name).
                final LocalDate today = now.toLocalDate();
                final LocalDate due = review.getDueDate().isBefore(today) ? today : review.getDueDate();
                createEngineTask(employee, null, TASK_APPROVE_END, due, review.getId(), now);
                taskNotificationService.notifyTaskAssigned(OwnerRole.HR, employee, TASK_APPROVE_END,
                        hrRoute(employee, review.getId()));
            }
            return;
        }
        if (HrDecision.APPROVED.name().equals(review.getHrDecision())) {
            startOffboarding(review, employee, task.getPendingBy(), now);
        }
    }

    /** ONB-08 APPROVED: employee_exit (PROBATION_NOT_PASSED, SYSTEM) and stage OFFBOARDING. */
    private void startOffboarding(final Review review, final Employee employee, final Employee approver,
                                  final OffsetDateTime now) {
        if (employeeExitRepository.existsByEmployeeIdAndStatusNot(employee.getId(), "REJECTED")) {
            log.warn("Employee id={} already has an active exit; probation exit not created", employee.getId());
            return;
        }
        final LocalDate today = now.toLocalDate();
        final LocalDate lastDay = review.getDueDate().isBefore(today) ? today : review.getDueDate();
        final EmployeeExit exit = new EmployeeExit();
        exit.setEmployee(employee);
        exit.setExitType("PROBATION_NOT_PASSED");
        exit.setInitiatedBy(null);
        exit.setInitiatedByRole("SYSTEM");
        exit.setNoticeDate(today);
        exit.setLastWorkingDay(lastDay);
        exit.setStatus("APPROVED");
        exit.setApprovedBy(approver);
        exit.setApprovedAt(now);
        exit.setCreatedAt(now);
        exit.setUpdatedAt(now);
        exit.setCreatedBy(approver != null ? approver.getId() : null);
        final EmployeeExit savedExit = employeeExitRepository.save(exit);

        final String stageBefore = employee.getStage().name();
        employee.setStage(EmployeeStage.OFFBOARDING);
        employee.setStageSince(today);
        employee.setUpdatedAt(now);
        employeeRepository.save(employee);
        auditLogService.record(employee, "EMPLOYEE_EXIT", savedExit.getId(), "PROBATION_EXIT_APPROVED",
                approver, json(Map.of("stage", stageBefore)),
                json(Map.of("stage", EmployeeStage.OFFBOARDING.name(), "exitType", "PROBATION_NOT_PASSED",
                        "lastWorkingDay", lastDay.toString())));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Review findOrThrow(final Long reviewId) {
        final Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Review " + reviewId + " not found"));
        if (!REVIEW_TYPE.equals(review.getReviewType())) {
            throw new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND, "Review " + reviewId + " not found");
        }
        return review;
    }

    /** HR (not on themselves, G-13) or the employee's current manager (WS-02); never the employee (D-P2). */
    private void checkReadAccess(final Employee employee, final Employee caller) {
        if (caller.getAppRole() == AppRole.HR) {
            selfActionGuard.forbidSelf(caller, employee.getId());
            return;
        }
        if (!isCurrentManager(employee, caller)) {
            throw forbidden();
        }
    }

    private void checkWriteAccess(final Review review, final Employee employee, final Employee caller,
                                  final Integer version) {
        selfActionGuard.forbidSelf(caller, employee.getId());
        if (!isCurrentManager(employee, caller) || employee.getArchivedAt() != null) {
            throw forbidden();
        }
        if (review.getStatus() != ReviewStatus.DRAFT) {
            throw new ApiException(CODE_ALREADY_SUBMITTED, HttpStatus.CONFLICT, MSG_ALREADY_SUBMITTED);
        }
        // R-DB-5: every update checks the version the client read with P01
        if (version == null) {
            throw ApiException.fieldValidation(List.of(Map.of("field", "version", "message", MSG_REQUIRED)));
        }
        if (!version.equals(review.getVersion())) {
            throw new ApiException(ErrorCodes.STALE_VERSION, HttpStatus.CONFLICT,
                    "Version conflict — reload and retry");
        }
    }

    private static boolean isCurrentManager(final Employee employee, final Employee caller) {
        return employee.getManager() != null && employee.getManager().getId().equals(caller.getId());
    }

    private static ApiException forbidden() {
        return new ApiException(ErrorCodes.FORBIDDEN, HttpStatus.FORBIDDEN, "Not allowed");
    }

    /** The open, not-yet-pending "Approve end of employment" task, when HR may decide now. */
    private EngineTask awaitingDecisionTask(final Review review, final Employee employee) {
        if (review.getStatus() != ReviewStatus.SUBMITTED
                || !ProbationOutcome.END.name().equals(review.getOutcome())
                || review.getHrDecision() != null) {
            return null;
        }
        return engineTaskRepository.findOpenTaskByName(employee.getId(), TASK_APPROVE_END)
                .filter(t -> review.getId().equals(t.getRelatedId()))
                .orElse(null);
    }

    private void validateForSubmit(final SaveProbationReviewRequest req, final Map<Long, FormQuestion> questions) {
        final Map<Long, SaveProbationReviewRequest.Answer> given = answersById(req);
        final List<Map<String, String>> errors = new ArrayList<>();
        for (final FormQuestion q : questions.values()) {
            if (!q.isRequired()) {
                continue;
            }
            final SaveProbationReviewRequest.Answer a = given.get(q.getId());
            final boolean answered = a != null && switch (q.getAnswerType()) {
                case SCALE_1_5 -> a.rating() != null && a.rating() >= 1 && a.rating() <= 5;
                case YES_NO -> a.yesNo() != null;
                case TEXT -> a.textAnswer() != null && !a.textAnswer().isBlank();
            };
            if (!answered) {
                errors.add(Map.of("field", "answers." + q.getId(),
                        "message", q.getAnswerType() == FormAnswerType.TEXT ? MSG_SUMMARY_OUTCOME : MSG_REQUIRED));
            }
        }
        if (parseOutcome(req.outcome()) == null) {
            errors.add(Map.of("field", "outcome", "message", MSG_SUMMARY_OUTCOME));
        }
        if (!errors.isEmpty()) {
            throw ApiException.fieldValidation(errors);
        }
    }

    private void saveAnswers(final Review review, final SaveProbationReviewRequest req,
                             final Map<Long, FormQuestion> questions, final Employee caller) {
        final OffsetDateTime now = OffsetDateTime.now(clock);
        final Map<ReviewAnswerId, ReviewAnswer> existing = reviewAnswerRepository.findByIdReviewId(review.getId())
                .stream().collect(Collectors.toMap(ReviewAnswer::getId, Function.identity()));
        for (final SaveProbationReviewRequest.Answer a : answersById(req).values()) {
            final FormQuestion q = questions.get(a.questionId());
            if (q == null) {
                throw ApiException.fieldValidation(List.of(Map.of("field", "answers." + a.questionId(),
                        "message", MSG_REQUIRED)));
            }
            final ReviewAnswerId id = new ReviewAnswerId(review.getId(), q.getId());
            final ReviewAnswer answer = existing.getOrDefault(id, new ReviewAnswer(id));
            answer.setRating(null);
            answer.setYesNo(null);
            answer.setTextAnswer(null);
            switch (q.getAnswerType()) {
                case SCALE_1_5 -> {
                    if (a.rating() != null && (a.rating() < 1 || a.rating() > 5)) {
                        throw ApiException.fieldValidation(List.of(Map.of("field", "answers." + q.getId(),
                                "message", MSG_REQUIRED)));
                    }
                    answer.setRating(a.rating() != null ? a.rating().shortValue() : null);
                }
                case YES_NO -> answer.setYesNo(a.yesNo());
                case TEXT -> answer.setTextAnswer(a.textAnswer() != null && !a.textAnswer().isBlank()
                        ? a.textAnswer().trim() : null);
            }
            if (answer.getCreatedAt() == null) {
                answer.setCreatedAt(now);
                answer.setCreatedBy(caller.getId());
            }
            answer.setUpdatedAt(now);
            answer.setUpdatedBy(caller.getId());
            reviewAnswerRepository.save(answer);
        }
    }

    private static Map<Long, SaveProbationReviewRequest.Answer> answersById(final SaveProbationReviewRequest req) {
        final Map<Long, SaveProbationReviewRequest.Answer> byId = new LinkedHashMap<>();
        if (req.answers() != null) {
            for (final SaveProbationReviewRequest.Answer a : req.answers()) {
                if (a != null && a.questionId() != null) {
                    byId.put(a.questionId(), a);
                }
            }
        }
        return byId;
    }

    /** The form's questions in display order (sections, then questions). */
    private Map<Long, FormQuestion> questionsById(final AppraisalForm form) {
        final Map<Long, FormQuestion> byId = new LinkedHashMap<>();
        for (final FormSection s : formSectionRepository.findByFormIdOrderBySortOrderAsc(form.getId())) {
            for (final FormQuestion q : formQuestionRepository.findBySectionIdOrderBySortOrderAsc(s.getId())) {
                byId.put(q.getId(), q);
            }
        }
        return byId;
    }

    private AppraisalForm activeProbationForm() {
        return appraisalFormRepository.findByStatusNot(FormStatus.ARCHIVED).stream()
                .filter(f -> f.getUsedFor() == FormUsedFor.PROBATION && f.getStatus() == FormStatus.ACTIVE)
                .findFirst().orElse(null);
    }

    private static String parseOutcome(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ProbationOutcome.valueOf(value).name();
        } catch (final IllegalArgumentException ex) {
            throw ApiException.fieldValidation(List.of(Map.of("field", "outcome", "message", MSG_SUMMARY_OUTCOME)));
        }
    }

    private static HrDecision parseDecision(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return HrDecision.valueOf(value);
        } catch (final IllegalArgumentException ex) {
            return null;
        }
    }

    /** ONB-01: probation end = start + probation months (same formula as {@code v_contract}). */
    private static LocalDate probationEndDate(final Contract contract) {
        return contract.getStartDate().plusMonths(contract.getProbationMonths());
    }

    private OffsetDateTime startUndoWindow(final EngineTask task, final Employee caller, final String output,
                                           final OffsetDateTime now) {
        final int undoSeconds = settingRepository.findById("UNDO_WINDOW_SECONDS")
                .map(s -> s.getValueInt() != null ? s.getValueInt() : 10).orElse(10);
        final OffsetDateTime undoUntil = now.plusSeconds(undoSeconds);
        task.setPendingStatus(PendingStatus.PENDING);
        task.setPendingBy(caller);
        task.setPendingExecuteAt(undoUntil);
        task.setPendingOutput(output);
        task.setUpdatedAt(now);
        task.setUpdatedBy(caller.getId());
        engineTaskRepository.save(task);
        return undoUntil;
    }

    private void createEngineTask(final Employee employee, final Employee owner, final String taskName,
                                  final LocalDate dueDate, final Long reviewId, final OffsetDateTime now) {
        final EngineTask task = new EngineTask();
        // Placeholder until EngineTaskWatcher resolves the engine's real id by task name.
        task.setEngineTaskId(UUID.randomUUID().toString());
        task.setEmployee(employee);
        task.setOwnerEmployee(owner);
        task.setTaskName(taskName);
        task.setDueDate(dueDate);
        task.setRelatedEntity(RELATED_ENTITY);
        task.setRelatedId(reviewId);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setNotifiedAt(now);
        engineTaskRepository.save(task);
    }

    /**
     * G-04 for the manager's task: one in-app notification plus the dedicated
     * {@code PROBATION_REVIEW_DUE} e-mail (FRD-08 template, spec D-P7) instead of the generic
     * TASK_ASSIGNED one.
     */
    private void notifyManager(final Employee employee, final Employee manager, final Review review,
                               final OffsetDateTime now) {
        final String route = managerRoute(employee, review.getId());
        final Notification notification = new Notification();
        notification.setRecipient(manager);
        notification.setTitle("New task: " + TASK_PROBATION_REVIEW);
        notification.setLinkRoute(route);
        notification.setEmployee(employee);
        notification.setCreatedAt(now);
        notification.setUpdatedAt(now);
        notificationRepository.save(notification);

        if (manager.getPrivateEmail() == null) {
            return;
        }
        emailTemplateRepository.findByCode("PROBATION_REVIEW_DUE").ifPresent(template -> {
            final Map<String, String> vars = new HashMap<>();
            vars.put("{employee_name}", employee.getFirstName() + " " + employee.getLastName());
            vars.put("{manager_name}", manager.getFirstName());
            vars.put("{task_link}", frontendBaseUrl + route);
            emailOutboxService.enqueue("PROBATION_REVIEW_DUE", manager.getPrivateEmail(),
                    render(template.getSubject(), vars), render(template.getBody(), vars), employee);
        });
    }

    private static String render(final String text, final Map<String, String> vars) {
        String out = text == null ? "" : text;
        for (final Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    private ProbationReviewDto toDto(final Review review, final Employee employee, final Employee caller) {
        final Contract contract = review.getContract();
        final AppraisalForm form = review.getForm();
        final Map<Long, ReviewAnswer> answers = reviewAnswerRepository.findByIdReviewId(review.getId()).stream()
                .collect(Collectors.toMap(a -> a.getId().getQuestionId(), Function.identity()));
        final List<ProbationQuestionDto> questions = new ArrayList<>();
        for (final FormSection s : formSectionRepository.findByFormIdOrderBySortOrderAsc(form.getId())) {
            for (final FormQuestion q : formQuestionRepository.findBySectionIdOrderBySortOrderAsc(s.getId())) {
                final ReviewAnswer a = answers.get(q.getId());
                questions.add(new ProbationQuestionDto(q.getId(), s.getName(), q.getText(), q.getDescription(),
                        q.getAnswerType().name(), q.isRequired(),
                        a != null && a.getRating() != null ? a.getRating().intValue() : null,
                        a != null ? a.getYesNo() : null,
                        a != null ? a.getTextAnswer() : null));
            }
        }
        final var items = checklistItemRepository.findByEmployeeIdAndPhase(employee.getId(), ChecklistPhase.ONBOARDING);
        final long done = items.stream().filter(i -> i.getStatus() == ChecklistItemStatus.DONE).count();
        final int percent = items.isEmpty() ? 0 : (int) (done * 100 / items.size());

        final boolean editable = review.getStatus() == ReviewStatus.DRAFT && employee.getArchivedAt() == null
                && isCurrentManager(employee, caller);
        final boolean awaiting = awaitingDecisionTask(review, employee) != null;
        final boolean canDecide = awaiting && caller.getAppRole() == AppRole.HR
                && !caller.getId().equals(employee.getId());
        return new ProbationReviewDto(
                review.getId(), review.getVersion(), employee.getId(), employee.getEmployeeCode(),
                employee.getFirstName() + " " + employee.getLastName(),
                review.getReviewer().getFirstName() + " " + review.getReviewer().getLastName(),
                review.getStatus().name(), review.getDueDate().toString(),
                contract.getStartDate().toString(), probationEndDate(contract).toString(),
                null, percent,
                employee.getBuddy() != null ? employee.getBuddy().getFirstName() + " " + employee.getBuddy().getLastName() : null,
                form.getName(),
                List.of(form.getLabel1(), form.getLabel2(), form.getLabel3(), form.getLabel4(), form.getLabel5()),
                questions, review.getOutcome(),
                review.getSubmittedAt() != null ? review.getSubmittedAt().toString() : null,
                review.getHrDecision(),
                review.getHrDecidedAt() != null ? review.getHrDecidedAt().toString() : null,
                editable, awaiting, canDecide);
    }

    private void touch(final Review review, final Employee caller) {
        review.setUpdatedAt(OffsetDateTime.now(clock));
        review.setUpdatedBy(caller != null ? caller.getId() : null);
    }

    /** Audit snapshot (R-API-5): review state only — answers are free text (no personal data copied). */
    private static String snapshot(final Review review) {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", review.getStatus().name());
        m.put("outcome", review.getOutcome());
        m.put("hrDecision", review.getHrDecision());
        return json(m);
    }

    private static String json(final Map<String, ?> map) {
        return map.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + (e.getValue() == null ? "null"
                        : e.getValue() instanceof Number ? e.getValue().toString()
                        : "\"" + e.getValue().toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\""))
                .collect(Collectors.joining(",", "{", "}"));
    }
}
