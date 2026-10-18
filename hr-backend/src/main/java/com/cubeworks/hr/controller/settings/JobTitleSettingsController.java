package com.cubeworks.hr.controller.settings;

import com.cubeworks.hr.dto.settings.JobTitleDto;
import com.cubeworks.hr.dto.settings.JobTitleRequest;
import com.cubeworks.hr.security.CurrentUser;
import com.cubeworks.hr.service.settings.JobTitleSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing job titles in the Settings screen (FRD-08 §6 C04–C06).
 *
 * <p>All endpoints require the {@code HR} role (FRD-08 §2). Controllers stay thin
 * (R-CODE-1): all logic lives in {@link JobTitleSettingsService}.</p>
 */
@RestController
@RequestMapping("/api/settings/job-titles")
@PreAuthorize("hasRole('HR')")
public class JobTitleSettingsController {

    private final JobTitleSettingsService jobTitleSettingsService;
    private final CurrentUser currentUser;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param jobTitleSettingsService the service holding all job-title CRUD logic
     * @param currentUser             resolves the authenticated employee from the JWT
     */
    public JobTitleSettingsController(final JobTitleSettingsService jobTitleSettingsService,
                                      final CurrentUser currentUser) {
        this.jobTitleSettingsService = jobTitleSettingsService;
        this.currentUser = currentUser;
    }

    /**
     * C04 — Returns all job titles.
     *
     * @return 200 with the job-title list
     */
    @GetMapping
    public ResponseEntity<List<JobTitleDto>> listJobTitles() {
        return ResponseEntity.ok(jobTitleSettingsService.listAll());
    }

    /**
     * C05 — Creates a new job title.
     *
     * @param request the new job title name
     * @return 201 with the newly created job title
     */
    @PostMapping
    public ResponseEntity<JobTitleDto> createJobTitle(@Valid @RequestBody final JobTitleRequest request) {
        final var actor = currentUser.require();
        final JobTitleDto created = jobTitleSettingsService.create(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * C06 — Deletes a job title by ID.
     *
     * <p>Returns {@code 409 ITEM_IN_USE} if the job title is still referenced
     * by any employee or checklist template (ADM-03).</p>
     *
     * @param id the job title's surrogate PK
     * @return 204 on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJobTitle(@PathVariable final Long id) {
        final var actor = currentUser.require();
        jobTitleSettingsService.delete(id, actor);
        return ResponseEntity.noContent().build();
    }
}
