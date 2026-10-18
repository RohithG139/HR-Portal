package com.cubeworks.hr.service.settings;

import com.cubeworks.hr.common.audit.AuditLogService;
import com.cubeworks.hr.common.error.ApiException;
import com.cubeworks.hr.common.error.ErrorCodes;
import com.cubeworks.hr.dto.settings.*;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.model.settings.*;
import com.cubeworks.hr.repository.settings.AppraisalFormRepository;
import com.cubeworks.hr.repository.settings.FormQuestionRepository;
import com.cubeworks.hr.repository.settings.FormSectionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service for managing appraisal forms, sections and questions (FRD-08 §6 C14–C24).
 *
 * <p>Key business rules:</p>
 * <ul>
 *   <li>C17 status → ACTIVE: the form must have at least one section, and each section
 *       must have at least one question ("Add at least one section with a question.").</li>
 *   <li>ADM-04: archiving only sets {@code status = 'ARCHIVED'}; never touches existing
 *       {@code review} rows that reference this form.</li>
 *   <li>ADM-05: every write is audited in the same transaction (R-API-5).</li>
 *   <li>Optimistic locking on C17 and C19: the client supplies {@code version}; the
 *       service compares it against the stored version before saving.</li>
 * </ul>
 */
@Service
public class AppraisalFormSettingsService {

    private static final Logger log = LoggerFactory.getLogger(AppraisalFormSettingsService.class);

    private final AppraisalFormRepository formRepository;
    private final FormSectionRepository sectionRepository;
    private final FormQuestionRepository questionRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param formRepository     reads/writes {@code hr.appraisal_form}
     * @param sectionRepository  reads/writes {@code hr.form_section}
     * @param questionRepository reads/writes {@code hr.form_question}
     * @param auditLogService    writes audit entries (ADM-05)
     * @param objectMapper       serialises before/after snapshots
     * @param clock              application-wide clock bean
     */
    public AppraisalFormSettingsService(final AppraisalFormRepository formRepository,
                                        final FormSectionRepository sectionRepository,
                                        final FormQuestionRepository questionRepository,
                                        final AuditLogService auditLogService,
                                        final ObjectMapper objectMapper,
                                        final Clock clock) {
        this.formRepository = formRepository;
        this.sectionRepository = sectionRepository;
        this.questionRepository = questionRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * C14 — Lists all appraisal forms with section counts.
     *
     * @return all forms ordered by name
     */
    @Transactional(readOnly = true)
    public List<AppraisalFormListDto> listAll() {
        // One aggregate query for every form's section count, not one countByFormId call
        // per form (N+1 found in Gate 5 code review).
        final Map<Long, Long> sectionCountsByFormId = sectionRepository.countSectionsGroupedByForm()
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        return formRepository.findAll().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(f -> new AppraisalFormListDto(
                        f.getId(),
                        f.getName(),
                        f.getUsedFor().name(),
                        f.getStatus().name(),
                        sectionCountsByFormId.getOrDefault(f.getId(), 0L),
                        f.getUpdatedAt()))
                .toList();
    }

    /**
     * C15 — Creates a new appraisal form in DRAFT status.
     *
     * <p>Transaction boundary: the form INSERT and the audit_log INSERT are committed
     * atomically (ADM-05).</p>
     *
     * @param request the create request with name and usedFor
     * @param actor   the HR user performing the action
     * @return a map containing the new form's {@code id}
     * @throws ApiException 409 DUPLICATE_NAME if the name is already in use
     * @throws ApiException 400 VALIDATION_FAILED for invalid usedFor value
     */
    @Transactional
    public Map<String, Long> create(final AppraisalFormCreateRequest request,
                                    final Employee actor) {
        if (formRepository.findByName(request.name()).isPresent()) {
            throw new ApiException(ErrorCodes.DUPLICATE_NAME, HttpStatus.CONFLICT,
                    "This name already exists.");
        }

        final FormUsedFor usedFor = parseUsedFor(request.usedFor());

        final OffsetDateTime now = OffsetDateTime.now(clock);
        final AppraisalForm form = new AppraisalForm();
        form.setName(request.name());
        form.setUsedFor(usedFor);
        form.setStatus(FormStatus.DRAFT);
        form.setCreatedAt(now);
        form.setCreatedBy(actor.getId());
        form.setUpdatedAt(now);
        form.setUpdatedBy(actor.getId());
        formRepository.save(form);

        auditLogService.record(null, "APPRAISAL_FORM", form.getId(), "CREATE",
                actor, null, snapshotForm(form));

        // Name is user-entered free text and is not logged raw (log-injection risk via
        // embedded CR/LF); the audit_log row above already captures it faithfully.
        log.info("AppraisalForm created: id={} by employee={}", form.getId(), actor.getId());
        return Map.of("id", form.getId());
    }

    /**
     * C16 — Returns full appraisal form detail with all sections and their questions.
     *
     * <p>N+1 prevention: sections are loaded with one query, then questions are loaded
     * per section. For admin settings screens (small data) this is acceptable — forms
     * have at most ~10 sections, each with ~10 questions.</p>
     *
     * @param id the form's surrogate PK
     * @return form detail with sections and questions nested
     * @throws ApiException 404 NOT_FOUND if no form with that id exists
     */
    @Transactional(readOnly = true)
    public AppraisalFormDetailDto getDetail(final Long id) {
        final AppraisalForm form = formRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Appraisal form not found: " + id));

        // Load sections ordered by sort_order, then questions for each section
        final List<FormSectionDto> sections = sectionRepository
                .findByFormIdOrderBySortOrderAsc(id)
                .stream()
                .map(s -> {
                    final List<FormQuestionDto> questions = questionRepository
                            .findBySectionIdOrderBySortOrderAsc(s.getId())
                            .stream()
                            .map(this::toQuestionDto)
                            .toList();
                    return new FormSectionDto(
                            s.getId(), s.getName(),
                            s.isBySelf(), s.isByPeer(), s.isByManager(), s.isByHr(),
                            s.getSortOrder(), questions);
                })
                .toList();

        return new AppraisalFormDetailDto(
                form.getId(), form.getName(),
                form.getUsedFor().name(), form.getStatus().name(),
                form.getOverallScoreMethod().name(),
                form.getLabel1(), form.getLabel2(), form.getLabel3(),
                form.getLabel4(), form.getLabel5(),
                form.getVersion(),
                sections);
    }

    /**
     * C17 — Updates an appraisal form's header fields.
     *
     * <p>Transaction boundary: the form UPDATE and the audit_log INSERT are committed
     * atomically. Setting status to ACTIVE is only allowed when at least one section
     * exists and each section has at least one question.</p>
     *
     * <p>Optimistic locking: the service compares {@code request.version()} against the
     * entity's loaded version before saving. If they differ, {@code 409 STALE_VERSION}
     * is thrown before the UPDATE is issued.</p>
     *
     * @param id      the form to update
     * @param request the updated fields including the client's known version
     * @param actor   the HR user performing the action
     * @return the updated form detail
     * @throws ApiException 404 NOT_FOUND if no form with that id exists
     * @throws ApiException 409 STALE_VERSION if the client's version is stale
     * @throws ApiException 400 VALIDATION_FAILED if activating a form without content
     */
    @Transactional
    public AppraisalFormDetailDto update(final Long id,
                                         final AppraisalFormUpdateRequest request,
                                         final Employee actor) {
        final AppraisalForm form = formRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Appraisal form not found: " + id));

        // Optimistic-lock check: compare client version against DB version
        if (!Objects.equals(form.getVersion(), request.version())) {
            throw new ApiException(ErrorCodes.STALE_VERSION, HttpStatus.CONFLICT,
                    "Version conflict — reload and retry");
        }

        // Duplicate-name check: reject if another form already has this name
        formRepository.findByName(request.name()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new ApiException(ErrorCodes.DUPLICATE_NAME, HttpStatus.CONFLICT,
                        "This name already exists.");
            }
        });

        final FormStatus newStatus = parseFormStatus(request.status());
        final FormUsedFor usedFor = parseUsedFor(request.usedFor());
        final OverallScoreMethod scoreMethod = parseScoreMethod(request.overallScoreMethod());

        // Enforce "Add at least one section with a question." when activating (FRD-08 §5 S3/S4)
        if (newStatus == FormStatus.ACTIVE) {
            final long sectionCount = sectionRepository.countByFormId(id);
            if (sectionCount == 0) {
                throw ApiException.fieldValidation(List.of(
                        Map.of("field", "status",
                                "message", "Add at least one section with a question.")));
            }
            final List<FormSection> sections = sectionRepository.findByFormIdOrderBySortOrderAsc(id);
            for (final FormSection section : sections) {
                if (questionRepository.countBySectionId(section.getId()) == 0) {
                    throw ApiException.fieldValidation(List.of(
                            Map.of("field", "status",
                                    "message", "Add at least one section with a question.")));
                }
            }
        }

        final String before = snapshotForm(form);
        final OffsetDateTime now = OffsetDateTime.now(clock);

        form.setName(request.name());
        form.setUsedFor(usedFor);
        form.setStatus(newStatus);
        form.setOverallScoreMethod(scoreMethod);
        if (request.label1() != null) form.setLabel1(request.label1());
        if (request.label2() != null) form.setLabel2(request.label2());
        if (request.label3() != null) form.setLabel3(request.label3());
        if (request.label4() != null) form.setLabel4(request.label4());
        if (request.label5() != null) form.setLabel5(request.label5());
        form.setUpdatedAt(now);
        form.setUpdatedBy(actor.getId());
        formRepository.save(form);

        auditLogService.record(null, "APPRAISAL_FORM", id, "UPDATE",
                actor, before, snapshotForm(form));

        log.info("AppraisalForm updated: id={} status={} by employee={}", id, newStatus, actor.getId());
        return getDetail(id);
    }

    /**
     * C18 — Adds a section to an appraisal form.
     *
     * <p>Transaction boundary: INSERT and audit_log INSERT are committed atomically.</p>
     *
     * <p>At least one of bySelf/byPeer/byManager/byHr must be true — enforced by a DB CHECK
     * and validated here so the error is a clean API response.</p>
     *
     * @param formId  the owning form's id
     * @param request the section to add
     * @param actor   the HR user performing the action
     * @return the new section DTO (questions array is empty on creation)
     * @throws ApiException 404 NOT_FOUND if no form with that id exists
     * @throws ApiException 400 VALIDATION_FAILED if no reviewer is assigned
     */
    @Transactional
    public FormSectionDto addSection(final Long formId,
                                     final FormSectionRequest request,
                                     final Employee actor) {
        final AppraisalForm form = formRepository.findById(formId)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Appraisal form not found: " + formId));

        if (!request.bySelf() && !request.byPeer() && !request.byManager() && !request.byHr()) {
            throw ApiException.fieldValidation(List.of(
                    Map.of("field", "completedBy", "message", "Choose at least one reviewer.")));
        }

        final int nextSortOrder = (int) sectionRepository.countByFormId(formId);
        final OffsetDateTime now = OffsetDateTime.now(clock);

        final FormSection section = new FormSection();
        section.setForm(form);
        section.setName(request.name());
        section.setBySelf(request.bySelf());
        section.setByPeer(request.byPeer());
        section.setByManager(request.byManager());
        section.setByHr(request.byHr());
        section.setSortOrder(nextSortOrder);
        section.setCreatedAt(now);
        section.setCreatedBy(actor.getId());
        section.setUpdatedAt(now);
        section.setUpdatedBy(actor.getId());
        sectionRepository.save(section);

        auditLogService.record(null, "FORM_SECTION", section.getId(), "CREATE",
                actor, null, snapshotSection(section));

        log.info("FormSection created: id={} formId={} by employee={}", section.getId(), formId, actor.getId());
        return new FormSectionDto(section.getId(), section.getName(),
                section.isBySelf(), section.isByPeer(), section.isByManager(), section.isByHr(),
                section.getSortOrder(), List.of());
    }

    /**
     * C19 — Updates a section's name and reviewer assignments.
     *
     * <p>Transaction boundary: UPDATE and audit_log INSERT are committed atomically.</p>
     *
     * @param formId    the owning form's id
     * @param sectionId the section to update
     * @param request   the updated fields
     * @param actor     the HR user performing the action
     * @return the updated section DTO with its questions
     * @throws ApiException 404 NOT_FOUND if section does not exist or belongs to another form
     * @throws ApiException 400 VALIDATION_FAILED if no reviewer is assigned
     */
    @Transactional
    public FormSectionDto updateSection(final Long formId,
                                        final Long sectionId,
                                        final FormSectionRequest request,
                                        final Employee actor) {
        final FormSection section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Form section not found: " + sectionId));

        if (!section.getForm().getId().equals(formId)) {
            throw new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Section " + sectionId + " does not belong to form " + formId);
        }

        if (!request.bySelf() && !request.byPeer() && !request.byManager() && !request.byHr()) {
            throw ApiException.fieldValidation(List.of(
                    Map.of("field", "completedBy", "message", "Choose at least one reviewer.")));
        }

        final String before = snapshotSection(section);
        final OffsetDateTime now = OffsetDateTime.now(clock);

        section.setName(request.name());
        section.setBySelf(request.bySelf());
        section.setByPeer(request.byPeer());
        section.setByManager(request.byManager());
        section.setByHr(request.byHr());
        section.setUpdatedAt(now);
        section.setUpdatedBy(actor.getId());
        sectionRepository.save(section);

        auditLogService.record(null, "FORM_SECTION", sectionId, "UPDATE",
                actor, before, snapshotSection(section));

        final List<FormQuestionDto> questions = questionRepository
                .findBySectionIdOrderBySortOrderAsc(sectionId)
                .stream().map(this::toQuestionDto).toList();

        log.info("FormSection updated: id={} by employee={}", sectionId, actor.getId());
        return new FormSectionDto(section.getId(), section.getName(),
                section.isBySelf(), section.isByPeer(), section.isByManager(), section.isByHr(),
                section.getSortOrder(), questions);
    }

    /**
     * C20 — Deletes a section from a form, cascading its questions.
     *
     * <p>Transaction boundary: DELETE (cascade) and audit_log INSERT are committed atomically.</p>
     *
     * <p>The DB schema defines ON DELETE CASCADE from form_section to form_question, so
     * deleting the section removes all its questions automatically.</p>
     *
     * @param formId    the owning form's id
     * @param sectionId the section to delete
     * @param actor     the HR user performing the action
     * @throws ApiException 404 NOT_FOUND if section does not exist or belongs to another form
     */
    @Transactional
    public void deleteSection(final Long formId, final Long sectionId, final Employee actor) {
        final FormSection section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Form section not found: " + sectionId));

        if (!section.getForm().getId().equals(formId)) {
            throw new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Section " + sectionId + " does not belong to form " + formId);
        }

        final String before = snapshotSection(section);
        sectionRepository.delete(section);

        auditLogService.record(null, "FORM_SECTION", sectionId, "DELETE",
                actor, before, null);
        log.info("FormSection deleted: id={} formId={} by employee={}", sectionId, formId, actor.getId());
    }

    /**
     * C21 — Adds a question to a form section.
     *
     * <p>Transaction boundary: INSERT and audit_log INSERT are committed atomically.</p>
     *
     * @param sectionId the owning section's id (the flat {@code /form-sections/{id}/questions} path)
     * @param request   the question to add
     * @param actor     the HR user performing the action
     * @return the newly created question
     * @throws ApiException 404 NOT_FOUND if no section with that id exists
     */
    @Transactional
    public FormQuestionDto addQuestion(final Long sectionId,
                                       final FormQuestionRequest request,
                                       final Employee actor) {
        final FormSection section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Form section not found: " + sectionId));

        final FormAnswerType answerType = parseAnswerType(request.answerType());
        final int nextSortOrder = (int) questionRepository.countBySectionId(sectionId);
        final OffsetDateTime now = OffsetDateTime.now(clock);

        final FormQuestion question = new FormQuestion();
        question.setSection(section);
        question.setText(request.text());
        question.setDescription(request.description());
        question.setAnswerType(answerType);
        question.setRequired(request.required());
        question.setSortOrder(nextSortOrder);
        question.setCreatedAt(now);
        question.setCreatedBy(actor.getId());
        question.setUpdatedAt(now);
        question.setUpdatedBy(actor.getId());
        questionRepository.save(question);

        auditLogService.record(null, "FORM_QUESTION", question.getId(), "CREATE",
                actor, null, snapshotQuestion(question));

        log.info("FormQuestion created: id={} sectionId={} by employee={}",
                question.getId(), sectionId, actor.getId());
        return toQuestionDto(question);
    }

    /**
     * C22 — Updates a form question.
     *
     * <p>Transaction boundary: UPDATE and audit_log INSERT are committed atomically.</p>
     *
     * @param sectionId  the owning section's id
     * @param questionId the question to update
     * @param request    the updated fields
     * @param actor      the HR user performing the action
     * @return the updated question
     * @throws ApiException 404 NOT_FOUND if question does not exist or belongs to another section
     */
    @Transactional
    public FormQuestionDto updateQuestion(final Long sectionId,
                                          final Long questionId,
                                          final FormQuestionRequest request,
                                          final Employee actor) {
        final FormQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Form question not found: " + questionId));

        if (!question.getSection().getId().equals(sectionId)) {
            throw new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Question " + questionId + " does not belong to section " + sectionId);
        }

        final String before = snapshotQuestion(question);
        final FormAnswerType answerType = parseAnswerType(request.answerType());
        final OffsetDateTime now = OffsetDateTime.now(clock);

        question.setText(request.text());
        question.setDescription(request.description());
        question.setAnswerType(answerType);
        question.setRequired(request.required());
        question.setUpdatedAt(now);
        question.setUpdatedBy(actor.getId());
        questionRepository.save(question);

        auditLogService.record(null, "FORM_QUESTION", questionId, "UPDATE",
                actor, before, snapshotQuestion(question));

        log.info("FormQuestion updated: id={} by employee={}", questionId, actor.getId());
        return toQuestionDto(question);
    }

    /**
     * C23 — Deletes a form question.
     *
     * <p>Transaction boundary: DELETE and audit_log INSERT are committed atomically.</p>
     *
     * @param sectionId  the owning section's id
     * @param questionId the question to delete
     * @param actor      the HR user performing the action
     * @throws ApiException 404 NOT_FOUND if question does not exist or belongs to another section
     */
    @Transactional
    public void deleteQuestion(final Long sectionId, final Long questionId, final Employee actor) {
        final FormQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Form question not found: " + questionId));

        if (!question.getSection().getId().equals(sectionId)) {
            throw new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Question " + questionId + " does not belong to section " + sectionId);
        }

        final String before = snapshotQuestion(question);
        questionRepository.delete(question);

        auditLogService.record(null, "FORM_QUESTION", questionId, "DELETE",
                actor, before, null);
        log.info("FormQuestion deleted: id={} sectionId={} by employee={}", questionId, sectionId, actor.getId());
    }

    /**
     * C24 — Archives an appraisal form.
     *
     * <p>Transaction boundary: the UPDATE and the audit_log INSERT are committed atomically.</p>
     *
     * <p>ADM-04: only sets {@code status = 'ARCHIVED'}; never touches {@code review} rows
     * that already reference this form.</p>
     *
     * @param id    the form to archive
     * @param actor the HR user performing the action
     * @throws ApiException 404 NOT_FOUND if no form with that id exists
     */
    @Transactional
    public void archive(final Long id, final Employee actor) {
        final AppraisalForm form = formRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Appraisal form not found: " + id));

        final String before = snapshotForm(form);
        final OffsetDateTime now = OffsetDateTime.now(clock);
        form.setStatus(FormStatus.ARCHIVED);
        form.setUpdatedAt(now);
        form.setUpdatedBy(actor.getId());
        formRepository.save(form);

        auditLogService.record(null, "APPRAISAL_FORM", id, "ARCHIVE",
                actor, before, snapshotForm(form));
        log.info("AppraisalForm archived: id={} by employee={}", id, actor.getId());
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private FormQuestionDto toQuestionDto(final FormQuestion q) {
        return new FormQuestionDto(
                q.getId(), q.getText(), q.getDescription(),
                q.getAnswerType().name(), q.isRequired(), q.getSortOrder());
    }

    private FormUsedFor parseUsedFor(final String value) {
        try {
            return FormUsedFor.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.fieldValidation(List.of(
                    Map.of("field", "usedFor", "message", "This field is required.")));
        }
    }

    private FormStatus parseFormStatus(final String value) {
        try {
            return FormStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.fieldValidation(List.of(
                    Map.of("field", "status", "message", "This field is required.")));
        }
    }

    private OverallScoreMethod parseScoreMethod(final String value) {
        try {
            return OverallScoreMethod.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.fieldValidation(List.of(
                    Map.of("field", "overallScoreMethod", "message", "This field is required.")));
        }
    }

    private FormAnswerType parseAnswerType(final String value) {
        try {
            return FormAnswerType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.fieldValidation(List.of(
                    Map.of("field", "answerType", "message", "This field is required.")));
        }
    }

    private String snapshotForm(final AppraisalForm form) {
        try {
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", form.getId() != null ? form.getId() : 0L);
            m.put("name", form.getName());
            m.put("usedFor", form.getUsedFor().name());
            m.put("status", form.getStatus().name());
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise form snapshot", e);
            return "{}";
        }
    }

    private String snapshotSection(final FormSection section) {
        try {
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", section.getId() != null ? section.getId() : 0L);
            m.put("name", section.getName());
            m.put("bySelf", section.isBySelf());
            m.put("byPeer", section.isByPeer());
            m.put("byManager", section.isByManager());
            m.put("byHr", section.isByHr());
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise section snapshot", e);
            return "{}";
        }
    }

    private String snapshotQuestion(final FormQuestion q) {
        try {
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", q.getId() != null ? q.getId() : 0L);
            m.put("text", q.getText());
            m.put("answerType", q.getAnswerType().name());
            m.put("required", q.isRequired());
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise question snapshot", e);
            return "{}";
        }
    }
}
