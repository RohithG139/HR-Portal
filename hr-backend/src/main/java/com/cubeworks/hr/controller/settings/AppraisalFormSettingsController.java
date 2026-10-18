package com.cubeworks.hr.controller.settings;

import com.cubeworks.hr.dto.settings.AppraisalFormCreateRequest;
import com.cubeworks.hr.dto.settings.AppraisalFormDetailDto;
import com.cubeworks.hr.dto.settings.AppraisalFormListDto;
import com.cubeworks.hr.dto.settings.AppraisalFormUpdateRequest;
import com.cubeworks.hr.dto.settings.FormQuestionDto;
import com.cubeworks.hr.dto.settings.FormQuestionRequest;
import com.cubeworks.hr.dto.settings.FormSectionDto;
import com.cubeworks.hr.dto.settings.FormSectionRequest;
import com.cubeworks.hr.security.CurrentUser;
import com.cubeworks.hr.service.settings.AppraisalFormSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing appraisal forms, sections and questions
 * (FRD-08 §6 C14–C24).
 *
 * <p>All endpoints require the {@code HR} role (FRD-08 §2). Controllers stay thin
 * (R-CODE-1): all logic lives in {@link AppraisalFormSettingsService}.</p>
 *
 * <p>Note: C21–C23 (question CRUD) use the path
 * {@code /api/settings/form-sections/{id}/questions} as defined in FRD-08 §4.4 (flat
 * under form-sections, not re-nested under the form).</p>
 */
@RestController
@PreAuthorize("hasRole('HR')")
public class AppraisalFormSettingsController {

    private final AppraisalFormSettingsService appraisalFormSettingsService;
    private final CurrentUser currentUser;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param appraisalFormSettingsService the service holding all form/section/question logic
     * @param currentUser                  resolves the authenticated employee from the JWT
     */
    public AppraisalFormSettingsController(
            final AppraisalFormSettingsService appraisalFormSettingsService,
            final CurrentUser currentUser) {
        this.appraisalFormSettingsService = appraisalFormSettingsService;
        this.currentUser = currentUser;
    }

    // ── Forms (C14–C17, C24) ─────────────────────────────────────────────────

    /**
     * C14 — Lists all appraisal forms with section counts and status.
     *
     * @return 200 with the form list
     */
    @GetMapping("/api/settings/appraisal-forms")
    public ResponseEntity<List<AppraisalFormListDto>> listForms() {
        return ResponseEntity.ok(appraisalFormSettingsService.listAll());
    }

    /**
     * C15 — Creates a new appraisal form in DRAFT status.
     *
     * @param request name and usedFor
     * @return 201 with {@code {id: ...}}
     */
    @PostMapping("/api/settings/appraisal-forms")
    public ResponseEntity<Map<String, Long>> createForm(
            @Valid @RequestBody final AppraisalFormCreateRequest request) {
        final var actor = currentUser.require();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(appraisalFormSettingsService.create(request, actor));
    }

    /**
     * C16 — Returns full form detail with all sections and their questions.
     *
     * @param id the form's surrogate PK
     * @return 200 with the form detail
     */
    @GetMapping("/api/settings/appraisal-forms/{id}")
    public ResponseEntity<AppraisalFormDetailDto> getForm(@PathVariable final Long id) {
        return ResponseEntity.ok(appraisalFormSettingsService.getDetail(id));
    }

    /**
     * C17 — Updates an appraisal form's header fields.
     *
     * <p>Setting {@code status} to {@code ACTIVE} requires at least one section with at
     * least one question ("Add at least one section with a question.").</p>
     *
     * @param id      the form to update
     * @param request updated fields including optimistic-lock version
     * @return 200 with the updated form detail
     */
    @PutMapping("/api/settings/appraisal-forms/{id}")
    public ResponseEntity<AppraisalFormDetailDto> updateForm(
            @PathVariable final Long id,
            @Valid @RequestBody final AppraisalFormUpdateRequest request) {
        final var actor = currentUser.require();
        return ResponseEntity.ok(appraisalFormSettingsService.update(id, request, actor));
    }

    /**
     * C24 — Archives an appraisal form (ADM-04).
     *
     * @param id the form to archive
     * @return 200 on success
     */
    @PostMapping("/api/settings/appraisal-forms/{id}/archive")
    public ResponseEntity<Void> archiveForm(@PathVariable final Long id) {
        final var actor = currentUser.require();
        appraisalFormSettingsService.archive(id, actor);
        return ResponseEntity.ok().build();
    }

    // ── Sections (C18–C20) ───────────────────────────────────────────────────

    /**
     * C18 — Adds a section to an appraisal form.
     *
     * @param id      the owning form's id
     * @param request the section to add
     * @return 201 with the new section (no questions yet)
     */
    @PostMapping("/api/settings/appraisal-forms/{id}/sections")
    public ResponseEntity<FormSectionDto> addSection(
            @PathVariable final Long id,
            @Valid @RequestBody final FormSectionRequest request) {
        final var actor = currentUser.require();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(appraisalFormSettingsService.addSection(id, request, actor));
    }

    /**
     * C19 — Updates a form section's name and reviewer assignments.
     *
     * @param id        the owning form's id
     * @param sectionId the section to update
     * @param request   the updated fields
     * @return 200 with the updated section
     */
    @PutMapping("/api/settings/appraisal-forms/{id}/sections/{sectionId}")
    public ResponseEntity<FormSectionDto> updateSection(
            @PathVariable final Long id,
            @PathVariable final Long sectionId,
            @Valid @RequestBody final FormSectionRequest request) {
        final var actor = currentUser.require();
        return ResponseEntity.ok(
                appraisalFormSettingsService.updateSection(id, sectionId, request, actor));
    }

    /**
     * C20 — Deletes a form section (cascades its questions).
     *
     * @param id        the owning form's id
     * @param sectionId the section to delete
     * @return 204 on success
     */
    @DeleteMapping("/api/settings/appraisal-forms/{id}/sections/{sectionId}")
    public ResponseEntity<Void> deleteSection(
            @PathVariable final Long id,
            @PathVariable final Long sectionId) {
        final var actor = currentUser.require();
        appraisalFormSettingsService.deleteSection(id, sectionId, actor);
        return ResponseEntity.noContent().build();
    }

    // ── Questions (C21–C23) — flat under /form-sections/{id}/questions ────────

    /**
     * C21 — Adds a question to a form section.
     *
     * <p>Path is flat under {@code /form-sections} per FRD-08 §4.4 (not re-nested
     * under the form).</p>
     *
     * @param id      the owning section's id
     * @param request the question to add
     * @return 201 with the new question
     */
    @PostMapping("/api/settings/form-sections/{id}/questions")
    public ResponseEntity<FormQuestionDto> addQuestion(
            @PathVariable final Long id,
            @Valid @RequestBody final FormQuestionRequest request) {
        final var actor = currentUser.require();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(appraisalFormSettingsService.addQuestion(id, request, actor));
    }

    /**
     * C22 — Updates a form question.
     *
     * @param id         the owning section's id
     * @param questionId the question to update
     * @param request    the updated fields
     * @return 200 with the updated question
     */
    @PutMapping("/api/settings/form-sections/{id}/questions/{questionId}")
    public ResponseEntity<FormQuestionDto> updateQuestion(
            @PathVariable final Long id,
            @PathVariable final Long questionId,
            @Valid @RequestBody final FormQuestionRequest request) {
        final var actor = currentUser.require();
        return ResponseEntity.ok(
                appraisalFormSettingsService.updateQuestion(id, questionId, request, actor));
    }

    /**
     * C23 — Deletes a form question.
     *
     * @param id         the owning section's id
     * @param questionId the question to delete
     * @return 204 on success
     */
    @DeleteMapping("/api/settings/form-sections/{id}/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(
            @PathVariable final Long id,
            @PathVariable final Long questionId) {
        final var actor = currentUser.require();
        appraisalFormSettingsService.deleteQuestion(id, questionId, actor);
        return ResponseEntity.noContent().build();
    }
}
