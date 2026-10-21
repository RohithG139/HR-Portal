package com.cubeworks.hr.onboarding;

import com.cubeworks.hr.common.outbox.EmailOutbox;
import com.cubeworks.hr.common.outbox.EmailOutboxRepository;
import com.cubeworks.hr.common.outbox.EmailOutboxStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Fallback for {@link OnboardingEmailReleaseService}: releases onboarding e-mails whose case
 * engine callback never arrived (added 2026-10-21).
 *
 * <h2>Why this exists</h2>
 * <p>"Welcome new employee" is written {@link EmailOutboxStatus#HELD} and the Keycloak
 * set-password e-mail is withheld entirely until the case engine's "Release onboarding emails"
 * process task calls back ({@link com.cubeworks.hr.engine.EngineCallbackController}) to confirm
 * the employee's case actually started. If that callback is ever lost — the engine is briefly
 * unreachable when {@code startCase} fires, the process task itself fails, a network partition —
 * nothing else would ever release those e-mails, silently breaking R-ARC-6's "never lost"
 * guarantee for exactly the e-mail a new hire needs to ever log in. This scheduler is that
 * guarantee's other half: past a generous timeout, release anyway.</p>
 *
 * <h2>Cadence and timeout</h2>
 * <p>Polls every 5 minutes; releases rows HELD for more than 10 minutes. Both numbers are
 * deliberately generous — the normal path (engine callback) completes in seconds, so anything
 * still HELD after 10 minutes genuinely indicates the callback was lost, not ordinary latency.
 * A WARN log on every release makes a chronically-lost callback (e.g. a misconfigured process
 * task) visible in practice, not just theoretically recoverable.</p>
 */
@Component
public class OnboardingEmailReleaseScheduler {

    private static final Logger log = LoggerFactory.getLogger(OnboardingEmailReleaseScheduler.class);
    private static final long HELD_TIMEOUT_MINUTES = 10;

    private final EmailOutboxRepository emailOutboxRepository;
    private final OnboardingEmailReleaseService onboardingEmailReleaseService;
    private final Clock clock;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param emailOutboxRepository         finds HELD rows older than the timeout
     * @param onboardingEmailReleaseService shared release logic (also used by the engine callback)
     * @param clock                         application-wide clock bean
     */
    public OnboardingEmailReleaseScheduler(final EmailOutboxRepository emailOutboxRepository,
                                            final OnboardingEmailReleaseService onboardingEmailReleaseService,
                                            final Clock clock) {
        this.emailOutboxRepository = emailOutboxRepository;
        this.onboardingEmailReleaseService = onboardingEmailReleaseService;
        this.clock = clock;
    }

    /**
     * Polls every 5 minutes for HELD outbox rows older than {@link #HELD_TIMEOUT_MINUTES} and
     * releases each one's employee via {@link OnboardingEmailReleaseService} — the same method
     * the engine's own callback calls, so release is idempotent regardless of which caller wins.
     */
    @Scheduled(fixedDelay = 300_000)
    public void releaseStaleHeldEmails() {
        final OffsetDateTime cutoff = OffsetDateTime.now(clock).minusMinutes(HELD_TIMEOUT_MINUTES);
        final List<EmailOutbox> stale =
                emailOutboxRepository.findByStatusAndCreatedAtLessThanEqual(EmailOutboxStatus.HELD, cutoff);

        if (stale.isEmpty()) {
            return; // Nothing to do — avoid unnecessary log noise
        }

        for (final EmailOutbox row : stale) {
            if (row.getEmployee() == null) {
                log.warn("[OnboardingEmailReleaseScheduler] HELD outbox row id={} has no related "
                        + "employee; cannot release", row.getId());
                continue;
            }
            log.warn("[OnboardingEmailReleaseScheduler] Outbox row id={} for employee id={} has been "
                    + "HELD for over {} minutes — the case engine's release callback was likely lost; "
                    + "releasing via fallback", row.getId(), row.getEmployee().getId(), HELD_TIMEOUT_MINUTES);
            try {
                onboardingEmailReleaseService.releaseOnboardingEmails(row.getEmployee());
            } catch (final Exception ex) {
                log.error("[OnboardingEmailReleaseScheduler] Failed to release stale HELD row id={}; "
                        + "will retry on next tick", row.getId(), ex);
            }
        }
    }
}
