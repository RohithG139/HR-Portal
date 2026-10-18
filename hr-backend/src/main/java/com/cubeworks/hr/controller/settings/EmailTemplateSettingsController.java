package com.cubeworks.hr.controller.settings;

import com.cubeworks.hr.dto.settings.EmailTemplateDetailDto;
import com.cubeworks.hr.dto.settings.EmailTemplateListDto;
import com.cubeworks.hr.dto.settings.EmailTemplateUpdateRequest;
import com.cubeworks.hr.security.CurrentUser;
import com.cubeworks.hr.service.settings.EmailTemplateSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing e-mail templates and sending test e-mails
 * (FRD-08 §6 C25–C28).
 *
 * <p>All endpoints require the {@code HR} role (FRD-08 §2). Templates are identified by
 * their immutable {@code code} (ADM-01), not a numeric id. Controllers stay thin
 * (R-CODE-1): all logic lives in {@link EmailTemplateSettingsService}.</p>
 */
@RestController
@RequestMapping("/api/settings/email-templates")
@PreAuthorize("hasRole('HR')")
public class EmailTemplateSettingsController {

    private final EmailTemplateSettingsService emailTemplateSettingsService;
    private final CurrentUser currentUser;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param emailTemplateSettingsService the service holding all e-mail template logic
     * @param currentUser                  resolves the authenticated employee from the JWT
     */
    public EmailTemplateSettingsController(
            final EmailTemplateSettingsService emailTemplateSettingsService,
            final CurrentUser currentUser) {
        this.emailTemplateSettingsService = emailTemplateSettingsService;
        this.currentUser = currentUser;
    }

    /**
     * C25 — Lists all 21 e-mail templates.
     *
     * @return 200 with the template list (code, name, trigger description, recipients summary, active)
     */
    @GetMapping
    public ResponseEntity<List<EmailTemplateListDto>> listTemplates() {
        return ResponseEntity.ok(emailTemplateSettingsService.listAll());
    }

    /**
     * C26 — Returns full detail for a single e-mail template by code.
     *
     * <p>The {@code code} is the URL path key (ADM-01: it is never editable).</p>
     *
     * @param code the machine-readable template code (e.g. "WELCOME_NEW_EMPLOYEE")
     * @return 200 with the full template detail
     */
    @GetMapping("/{code}")
    public ResponseEntity<EmailTemplateDetailDto> getTemplate(@PathVariable final String code) {
        return ResponseEntity.ok(emailTemplateSettingsService.getByCode(code));
    }

    /**
     * C27 — Updates an e-mail template's editable fields.
     *
     * <p>The {@code code} is taken from the URL (never from the request body). Returns
     * {@code 400} if no recipient is selected or if CC contains invalid addresses.</p>
     *
     * @param code    the template code
     * @param request the updated fields including optimistic-lock version
     * @return 200 with the updated template detail
     */
    @PutMapping("/{code}")
    public ResponseEntity<EmailTemplateDetailDto> updateTemplate(
            @PathVariable final String code,
            @Valid @RequestBody final EmailTemplateUpdateRequest request) {
        final var actor = currentUser.require();
        return ResponseEntity.ok(emailTemplateSettingsService.update(code, request, actor));
    }

    /**
     * C28 — Sends a test e-mail of the template to the requesting HR user.
     *
     * <p>Renders the current saved subject/body with sample placeholder data and writes
     * to {@code email_outbox} (R-ARC-6). The test e-mail is delivered via the existing
     * Mailcatcher SMTP path in dev.</p>
     *
     * @param code the template code to test-send
     * @return 202 Accepted (the e-mail is queued, not delivered synchronously)
     */
    @PostMapping("/{code}/test")
    public ResponseEntity<Void> testSend(@PathVariable final String code) {
        final var actor = currentUser.require();
        emailTemplateSettingsService.testSend(code, actor);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
