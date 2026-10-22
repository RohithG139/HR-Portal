package com.cubeworks.hr.onboarding;

import com.cubeworks.hr.employee.Contract;
import com.cubeworks.hr.employee.ContractRepository;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.engine.CaseEngineClient;
import com.cubeworks.hr.task.EngineTask;
import com.cubeworks.hr.task.EngineTaskRepository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.cubeworks.hr.notification.NotificationRepository;
import com.cubeworks.hr.notification.TaskNotificationService;
import com.cubeworks.hr.settings.OwnerRole;
import com.cubeworks.hr.settings.SettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Raises "Onboarding overdue" for HR when an onboarding is still in progress
 * {@code ONBOARDING_OVERDUE} days after the employee's start date (FRD-02 §6, D-20).
 *
 * <h2>Why Spring and not the case engine</h2>
 * <p>The E1 spike showed CaseFabric 1.1.35 timers cannot read a case-file date, and the model has
 * no "Onboarding overdue" plan item, so the case-file fields {@code Onboarding.overdueDate} /
 * {@code overdueEnabled} drive nothing. This scheduler computes the deadline from the database
 * instead (F01 spec §8).</p>
 *
 * <h2>Once per employee</h2>
 * <p>An existing "New task: Onboarding overdue" notification about the employee means the alert
 * was already raised, so no extra column is needed. The setting cannot be switched off (D-20),
 * so its {@code enabled} flag is not checked.</p>
 */
@Component
public class OnboardingOverdueScheduler {

    private static final Logger log = LoggerFactory.getLogger(OnboardingOverdueScheduler.class);

    /** Task title shown to HR; also the once-only marker (see class doc). */
    static final String TASK_TITLE = "Onboarding overdue";

    private final OnboardingRepository onboardingRepository;
    private final ContractRepository contractRepository;
    private final SettingRepository settingRepository;
    private final NotificationRepository notificationRepository;
    private final TaskNotificationService taskNotificationService;
    private final EngineTaskRepository engineTaskRepository;
    private final CaseEngineClient caseEngineClient;
    private final Clock clock;

    /** Self-reference for AOP-correct per-row transactions (same pattern as OverdueReminderScheduler). */
    private OnboardingOverdueScheduler self;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param onboardingRepository    finds in-progress onboardings
     * @param contractRepository      reads the current contract's start date
     * @param settingRepository       reads {@code ONBOARDING_OVERDUE} (R-ARC-7)
     * @param notificationRepository  checks whether the alert was already raised
     * @param taskNotificationService raises the in-app notification + e-mail to HR
     * @param clock                   application-wide clock bean
     */
    public OnboardingOverdueScheduler(final OnboardingRepository onboardingRepository,
                                      final ContractRepository contractRepository,
                                      final SettingRepository settingRepository,
                                      final NotificationRepository notificationRepository,
                                      final TaskNotificationService taskNotificationService,
                                      final EngineTaskRepository engineTaskRepository,
                                      final CaseEngineClient caseEngineClient,
                                      final Clock clock) {
        this.engineTaskRepository = engineTaskRepository;
        this.caseEngineClient = caseEngineClient;
        this.onboardingRepository = onboardingRepository;
        this.contractRepository = contractRepository;
        this.settingRepository = settingRepository;
        this.notificationRepository = notificationRepository;
        this.taskNotificationService = taskNotificationService;
        this.clock = clock;
    }

    /**
     * Injects the Spring proxy of this bean (see {@code UndoWindowScheduler#setSelf} for why).
     *
     * @param self the Spring proxy of this scheduler bean
     */
    @Autowired
    public void setSelf(@Lazy final OnboardingOverdueScheduler self) {
        this.self = self;
    }

    /** Polls hourly (day-granularity rule) and raises the alert for each newly overdue onboarding. */
    @Scheduled(fixedDelay = 3_600_000)
    public void raiseOverdueOnboardings() {
        final int overdueDays = settingRepository.findById("ONBOARDING_OVERDUE")
                .map(s -> s.getValueInt() != null ? s.getValueInt() : 3).orElse(3);
        final LocalDate today = LocalDate.now(clock);

        for (final Onboarding onb : onboardingRepository.findByStatus(OnboardingStatus.IN_PROGRESS)) {
            try {
                self.raiseIfOverdue(onb.getId(), overdueDays, today);
            } catch (final Exception ex) {
                log.error("Failed to raise onboarding overdue for onboarding id={}; will retry",
                        onb.getId(), ex);
            }
        }
    }

    /**
     * Raises "Onboarding overdue" for one onboarding if it is overdue and not yet raised.
     *
     * @param onboardingId the onboarding's PK
     * @param overdueDays  days after the start date ({@code ONBOARDING_OVERDUE})
     * @param today        the current date
     */
    @Transactional
    public void raiseIfOverdue(final Long onboardingId, final int overdueDays, final LocalDate today) {
        final Onboarding onb = onboardingRepository.findById(onboardingId).orElse(null);
        if (onb == null || onb.getStatus() != OnboardingStatus.IN_PROGRESS) {
            return;
        }
        final Long employeeId = onb.getEmployee().getId();
        final LocalDate startDate = contractRepository.findByEmployeeIdAndIsCurrentTrue(employeeId)
                .map(Contract::getStartDate).orElse(null);
        if (startDate == null || startDate.plusDays(overdueDays).isAfter(today)) {
            return;
        }
        if (engineTaskRepository.existsByEmployeeIdAndTaskName(employeeId, TASK_TITLE)
                || notificationRepository.existsByEmployee_IdAndTitle(employeeId, "New task: " + TASK_TITLE)) {
            return;
        }
        // A real HR task (G-03: owner, due date, Open action, in My Tasks), not only a notification.
        // The engine_task row is written in this transaction (placeholder id, resolved by
        // EngineTaskWatcher); the engine call runs after commit (R-ARC-5).
        final Employee emp = onb.getEmployee();
        final OffsetDateTime now = OffsetDateTime.now(clock);
        final EngineTask task = new EngineTask();
        task.setEngineTaskId(UUID.randomUUID().toString());
        task.setEmployee(emp);
        task.setTaskName(TASK_TITLE);
        task.setDueDate(today);
        task.setRelatedEntity("ONBOARDING");
        task.setRelatedId(onb.getId());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setNotifiedAt(now);
        engineTaskRepository.save(task);
        // Step 3 (checklist) of the wizard — the wizard's ?step is 0-based
        taskNotificationService.notifyTaskAssigned(OwnerRole.HR, emp, TASK_TITLE,
                "/employees/" + employeeId + "/onboarding?step=2");

        // case id = employee_code (01-overview.md golden rule; employee.case_id is only a cache)
        final String caseId = emp.getEmployeeCode();
        if (caseId != null) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        // manualActivationRule keeps it Enabled until started (employeecase.case)
                        caseEngineClient.startPlanItem(caseId, TASK_TITLE);
                    } catch (final Exception ex) {
                        log.error("Engine start of '{}' failed for caseId={}", TASK_TITLE, caseId, ex);
                    }
                }
            });
        }
    }
}
