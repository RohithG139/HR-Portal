package com.cubeworks.hr.notification;

import com.cubeworks.hr.common.outbox.EmailOutboxService;
import com.cubeworks.hr.employee.AppRole;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.employee.EmployeeRepository;
import com.cubeworks.hr.settings.OwnerRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Notifies the owner(s) of a task — in-app notification plus e-mail — on two events:
 * a new task being raised (G-04) and a task becoming overdue (G-05).
 *
 * <p>Must be called inside the same {@code @Transactional} boundary as the task's own creation
 * or overdue-detection, same convention as {@link EmailOutboxService#enqueue}: if that
 * transaction rolls back, neither the notification nor the e-mail should have gone out.</p>
 *
 * <h2>Owner resolution</h2>
 * <p>A task's "owner" is a {@code checklist_item}/{@code engine_task} {@code ownerRole}
 * (HR/IT_ADMIN/MANAGER/EMPLOYEE/SYSTEM), not a single person — HR and IT_ADMIN are tenant-wide
 * roles (anyone holding that app role is notified), MANAGER resolves to the subject employee's
 * specific line manager, EMPLOYEE resolves to the subject employee themself, and SYSTEM has no
 * human recipient (the backend completes those items automatically).</p>
 */
@Service
public class TaskNotificationService {

    private final NotificationRepository notificationRepository;
    private final EmployeeRepository employeeRepository;
    private final EmailOutboxService emailOutboxService;
    private final Clock clock;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param notificationRepository persists the in-app notification row(s)
     * @param employeeRepository     resolves HR/IT_ADMIN tenant-wide recipients
     * @param emailOutboxService     queues the matching e-mail (R-ARC-6)
     * @param clock                  application-wide clock bean
     */
    public TaskNotificationService(final NotificationRepository notificationRepository,
                                    final EmployeeRepository employeeRepository,
                                    final EmailOutboxService emailOutboxService,
                                    final Clock clock) {
        this.notificationRepository = notificationRepository;
        this.employeeRepository = employeeRepository;
        this.emailOutboxService = emailOutboxService;
        this.clock = clock;
    }

    /**
     * G-04: "Every new task creates an in-app notification and an e-mail to its owner."
     * Call this once, right after persisting a new {@code checklist_item} or {@code engine_task} row.
     *
     * @param ownerRole        who owns the new task
     * @param subjectEmployee  the employee the task is about (e.g. the new hire being onboarded)
     * @param taskTitle        the task/checklist-item name, shown in the notification and e-mail
     * @param linkRoute        the frontend route the notification should open (may be null)
     */
    @Transactional
    public void notifyTaskAssigned(final OwnerRole ownerRole, final Employee subjectEmployee,
                                    final String taskTitle, final String linkRoute) {
        final String title = "New task: " + taskTitle;
        final String subject = "New task: " + taskTitle;
        notifyRecipients(ownerRole, subjectEmployee, title, linkRoute, "TASK_ASSIGNED", subject,
                recipientFirstName -> "Hi " + recipientFirstName + ",\n\nA new task \"" + taskTitle
                        + "\" has been assigned to you.");
    }

    /**
     * G-05: "An overdue reminder e-mail is sent 1 day after the due date (configurable, FRD-08)."
     * Call this once per remindable task, from {@link com.cubeworks.hr.task.OverdueReminderScheduler}.
     *
     * @param ownerRole        who owns the overdue task
     * @param subjectEmployee  the employee the task is about
     * @param taskTitle        the task/checklist-item name
     * @param linkRoute        the frontend route the notification should open (may be null)
     */
    @Transactional
    public void notifyOverdue(final OwnerRole ownerRole, final Employee subjectEmployee,
                               final String taskTitle, final String linkRoute) {
        final String title = "Overdue: " + taskTitle;
        final String subject = "Overdue: " + taskTitle;
        notifyRecipients(ownerRole, subjectEmployee, title, linkRoute, "TASK_OVERDUE", subject,
                recipientFirstName -> "Hi " + recipientFirstName + ",\n\nThe task \"" + taskTitle
                        + "\" is now overdue.");
    }

    // ── Shared recipient resolution ──────────────────────────────────────────────

    private void notifyRecipients(final OwnerRole ownerRole, final Employee subjectEmployee,
                                   final String notificationTitle, final String linkRoute,
                                   final String templateCode, final String subject,
                                   final java.util.function.Function<String, String> bodyForRecipient) {
        final List<Employee> recipients = resolveRecipients(ownerRole, subjectEmployee);
        final OffsetDateTime now = OffsetDateTime.now(clock);

        for (final Employee recipient : recipients) {
            final Notification notification = new Notification();
            notification.setRecipient(recipient);
            notification.setTitle(notificationTitle);
            notification.setLinkRoute(linkRoute);
            notification.setEmployee(subjectEmployee);
            notification.setCreatedAt(now);
            notification.setUpdatedAt(now);
            notificationRepository.save(notification);

            if (recipient.getPrivateEmail() != null) {
                emailOutboxService.enqueue(templateCode, recipient.getPrivateEmail(), subject,
                        bodyForRecipient.apply(recipient.getFirstName()), subjectEmployee);
            }
        }
    }

    /**
     * Resolves which employees should be notified for a given owner role.
     *
     * <p>HR and IT_ADMIN are tenant-wide roles in this data model (there is no concept of
     * "my specific HR/IT person" per employee), so every employee currently holding that
     * app role is notified. MANAGER resolves to the subject's own line manager (WS-02).
     * EMPLOYEE resolves to the subject themself. SYSTEM has no human recipient.</p>
     *
     * @param ownerRole       the task's owner role
     * @param subjectEmployee the employee the task is about
     * @return the employees to notify (never null, may be empty)
     */
    private List<Employee> resolveRecipients(final OwnerRole ownerRole, final Employee subjectEmployee) {
        return switch (ownerRole) {
            case HR -> employeeRepository.findByAppRole(AppRole.HR);
            case IT_ADMIN -> employeeRepository.findByAppRole(AppRole.IT_ADMIN);
            case MANAGER -> subjectEmployee.getManager() == null
                    ? List.of() : List.of(subjectEmployee.getManager());
            case EMPLOYEE -> List.of(subjectEmployee);
            case SYSTEM -> List.of();
        };
    }
}
