package com.cubeworks.hr.service.settings;

import com.cubeworks.hr.common.audit.AuditLogService;
import com.cubeworks.hr.common.error.ApiException;
import com.cubeworks.hr.common.error.ErrorCodes;
import com.cubeworks.hr.common.outbox.EmailOutboxService;
import com.cubeworks.hr.dto.settings.EmailTemplateDetailDto;
import com.cubeworks.hr.dto.settings.EmailTemplateListDto;
import com.cubeworks.hr.dto.settings.EmailTemplateUpdateRequest;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.settings.EmailTemplate;
import com.cubeworks.hr.settings.EmailTemplateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for managing e-mail templates and sending test e-mails (FRD-08 §6 C25–C28).
 *
 * <p>Business rules enforced here:</p>
 * <ul>
 *   <li>ADM-01: {@code code} is never editable — accepted only in the URL path (C26–C28).</li>
 *   <li>C27: at least one recipient flag must be {@code true} (FRD-08 §5 S5:
 *       "Choose at least one recipient.").</li>
 *   <li>C27: {@code cc} must be empty or contain only valid comma-separated e-mail addresses
 *       (FRD-08 §5 S5: "Enter valid e-mail addresses, separated by commas.").</li>
 *   <li>C28: test send renders the template's current saved values with sample placeholder
 *       data and writes to {@code email_outbox} (R-ARC-6). Sent to the requesting HR user's
 *       own e-mail ({@code work_email} falling back to {@code private_email}).</li>
 *   <li>ADM-05: every write is audited in the same transaction (R-API-5).</li>
 * </ul>
 */
@Service
public class EmailTemplateSettingsService {

    private static final Logger log = LoggerFactory.getLogger(EmailTemplateSettingsService.class);

    /** Simple regex for e-mail address validation (RFC 5322 simplified). */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /**
     * Sample placeholder values for test-send rendering (FRD-08 §7 "Test-send").
     * Matches the placeholder chips shown in the e-mail template body editor wireframe.
     */
    private static final Map<String, String> SAMPLE_PLACEHOLDERS = Map.of(
            "{employee_name}", "Jesse Willems",
            "{employee_first_name}", "Jesse",
            "{manager_name}", "Anouk Peters",
            "{due_date}", LocalDate.of(2027, 1, 31).toString(),
            "{task_name}", "Sample Task",
            "{task_link}", "https://example.com/tasks/1",
            "{start_date}", LocalDate.of(2027, 1, 5).toString(),
            "{last_day}", LocalDate.of(2027, 12, 31).toString());

    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailOutboxService emailOutboxService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param emailTemplateRepository reads/writes {@code hr.email_template}
     * @param emailOutboxService      writes rows to {@code hr.email_outbox} (R-ARC-6)
     * @param auditLogService         writes audit entries (ADM-05)
     * @param objectMapper            serialises before/after snapshots
     * @param clock                   application-wide clock bean
     */
    public EmailTemplateSettingsService(final EmailTemplateRepository emailTemplateRepository,
                                        final EmailOutboxService emailOutboxService,
                                        final AuditLogService auditLogService,
                                        final ObjectMapper objectMapper,
                                        final Clock clock) {
        this.emailTemplateRepository = emailTemplateRepository;
        this.emailOutboxService = emailOutboxService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * C25 — Lists all 21 e-mail templates with a computed recipients summary.
     *
     * @return all templates ordered by name
     */
    public List<EmailTemplateListDto> listAll() {
        return emailTemplateRepository.findAll().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(t -> new EmailTemplateListDto(
                        t.getCode(),
                        t.getName(),
                        t.getTriggerDescription(),
                        buildRecipientsSummary(t),
                        t.isActive()))
                .toList();
    }

    /**
     * C26 — Returns full detail for a single e-mail template by code.
     *
     * @param code the immutable machine-readable code (ADM-01)
     * @return the full template detail including subject, body, all recipient flags
     * @throws ApiException 404 NOT_FOUND if no template with that code exists
     */
    public EmailTemplateDetailDto getByCode(final String code) {
        final EmailTemplate t = requireByCode(code);
        return toDetailDto(t);
    }

    /**
     * C27 — Updates an e-mail template's editable fields.
     *
     * <p>Transaction boundary: the template UPDATE and the audit_log INSERT are committed
     * atomically. The {@code code} is never changed (ADM-01).</p>
     *
     * <p>Optimistic locking: the service compares {@code request.version()} against the
     * entity's loaded version before saving.</p>
     *
     * @param code    the immutable template code
     * @param request the updated fields
     * @param actor   the HR user performing the action
     * @return the updated template detail
     * @throws ApiException 404 NOT_FOUND if no template with that code exists
     * @throws ApiException 409 STALE_VERSION if the client's version is stale
     * @throws ApiException 400 VALIDATION_FAILED for recipient or CC validation failures
     */
    @Transactional
    public EmailTemplateDetailDto update(final String code,
                                         final EmailTemplateUpdateRequest request,
                                         final Employee actor) {
        final EmailTemplate template = requireByCode(code);

        // Optimistic-lock check
        if (!Objects.equals(template.getVersion(), request.version())) {
            throw new ApiException(ErrorCodes.STALE_VERSION, HttpStatus.CONFLICT,
                    "Version conflict — reload and retry");
        }

        // Duplicate-name check: reject if another template already has this name
        emailTemplateRepository.findByName(request.name()).ifPresent(existing -> {
            if (!existing.getCode().equals(code)) {
                throw new ApiException(ErrorCodes.DUPLICATE_NAME, HttpStatus.CONFLICT,
                        "This name already exists.");
            }
        });

        // At least one recipient must be selected
        if (!request.toEmployee() && !request.toManager() && !request.toHr()
                && !request.toItAdmin() && !request.toTaskOwner() && !request.toPeer()) {
            throw ApiException.fieldValidation(List.of(
                    Map.of("field", "toEmployee", "message", "Choose at least one recipient.")));
        }

        // CC validation: each comma-separated entry must be a valid e-mail address
        if (request.cc() != null && !request.cc().isBlank()) {
            validateCc(request.cc());
        }

        final String before = snapshotTemplate(template);
        final OffsetDateTime now = OffsetDateTime.now(clock);

        template.setName(request.name());
        template.setTriggerDescription(request.triggerDescription());
        template.setSubject(request.subject());
        template.setBody(request.body());
        template.setCc(request.cc());
        template.setToEmployee(request.toEmployee());
        template.setToManager(request.toManager());
        template.setToHr(request.toHr());
        template.setToItAdmin(request.toItAdmin());
        template.setToTaskOwner(request.toTaskOwner());
        template.setToPeer(request.toPeer());
        template.setActive(request.active());
        template.setUpdatedAt(now);
        template.setUpdatedBy(actor.getId());
        emailTemplateRepository.save(template);

        auditLogService.record(null, "EMAIL_TEMPLATE", template.getId(), "UPDATE",
                actor, before, snapshotTemplate(template));

        log.info("EmailTemplate updated: code={} by employee={}", code, actor.getId());
        return toDetailDto(template);
    }

    /**
     * C28 — Sends a test e-mail of the specified template to the requesting HR user.
     *
     * <p>Transaction boundary: the {@code email_outbox} INSERT is committed atomically with
     * any other work in this transaction. No audit_log row is written for test sends — they
     * are not a configuration change (ADM-05 only covers changes to settings).</p>
     *
     * <p>The template's current saved subject/body are rendered with fixed sample placeholder
     * data (FRD-08 §7 "Test-send"). The recipient is the HR user's {@code work_email},
     * falling back to {@code private_email} (FRD-08 §12 D-03).</p>
     *
     * @param code  the template code to test-send
     * @param actor the requesting HR user (who receives the test e-mail)
     * @throws ApiException 404 NOT_FOUND if no template with that code exists
     */
    @Transactional
    public void testSend(final String code, final Employee actor) {
        final EmailTemplate template = requireByCode(code);

        final String toAddress = resolveActorEmail(actor);
        final String renderedSubject = renderPlaceholders(template.getSubject());
        final String renderedBody = renderPlaceholders(template.getBody());

        emailOutboxService.enqueue(code, toAddress, renderedSubject, renderedBody, null);

        // Recipient address is deliberately not logged (PII) — same convention as
        // EmailOutboxSenderJob's own INFO logging.
        log.info("Test e-mail queued: templateCode={} by employee={}", code, actor.getId());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private EmailTemplate requireByCode(final String code) {
        return emailTemplateRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Email template not found: " + code));
    }

    private EmailTemplateDetailDto toDetailDto(final EmailTemplate t) {
        return new EmailTemplateDetailDto(
                t.getCode(), t.getName(), t.getTriggerDescription(),
                t.getSubject(), t.getBody(), t.getCc(),
                t.isToEmployee(), t.isToManager(), t.isToHr(),
                t.isToItAdmin(), t.isToTaskOwner(), t.isToPeer(),
                t.isActive(), t.getVersion());
    }

    /**
     * Builds the comma-separated recipients summary string for the list view.
     * Shows which recipient roles are enabled on this template.
     */
    private String buildRecipientsSummary(final EmailTemplate t) {
        final List<String> recipients = new ArrayList<>();
        if (t.isToEmployee())  recipients.add("Employee");
        if (t.isToManager())   recipients.add("Manager");
        if (t.isToHr())        recipients.add("HR");
        if (t.isToItAdmin())   recipients.add("IT admin");
        if (t.isToTaskOwner()) recipients.add("Task owner");
        if (t.isToPeer())      recipients.add("Peer");
        return String.join(", ", recipients);
    }

    /**
     * Validates that {@code cc} is a non-empty comma-separated list of valid e-mail addresses.
     *
     * @throws ApiException 400 VALIDATION_FAILED with message "Enter valid e-mail addresses,
     *                      separated by commas." if any address is invalid
     */
    private void validateCc(final String cc) {
        final String[] parts = cc.split(",");
        for (final String part : parts) {
            final String trimmed = part.trim();
            if (trimmed.isEmpty() || !EMAIL_PATTERN.matcher(trimmed).matches()) {
                throw ApiException.fieldValidation(List.of(
                        Map.of("field", "cc",
                                "message", "Enter valid e-mail addresses, separated by commas.")));
            }
        }
    }

    /**
     * Substitutes all known placeholder chips in the given text with sample data values.
     *
     * @param text the subject or body text that may contain {@code {placeholder}} tokens
     * @return the text with all known placeholders replaced
     */
    private String renderPlaceholders(final String text) {
        String result = text;
        for (final Map.Entry<String, String> entry : SAMPLE_PLACEHOLDERS.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * Resolves the e-mail address to send a test e-mail to.
     * Prefers work_email, falls back to private_email (FRD-08 §12 D-03).
     */
    private String resolveActorEmail(final Employee actor) {
        if (actor.getWorkEmail() != null && !actor.getWorkEmail().isBlank()) {
            return actor.getWorkEmail();
        }
        return actor.getPrivateEmail();
    }

    private String snapshotTemplate(final EmailTemplate t) {
        try {
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", t.getCode());
            m.put("name", t.getName());
            m.put("triggerDescription", t.getTriggerDescription());
            m.put("subject", t.getSubject());
            m.put("body", t.getBody());
            m.put("cc", t.getCc());
            m.put("active", t.isActive());
            m.put("toEmployee", t.isToEmployee());
            m.put("toManager", t.isToManager());
            m.put("toHr", t.isToHr());
            m.put("toItAdmin", t.isToItAdmin());
            m.put("toTaskOwner", t.isToTaskOwner());
            m.put("toPeer", t.isToPeer());
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise email template snapshot", e);
            return "{}";
        }
    }
}
