package com.cubeworks.hr.service.settings;

import com.cubeworks.hr.common.audit.AuditLogService;
import com.cubeworks.hr.common.error.ApiException;
import com.cubeworks.hr.common.error.ErrorCodes;
import com.cubeworks.hr.dto.settings.DepartmentDto;
import com.cubeworks.hr.dto.settings.DepartmentRequest;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.employee.EmployeeRepository;
import com.cubeworks.hr.settings.Department;
import com.cubeworks.hr.settings.DepartmentRepository;
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
import java.util.stream.Collectors;

/**
 * Service for managing departments in the Settings screen (FRD-08 §6 C01–C03).
 *
 * <p>Business rules enforced here:</p>
 * <ul>
 *   <li>ADM-03: a department in use by any employee cannot be deleted; the FK {@code RESTRICT}
 *       surfaces as {@link DataIntegrityViolationException}, translated here to
 *       {@code 409 ITEM_IN_USE}.</li>
 *   <li>Duplicate name: checked before INSERT so the error is a clean API response rather
 *       than a DB constraint violation.</li>
 *   <li>ADM-05: every write is audited in the same transaction (R-API-5).</li>
 * </ul>
 *
 * <p>This service holds all business logic; the controller delegates to it and stays thin
 * (R-CODE-1).</p>
 */
@Service
public class DepartmentSettingsService {

    private static final Logger log = LoggerFactory.getLogger(DepartmentSettingsService.class);

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param departmentRepository reads/writes the {@code hr.department} table
     * @param employeeRepository   used to count employees per department for C01
     * @param auditLogService      writes audit entries (ADM-05 / R-API-5)
     * @param objectMapper         serialises before/after snapshots for audit
     * @param clock                application-wide clock bean (never use OffsetDateTime.now() directly)
     */
    public DepartmentSettingsService(final DepartmentRepository departmentRepository,
                                     final EmployeeRepository employeeRepository,
                                     final AuditLogService auditLogService,
                                     final ObjectMapper objectMapper,
                                     final Clock clock) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * C01 — Lists all departments with their employee counts.
     *
     * @return all departments ordered by name
     */
    @Transactional(readOnly = true)
    public List<DepartmentDto> listAll() {
        // One aggregate query for every department's count, not one countByDepartmentId
        // call per department (N+1 found in Gate 5 code review).
        final Map<Long, Long> countsByDepartmentId = employeeRepository.countEmployeesGroupedByDepartment()
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        return departmentRepository.findAll().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(dept -> new DepartmentDto(
                        dept.getId(),
                        dept.getName(),
                        countsByDepartmentId.getOrDefault(dept.getId(), 0L)))
                .toList();
    }

    /**
     * C02 — Creates a new department.
     *
     * <p>Transaction boundary: the {@code department} INSERT and the {@code audit_log}
     * INSERT are committed atomically. If either fails, neither is persisted (ADM-05).</p>
     *
     * @param request the name of the new department
     * @param actor   the HR user performing the action
     * @return the newly created department
     * @throws ApiException 409 DUPLICATE_NAME if the name is already in use
     */
    @Transactional
    public DepartmentDto create(final DepartmentRequest request, final Employee actor) {
        // Duplicate-name check before hitting the DB UNIQUE constraint
        if (departmentRepository.findByName(request.name()).isPresent()) {
            throw new ApiException(ErrorCodes.DUPLICATE_NAME, HttpStatus.CONFLICT,
                    "This name already exists.");
        }

        final OffsetDateTime now = OffsetDateTime.now(clock);
        final Department dept = new Department(request.name());
        dept.setCreatedAt(now);
        dept.setCreatedBy(actor.getId());
        dept.setUpdatedAt(now);
        dept.setUpdatedBy(actor.getId());
        departmentRepository.save(dept);

        auditLogService.record(null, "DEPARTMENT", dept.getId(), "CREATE",
                actor, null, snapshot(dept));

        // Name is user-entered free text and is not logged raw (log-injection risk via
        // embedded CR/LF); the audit_log row above already captures it faithfully.
        log.info("Department created: id={} by employee={}", dept.getId(), actor.getId());
        return new DepartmentDto(dept.getId(), dept.getName(), 0L);
    }

    /**
     * C03 — Deletes a department by ID.
     *
     * <p>Transaction boundary: the DELETE and the {@code audit_log} INSERT are committed
     * atomically — if the audit write fails, the delete is rolled back too.</p>
     *
     * <p>ADM-03: the FK {@code ON DELETE RESTRICT} on {@code employee.department_id}
     * prevents deleting a department that still has employees; the resulting
     * {@link DataIntegrityViolationException} is caught and translated to
     * {@code 409 ITEM_IN_USE}.</p>
     *
     * @param id    the department to delete
     * @param actor the HR user performing the action
     * @throws ApiException 404 NOT_FOUND if no department with that id exists
     * @throws ApiException 409 ITEM_IN_USE if the department still has employees
     */
    @Transactional
    public void delete(final Long id, final Employee actor) {
        final Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Department not found: " + id));

        final String before = snapshot(dept);

        try {
            departmentRepository.delete(dept);
            departmentRepository.flush(); // flush to trigger FK constraint immediately
        } catch (DataIntegrityViolationException ex) {
            log.debug("Cannot delete department id={}: still has employees", id);
            throw new ApiException(ErrorCodes.ITEM_IN_USE, HttpStatus.CONFLICT,
                    "This item is in use and cannot be removed.");
        }

        auditLogService.record(null, "DEPARTMENT", id, "DELETE", actor, before, null);
        log.info("Department deleted: id={} by employee={}", id, actor.getId());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String snapshot(final Department dept) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "id", dept.getId() != null ? dept.getId() : 0L,
                    "name", dept.getName()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise department snapshot", e);
            return "{}";
        }
    }
}
