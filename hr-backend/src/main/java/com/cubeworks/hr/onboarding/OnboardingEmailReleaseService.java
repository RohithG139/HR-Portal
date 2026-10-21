package com.cubeworks.hr.onboarding;

import com.cubeworks.hr.common.outbox.EmailOutbox;
import com.cubeworks.hr.common.outbox.EmailOutboxRepository;
import com.cubeworks.hr.common.outbox.EmailOutboxStatus;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.employee.EmployeeRepository;
import com.cubeworks.hr.idp.KeycloakAdminClient;
import com.cubeworks.hr.settings.SettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Releases the onboarding e-mails that {@link OnboardingService#createOnboarding} deliberately
 * withholds — "Welcome new employee" ({@link EmailOutboxStatus#HELD} in the outbox) and the
 * Keycloak "set your password" e-mail (not queued at all until this point).
 *
 * <h2>Why these are separate from the rest of onboarding's e-mails</h2>
 * <p>Added 2026-10-21 so the case engine, not Spring's own assumption that case-start will
 * succeed, is the genuine trigger for when these two e-mails go out. Two callers invoke this
 * same idempotent method:</p>
 * <ul>
 *   <li>{@link com.cubeworks.hr.engine.EngineCallbackController} — the primary path. The case
 *       engine's "Release onboarding emails" process task fires the moment the employee's case
 *       actually starts and calls back here with the employee code.</li>
 *   <li>{@link OnboardingEmailReleaseScheduler} — the fallback. If that callback never arrives
 *       (engine down, process task failed, network partition), e-mails are never silently lost:
 *       the row is still durably queued (R-ARC-6), just released later than intended.</li>
 * </ul>
 *
 * <h2>Idempotency</h2>
 * <p>Both callers may race or retry. The HELD → PENDING release is naturally idempotent (a
 * second call simply finds no HELD rows left). The Keycloak set-password e-mail is gated on
 * that same "were there HELD rows to release" signal rather than a separate flag, so calling
 * this method twice for the same employee sends the set-password e-mail at most once — deliberate
 * simplicity (CLAUDE.md R-CODE-4): a dedicated idempotency column would be over-engineering for a
 * race window that is normally seconds and, worst case via the fallback scheduler, minutes.</p>
 */
@Service
public class OnboardingEmailReleaseService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingEmailReleaseService.class);

    private final EmployeeRepository employeeRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final SettingRepository settingRepository;
    private final Clock clock;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param employeeRepository    looks up the employee by employee code (the case file's
     *                              {@code Employee.employeeId}, bound from {@code employee_code})
     * @param emailOutboxRepository finds and releases the employee's HELD outbox rows
     * @param keycloakAdminClient   sends the "set your password" e-mail via Keycloak's own SMTP
     * @param settingRepository     reads {@code SET_PASSWORD_LINK_DAYS} (R-ARC-7)
     * @param clock                 application-wide clock bean
     */
    public OnboardingEmailReleaseService(final EmployeeRepository employeeRepository,
                                          final EmailOutboxRepository emailOutboxRepository,
                                          final KeycloakAdminClient keycloakAdminClient,
                                          final SettingRepository settingRepository,
                                          final Clock clock) {
        this.employeeRepository = employeeRepository;
        this.emailOutboxRepository = emailOutboxRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.settingRepository = settingRepository;
        this.clock = clock;
    }

    /**
     * Looks up the employee by employee code and releases their onboarding e-mails.
     *
     * <p>Unknown employee codes are logged and ignored rather than thrown — the caller is an
     * external system (the case engine's HTTP callback, or a timed-out fallback row), and an
     * employee code that no longer resolves (e.g. test data cleanup) should not raise an error
     * for either caller to handle.</p>
     *
     * @param employeeCode the case file's {@code Employee.employeeId} (= {@code employee_code})
     */
    @Transactional
    public void releaseOnboardingEmails(final String employeeCode) {
        final Employee employee = employeeRepository.findByEmployeeCode(employeeCode).orElse(null);
        if (employee == null) {
            log.warn("[OnboardingEmailReleaseService] No employee found for code={}; nothing to release",
                    employeeCode);
            return;
        }
        releaseOnboardingEmails(employee);
    }

    /**
     * Releases the given employee's HELD onboarding outbox rows and, the first time this fires
     * for them, triggers the Keycloak set-password e-mail.
     *
     * @param employee the employee whose onboarding e-mails to release
     */
    @Transactional
    public void releaseOnboardingEmails(final Employee employee) {
        final OffsetDateTime now = OffsetDateTime.now(clock);

        final List<EmailOutbox> held =
                emailOutboxRepository.findByEmployeeIdAndStatus(employee.getId(), EmailOutboxStatus.HELD);
        for (final EmailOutbox row : held) {
            row.setStatus(EmailOutboxStatus.PENDING);
            row.setUpdatedAt(now);
            emailOutboxRepository.save(row);
        }

        // Gated on "were there HELD rows" rather than a separate idempotency flag (see class doc):
        // a second call for the same employee finds nothing HELD and is a no-op end to end.
        if (held.isEmpty()) {
            log.debug("[OnboardingEmailReleaseService] No HELD rows for employee id={}; already released",
                    employee.getId());
            return;
        }

        log.info("[OnboardingEmailReleaseService] Released {} onboarding e-mail(s) for employee id={}",
                held.size(), employee.getId());

        if (employee.getIdpSubject() != null) {
            // R-ARC-7: the link lifespan comes from `setting`, never a fixed constant.
            final int linkDays = settingRepository.findById("SET_PASSWORD_LINK_DAYS")
                    .map(s -> s.getValueInt() != null ? s.getValueInt() : 7).orElse(7);
            try {
                keycloakAdminClient.sendSetPasswordEmail(employee.getIdpSubject(), linkDays * 86400);
            } catch (final Exception ex) {
                // Same fallback as before this change: the account exists (idp_subject already
                // set), so HR can resend the invite (A34) if this fails.
                log.error("[OnboardingEmailReleaseService] Keycloak set-password e-mail failed for "
                        + "employee id={}; HR can resend via A34", employee.getId(), ex);
            }
        }
    }
}
