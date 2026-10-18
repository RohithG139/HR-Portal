package com.cubeworks.hr.task;

import com.cubeworks.hr.notification.TaskNotificationService;
import com.cubeworks.hr.onboarding.ChecklistItem;
import com.cubeworks.hr.onboarding.ChecklistItemRepository;
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
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Polls for overdue tasks and raises the G-05 reminder: "An overdue reminder e-mail is sent
 * 1 day after the due date (configurable, FRD-08)."
 *
 * <h2>Scope</h2>
 * <p>Covers both {@code checklist_item} rows (any owner role, any phase) and {@code engine_task}
 * rows ("Onboarding checklist" → HR, "Equipment request"/"Remaining setup" → IT admin — same
 * mapping {@link com.cubeworks.hr.onboarding.OnboardingService} uses for G-04). Each remindable
 * row is reminded exactly once: the scheduler stamps {@code overdue_reminder_at} in the same
 * transaction as the notification, so a second poll never finds the row again.</p>
 *
 * <h2>Cadence</h2>
 * <p>This is a day-granularity rule (the setting's unit is always {@code DAYS}), so hourly
 * polling is far more than enough — no need for {@link UndoWindowScheduler}'s 2-second cadence.</p>
 *
 * <h2>Self-invocation and Spring AOP</h2>
 * <p>Same pattern as {@link UndoWindowScheduler}: the polling method calls through a
 * {@code @Lazy} self-reference so that {@code @Transactional} on the per-row methods is
 * actually applied by the Spring proxy, and one row's failure does not roll back the others.</p>
 */
@Component
public class OverdueReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverdueReminderScheduler.class);

    private final ChecklistItemRepository checklistItemRepository;
    private final EngineTaskRepository engineTaskRepository;
    private final SettingRepository settingRepository;
    private final TaskNotificationService taskNotificationService;
    private final Clock clock;

    /** Self-reference for AOP-correct per-row transactions (see class doc). */
    private OverdueReminderScheduler self;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param checklistItemRepository repository for remindable checklist items
     * @param engineTaskRepository    repository for remindable engine tasks
     * @param settingRepository       reads {@code OVERDUE_REMINDER_DAYS} (R-ARC-7)
     * @param taskNotificationService raises the in-app notification + e-mail
     * @param clock                   application-wide clock bean
     */
    public OverdueReminderScheduler(final ChecklistItemRepository checklistItemRepository,
                                     final EngineTaskRepository engineTaskRepository,
                                     final SettingRepository settingRepository,
                                     final TaskNotificationService taskNotificationService,
                                     final Clock clock) {
        this.checklistItemRepository = checklistItemRepository;
        this.engineTaskRepository = engineTaskRepository;
        this.settingRepository = settingRepository;
        this.taskNotificationService = taskNotificationService;
        this.clock = clock;
    }

    /**
     * Injects the Spring proxy of this bean (see {@link UndoWindowScheduler#setSelf} for why).
     *
     * @param self the Spring proxy of this scheduler bean
     */
    @Autowired
    public void setSelf(@Lazy final OverdueReminderScheduler self) {
        this.self = self;
    }

    /**
     * Polls hourly for overdue, not-yet-reminded tasks and reminds each one.
     *
     * <p>Bails out entirely if {@code OVERDUE_REMINDER_DAYS} is disabled (R-ARC-7: the switch
     * lives in {@code setting}, not a code constant) — FRD-08 marks this rule "Can be switched
     * off: Yes", unlike the non-switchable {@code ONBOARDING_OVERDUE} timer.</p>
     */
    @Scheduled(fixedDelay = 3_600_000)
    public void sendOverdueReminders() {
        final var setting = settingRepository.findById("OVERDUE_REMINDER_DAYS").orElse(null);
        if (setting == null || !setting.isEnabled()) {
            return;
        }
        final int reminderDays = setting.getValueInt() != null ? setting.getValueInt() : 1;
        final LocalDate cutoff = LocalDate.now(clock).minusDays(reminderDays);

        for (final ChecklistItem item : checklistItemRepository.findRemindableOverdue(cutoff)) {
            try {
                self.remindChecklistItem(item.getId());
            } catch (final Exception ex) {
                log.error("Failed to send overdue reminder for checklist_item id={}; will retry",
                        item.getId(), ex);
            }
        }
        for (final EngineTask task : engineTaskRepository.findRemindableOverdue(cutoff)) {
            try {
                self.remindEngineTask(task.getId());
            } catch (final Exception ex) {
                log.error("Failed to send overdue reminder for engine_task id={}; will retry",
                        task.getId(), ex);
            }
        }
    }

    /**
     * Reminds the owner of one overdue checklist item and stamps {@code overdueReminderAt}.
     *
     * @param itemId the checklist item's PK
     */
    @Transactional
    public void remindChecklistItem(final Long itemId) {
        final ChecklistItem item = checklistItemRepository.findById(itemId).orElse(null);
        if (item == null || item.getOverdueReminderAt() != null) {
            // Already reminded (or deleted) between the poll and this call.
            return;
        }
        taskNotificationService.notifyOverdue(item.getOwnerRole(), item.getEmployee(),
                item.getName(), routeFor(item.getOwnerRole(), item.getEmployee().getId()));
        final OffsetDateTime now = OffsetDateTime.now(clock);
        item.setOverdueReminderAt(now);
        item.setUpdatedAt(now);
        checklistItemRepository.save(item);
    }

    /**
     * Reminds the owner of one overdue engine task and stamps {@code overdueReminderAt}.
     *
     * @param taskId the engine task's PK
     */
    @Transactional
    public void remindEngineTask(final Long taskId) {
        final EngineTask task = engineTaskRepository.findById(taskId).orElse(null);
        if (task == null || task.getOverdueReminderAt() != null) {
            return;
        }
        final OwnerRole ownerRole;
        final String linkRoute;
        if ("Onboarding checklist".equals(task.getTaskName())) {
            ownerRole = OwnerRole.HR;
            linkRoute = "/employees/" + task.getEmployee().getId() + "/onboarding";
        } else if ("Equipment request".equals(task.getTaskName())) {
            ownerRole = OwnerRole.IT_ADMIN;
            linkRoute = "/it/equipment-requests/" + task.getEmployee().getId();
        } else if ("Remaining setup".equals(task.getTaskName())) {
            ownerRole = OwnerRole.IT_ADMIN;
            linkRoute = "/it/setup/" + task.getEmployee().getId();
        } else {
            log.warn("No owner-role mapping for engine task name '{}' (id={}); skipping reminder",
                    task.getTaskName(), taskId);
            return;
        }
        taskNotificationService.notifyOverdue(ownerRole, task.getEmployee(), task.getTaskName(), linkRoute);
        final OffsetDateTime now = OffsetDateTime.now(clock);
        task.setOverdueReminderAt(now);
        task.setUpdatedAt(now);
        engineTaskRepository.save(task);
    }

    /**
     * Maps an owner role to the frontend route a reminder about that role's task should open.
     * Mirrors {@code OnboardingService.routeFor} — kept separate because this class has no
     * onboarding-specific checklist-item context, only the generic owner role + employee id.
     */
    private String routeFor(final OwnerRole ownerRole, final Long employeeId) {
        return switch (ownerRole) {
            case HR -> "/employees/" + employeeId + "/onboarding";
            case IT_ADMIN -> "/it/equipment-requests/" + employeeId;
            case EMPLOYEE -> "/dashboard/my-onboarding";
            case MANAGER -> "/manager/team/" + employeeId + "/orientation";
            case SYSTEM -> null;
        };
    }
}
