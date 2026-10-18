package com.cubeworks.hr.common.outbox;

import jakarta.mail.internet.MimeMessage;
import jakarta.mail.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Properties;

/**
 * No-op {@link JavaMailSender} used in tests (same {@code @Profile("test")} pattern as
 * {@link com.cubeworks.hr.engine.NoOpCaseEngineClient} for {@code CaseEngineClient}).
 *
 * <p>There is no SMTP relay reachable from a Maven test run (Mailcatcher's SMTP port is only
 * reachable from inside the engine stack's own Docker network — see {@code docker-compose.yml}'s
 * {@code engine_stack_network} comment). Without this bean, Spring Boot's auto-configured
 * {@link JavaMailSender} would attempt a real connection on every {@link EmailOutboxSenderJob}
 * tick during every test run and fail; found live, 2026-10-18, that a large accumulated backlog
 * of outbox rows across a shared {@code @SpringBootTest} context could make a single
 * {@code processPending()} invocation run long enough to starve the shared {@code @Scheduled}
 * thread pool and intermittently fail an unrelated test's Awaitility assertion
 * ({@code F01OnboardingTests.completeOnboarding_afterUndoWindow_taskRemovedFromMyTasks}).</p>
 *
 * <p>This bean satisfies the {@code JavaMailSender} dependency under the {@code test} profile
 * so {@link EmailOutboxSenderJob} keeps exercising its real send/retry/status-transition logic
 * in tests — only the actual network call is skipped, mirroring the pre-2026-10-18 stub's
 * behaviour (always succeeds, no I/O).</p>
 */
@Component
@Profile("test")
public class NoOpMailSender implements JavaMailSender {

    private static final Logger log = LoggerFactory.getLogger(NoOpMailSender.class);

    @Override
    public MimeMessage createMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Override
    public MimeMessage createMimeMessage(final InputStream contentStream) {
        throw new UnsupportedOperationException("NoOpMailSender: raw MIME input streams are not used by this app");
    }

    @Override
    public void send(final MimeMessage mimeMessage) throws MailException {
        log.debug("[NoOpMailSender] Skipping real send of MimeMessage (test profile)");
    }

    @Override
    public void send(final MimeMessage... mimeMessages) throws MailException {
        log.debug("[NoOpMailSender] Skipping real send of {} MimeMessage(s) (test profile)", mimeMessages.length);
    }

    @Override
    public void send(final MimeMessagePreparator mimeMessagePreparator) throws MailException {
        log.debug("[NoOpMailSender] Skipping real send via MimeMessagePreparator (test profile)");
    }

    @Override
    public void send(final MimeMessagePreparator... mimeMessagePreparators) throws MailException {
        log.debug("[NoOpMailSender] Skipping real send via {} MimeMessagePreparator(s) (test profile)",
                mimeMessagePreparators.length);
    }

    @Override
    public void send(final SimpleMailMessage simpleMessage) throws MailException {
        log.debug("[NoOpMailSender] Skipping real send of SimpleMailMessage (test profile)");
    }

    @Override
    public void send(final SimpleMailMessage... simpleMessages) throws MailException {
        log.debug("[NoOpMailSender] Skipping real send of {} SimpleMailMessage(s) (test profile)",
                simpleMessages.length);
    }
}
