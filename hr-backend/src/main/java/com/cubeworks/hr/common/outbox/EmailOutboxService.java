package com.cubeworks.hr.common.outbox;

import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.settings.EmailTemplate;
import com.cubeworks.hr.settings.EmailTemplateRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Writes e-mail rows to the transactional outbox (R-ARC-6).
 *
 * <h2>Outbox pattern</h2>
 * <p>E-mails are never sent directly from service methods. Instead, a row is inserted into
 * {@code hr.email_outbox} inside the same {@code @Transactional} boundary as the business
 * change that triggered the e-mail. A background job ({@link EmailOutboxSenderJob}) picks up
 * {@link EmailOutboxStatus#PENDING} rows and delivers them via SMTP. If the SMTP call fails
 * the job retries up to a configured limit and then marks the row {@link EmailOutboxStatus#FAILED}.</p>
 *
 * <h2>Calling convention</h2>
 * <p>Call {@link #enqueue} inside the business transaction — never after the commit. If the
 * transaction rolls back, the outbox row disappears and the e-mail is never sent. This is
 * correct: if the business change did not happen, the notification should not go out either.</p>
 */
@Service
public class EmailOutboxService {

    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final Clock clock;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param emailOutboxRepository   persists outbox entries
     * @param emailTemplateRepository looks up the email template for rendering
     * @param clock                   application-wide clock bean (never call OffsetDateTime.now() directly)
     */
    public EmailOutboxService(final EmailOutboxRepository emailOutboxRepository,
                              final EmailTemplateRepository emailTemplateRepository,
                              final Clock clock) {
        this.emailOutboxRepository = emailOutboxRepository;
        this.emailTemplateRepository = emailTemplateRepository;
        this.clock = clock;
    }

    /**
     * Queues an e-mail for delivery by the background sender job.
     *
     * <p>Must be called inside the same {@code @Transactional} method as the business event.
     * Atomicity: both the business row and this outbox row are committed together — if the
     * transaction rolls back, neither is persisted and no e-mail is sent (R-ARC-6).</p>
     *
     * @param templateCode the {@code email_template.code} value that defines subject and body
     * @param toAddress    the resolved recipient e-mail address
     * @param subject      the rendered subject line (substituted with actual values)
     * @param body         the rendered body text (substituted with actual values)
     * @param relatedEmployee the employee this e-mail is about (for audit queries); may be null
     */
    public void enqueue(final String templateCode,
                        final String toAddress,
                        final String subject,
                        final String body,
                        final Employee relatedEmployee) {

        final EmailTemplate template = emailTemplateRepository.findByCode(templateCode)
                .orElseThrow(() -> new IllegalStateException(
                        "Email template not found: " + templateCode));

        final OffsetDateTime now = OffsetDateTime.now(clock);
        final EmailOutbox entry = new EmailOutbox();
        entry.setEmailTemplate(template);
        entry.setToAddress(toAddress);
        entry.setSubject(subject);
        entry.setBody(body);
        entry.setStatus(EmailOutboxStatus.PENDING);
        entry.setAttempts(0);
        entry.setEmployee(relatedEmployee);
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);

        emailOutboxRepository.save(entry);
    }
}
