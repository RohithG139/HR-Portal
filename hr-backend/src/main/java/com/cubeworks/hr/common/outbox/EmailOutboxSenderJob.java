package com.cubeworks.hr.common.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Background job that drains the {@code email_outbox} table and delivers pending e-mails
 * via SMTP (R-ARC-6).
 *
 * <h2>At-least-once delivery</h2>
 * <p>Because e-mail rows are written inside the business transaction and the sender runs
 * separately, a crash between the commit and the SMTP call leaves the row in
 * {@link EmailOutboxStatus#PENDING}. On the next scheduler tick the job picks it up again
 * and retries — guaranteeing at-least-once delivery.</p>
 *
 * <h2>SMTP send (replaces the Gate 2 stub, found live 2026-10-18)</h2>
 * <p>Sends via Spring's {@link JavaMailSender}, configured in {@code application.yml}
 * (host-mode default: {@code localhost:1025}, Mailcatcher) and overridden to
 * {@code host.docker.internal} for the containerized dev service (repo-root
 * {@code docker-compose.yml}'s {@code hr-backend} environment). {@code toAddress}/
 * {@code subject}/{@code body} are the rendered snapshot already stored on the row — no
 * further template rendering happens here.</p>
 *
 * <h2>Transaction boundary</h2>
 * <p>Each row is processed in its own {@code REQUIRES_NEW} transaction (implicitly via the
 * service method). A failed send increments {@code attempts} and optionally marks the row
 * {@link EmailOutboxStatus#FAILED} — this state change must commit even if the send fails.</p>
 */
@Component
public class EmailOutboxSenderJob {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxSenderJob.class);
    private static final int MAX_ATTEMPTS = 3;

    private final EmailOutboxRepository emailOutboxRepository;
    private final JavaMailSender mailSender;
    private final Clock clock;
    private final String fromAddress;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param emailOutboxRepository repository for reading and updating outbox rows
     * @param mailSender            Spring's SMTP client, configured via {@code spring.mail.*}
     * @param clock                 application-wide clock bean
     * @param fromAddress           the From address stamped on every outgoing e-mail
     *                              ({@code cubeworks.mail.from-address})
     */
    public EmailOutboxSenderJob(final EmailOutboxRepository emailOutboxRepository,
                                final JavaMailSender mailSender,
                                final Clock clock,
                                @Value("${cubeworks.mail.from-address}") final String fromAddress) {
        this.emailOutboxRepository = emailOutboxRepository;
        this.mailSender = mailSender;
        this.clock = clock;
        this.fromAddress = fromAddress;
    }

    /**
     * Polls the outbox every 30 seconds for pending rows and delivers them.
     *
     * <p>Fixed delay (not fixed rate) ensures that if the previous run takes longer than
     * 30 seconds, the next run does not overlap — important because each row is picked up
     * by at most one JVM instance at a time in single-node deployments.</p>
     *
     * <p>Transaction boundary: each call to {@link #processPending()} saves all updates in
     * one transaction so a crash mid-run leaves any partially-processed rows in {@code PENDING}
     * to be picked up on the next tick.</p>
     */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void processPending() {
        // Fetch all pending rows — in production limit this to a batch size (e.g. 50) and
        // loop until empty to avoid large in-memory batches. For gate 2 the volume is tiny.
        final List<EmailOutbox> pending = emailOutboxRepository.findByStatus(EmailOutboxStatus.PENDING);

        for (final EmailOutbox row : pending) {
            final int attempt = row.getAttempts() + 1;
            row.setAttempts(attempt);
            row.setUpdatedAt(OffsetDateTime.now(clock));

            try {
                // Deliberately not logging the recipient address or subject (both personal
                // data — the subject is rendered from the employee's first name) at INFO,
                // which fires on every single send in normal operation, not just on error.
                log.info("[EmailOutboxSenderJob] Sending email id={} template={} (attempt {})",
                        row.getId(), row.getEmailTemplate().getCode(), attempt);

                final SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromAddress);
                message.setTo(row.getToAddress());
                if (row.getCc() != null && !row.getCc().isBlank()) {
                    message.setCc(row.getCc().split("\\s*,\\s*"));
                }
                message.setSubject(row.getSubject());
                message.setText(row.getBody());
                mailSender.send(message);

                row.setStatus(EmailOutboxStatus.SENT);
                row.setSentAt(OffsetDateTime.now(clock));
                log.info("[EmailOutboxSenderJob] Sent email id={}", row.getId());

            } catch (final Exception ex) {
                log.warn("[EmailOutboxSenderJob] Send failed for id={} attempt={}: {}",
                        row.getId(), attempt, ex.getMessage());
                if (attempt >= MAX_ATTEMPTS) {
                    row.setStatus(EmailOutboxStatus.FAILED);
                    log.error("[EmailOutboxSenderJob] Permanently failed email id={} after {} attempts",
                            row.getId(), attempt);
                }
                // If attempt < MAX_ATTEMPTS, leave status as PENDING for next tick.
            }
            emailOutboxRepository.save(row);
        }
    }
}
