package com.cubeworks.hr.controller.settings;

import com.cubeworks.hr.dto.settings.DepartmentDto;
import com.cubeworks.hr.dto.settings.DepartmentRequest;
import com.cubeworks.hr.security.CurrentUser;
import com.cubeworks.hr.service.settings.DepartmentSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing departments in the Settings screen (FRD-08 §6 C01–C03).
 *
 * <p>All endpoints require the {@code HR} role (FRD-08 §2: "HR — Yes,
 * Manager/Employee/IT admin — No"). No exceptions and no per-employee self-scoping.</p>
 *
 * <p>Controllers stay thin (R-CODE-1): all business logic lives in
 * {@link DepartmentSettingsService}.</p>
 */
@RestController
@RequestMapping("/api/settings/departments")
@PreAuthorize("hasRole('HR')")
public class DepartmentSettingsController {

    private final DepartmentSettingsService departmentSettingsService;
    private final CurrentUser currentUser;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param departmentSettingsService the service holding all department-CRUD logic
     * @param currentUser               resolves the authenticated employee from the JWT
     */
    public DepartmentSettingsController(final DepartmentSettingsService departmentSettingsService,
                                        final CurrentUser currentUser) {
        this.departmentSettingsService = departmentSettingsService;
        this.currentUser = currentUser;
    }

    /**
     * C01 — Returns all departments with their current employee count.
     *
     * @return 200 with the department list
     */
    @GetMapping
    public ResponseEntity<List<DepartmentDto>> listDepartments() {
        return ResponseEntity.ok(departmentSettingsService.listAll());
    }

    /**
     * C02 — Creates a new department.
     *
     * @param request the new department name
     * @return 201 with the newly created department
     */
    @PostMapping
    public ResponseEntity<DepartmentDto> createDepartment(@Valid @RequestBody final DepartmentRequest request) {
        final var actor = currentUser.require();
        final DepartmentDto created = departmentSettingsService.create(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * C03 — Deletes a department by ID.
     *
     * <p>Returns {@code 409 ITEM_IN_USE} if the department still has employees
     * assigned (ADM-03).</p>
     *
     * @param id the department's surrogate PK
     * @return 204 on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDepartment(@PathVariable final Long id) {
        final var actor = currentUser.require();
        departmentSettingsService.delete(id, actor);
        return ResponseEntity.noContent().build();
    }
}
