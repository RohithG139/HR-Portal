package com.cubeworks.hr.service.settings;

import com.cubeworks.hr.common.audit.AuditLogService;
import com.cubeworks.hr.common.error.ApiException;
import com.cubeworks.hr.common.error.ErrorCodes;
import com.cubeworks.hr.dto.settings.JobTitleDto;
import com.cubeworks.hr.dto.settings.JobTitleRequest;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.settings.JobTitle;
import com.cubeworks.hr.settings.JobTitleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service for managing job titles in the Settings screen (FRD-08 §6 C04–C06).
 *
 * <p>Business rules enforced here:</p>
 * <ul>
 *   <li>ADM-03: a job title in use by any employee or checklist template cannot be deleted;
 *       the FK {@code RESTRICT} surfaces as {@link DataIntegrityViolationException}.</li>
 *   <li>Duplicate name: checked before INSERT.</li>
 *   <li>ADM-05: every write is audited in the same transaction.</li>
 * </ul>
 */
@Service
public class JobTitleSettingsService {

    private static final Logger log = LoggerFactory.getLogger(JobTitleSettingsService.class);

    private final JobTitleRepository jobTitleRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param jobTitleRepository reads/writes the {@code hr.job_title} table
     * @param auditLogService    writes audit entries (ADM-05)
     * @param objectMapper       serialises before/after snapshots
     * @param clock              application-wide clock bean
     */
    public JobTitleSettingsService(final JobTitleRepository jobTitleRepository,
                                   final AuditLogService auditLogService,
                                   final ObjectMapper objectMapper,
                                   final Clock clock) {
        this.jobTitleRepository = jobTitleRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * C04 — Lists all job titles ordered by name.
     *
     * @return all job titles
     */
    public List<JobTitleDto> listAll() {
        return jobTitleRepository.findAll().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(jt -> new JobTitleDto(jt.getId(), jt.getName()))
                .toList();
    }

    /**
     * C05 — Creates a new job title.
     *
     * <p>Transaction boundary: the {@code job_title} INSERT and the {@code audit_log}
     * INSERT are committed atomically (ADM-05 / R-API-5).</p>
     *
     * @param request the name of the new job title
     * @param actor   the HR user performing the action
     * @return the newly created job title
     * @throws ApiException 409 DUPLICATE_NAME if the name is already in use
     */
    @Transactional
    public JobTitleDto create(final JobTitleRequest request, final Employee actor) {
        if (jobTitleRepository.findByName(request.name()).isPresent()) {
            throw new ApiException(ErrorCodes.DUPLICATE_NAME, HttpStatus.CONFLICT,
                    "This name already exists.");
        }

        final OffsetDateTime now = OffsetDateTime.now(clock);
        final JobTitle jt = new JobTitle(request.name());
        jt.setCreatedAt(now);
        jt.setCreatedBy(actor.getId());
        jt.setUpdatedAt(now);
        jt.setUpdatedBy(actor.getId());
        jobTitleRepository.save(jt);

        auditLogService.record(null, "JOB_TITLE", jt.getId(), "CREATE",
                actor, null, snapshot(jt));

        // Name is user-entered free text and is not logged raw (log-injection risk via
        // embedded CR/LF); the audit_log row above already captures it faithfully.
        log.info("JobTitle created: id={} by employee={}", jt.getId(), actor.getId());
        return new JobTitleDto(jt.getId(), jt.getName());
    }

    /**
     * C06 — Deletes a job title by ID.
     *
     * <p>Transaction boundary: DELETE and audit_log INSERT are committed atomically.</p>
     *
     * <p>ADM-03: the FK {@code ON DELETE RESTRICT} on {@code employee.job_title_id} and
     * {@code checklist_template.job_title_id} prevents deleting a job title still in use;
     * the resulting {@link DataIntegrityViolationException} is translated to
     * {@code 409 ITEM_IN_USE}.</p>
     *
     * @param id    the job title to delete
     * @param actor the HR user performing the action
     * @throws ApiException 404 NOT_FOUND if no job title with that id exists
     * @throws ApiException 409 ITEM_IN_USE if the job title is still referenced
     */
    @Transactional
    public void delete(final Long id, final Employee actor) {
        final JobTitle jt = jobTitleRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Job title not found: " + id));

        final String before = snapshot(jt);

        try {
            jobTitleRepository.delete(jt);
            jobTitleRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            log.debug("Cannot delete job title id={}: still referenced", id);
            throw new ApiException(ErrorCodes.ITEM_IN_USE, HttpStatus.CONFLICT,
                    "This item is in use and cannot be removed.");
        }

        auditLogService.record(null, "JOB_TITLE", id, "DELETE", actor, before, null);
        log.info("JobTitle deleted: id={} by employee={}", id, actor.getId());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String snapshot(final JobTitle jt) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "id", jt.getId() != null ? jt.getId() : 0L,
                    "name", jt.getName()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise job title snapshot", e);
            return "{}";
        }
    }
}
