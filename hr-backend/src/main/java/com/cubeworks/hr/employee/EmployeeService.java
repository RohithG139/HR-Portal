package com.cubeworks.hr.employee;

import com.cubeworks.hr.asset.AssetRepository;
import com.cubeworks.hr.common.audit.AuditLogService;
import com.cubeworks.hr.common.error.ApiException;
import com.cubeworks.hr.common.error.ErrorCodes;
import com.cubeworks.hr.onboarding.OnboardingDraftRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.constraints.Pattern;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business logic for employee management: list, view, profile edit and Keycloak account actions.
 *
 * <h2>Transaction boundaries</h2>
 * <p>Every write method is annotated {@code @Transactional}. The transaction covers:
 * the employee/contract row update, the {@code audit_log} insert, and any {@code email_outbox}
 * row. The transaction commits before any engine or Keycloak call (R-ARC-5).</p>
 */
@Service
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    /**
     * Sort fields the IT admin is permitted to use on the employee list (fix #6).
     *
     * <p>The IT admin's list endpoint (D-W9) is new in Wave 3 and exposes a reduced field set.
     * Allowing arbitrary sort properties would let a caller leak data ordering by hidden columns
     * (e.g. {@code dateOfBirth}, {@code privateEmail}) even though the values are not returned
     * in the response. This allowlist mirrors exactly the columns the IT admin response shape
     * renders. HR's own sort behaviour predates Wave 3 and is out of scope for this fix.</p>
     */
    private static final Set<String> IT_ADMIN_SORT_ALLOWLIST = Set.of(
            "lastName", "firstName", "employeeCode", "stage", "jobTitle.name");

    private final EmployeeRepository employeeRepository;
    private final OnboardingDraftRepository draftRepository;
    private final ContractRepository contractRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final com.cubeworks.hr.security.SelfActionGuard selfActionGuard;
    private final com.cubeworks.hr.common.outbox.EmailOutboxService emailOutboxService;
    private final AssetRepository assetRepository;
    private final com.cubeworks.hr.idp.KeycloakAdminClient keycloakAdminClient;
    private final com.cubeworks.hr.settings.SettingRepository settingRepository;

    /**
     * Constructor injection (R-CODE-1).
     */
    public EmployeeService(final EmployeeRepository employeeRepository,
                           final OnboardingDraftRepository draftRepository,
                           final ContractRepository contractRepository,
                           final AuditLogService auditLogService,
                           final Clock clock,
                           final ObjectMapper objectMapper,
                           final com.cubeworks.hr.security.SelfActionGuard selfActionGuard,
                           final com.cubeworks.hr.common.outbox.EmailOutboxService emailOutboxService,
                           final AssetRepository assetRepository,
                           final com.cubeworks.hr.idp.KeycloakAdminClient keycloakAdminClient,
                           final com.cubeworks.hr.settings.SettingRepository settingRepository) {
        this.employeeRepository = employeeRepository;
        this.draftRepository = draftRepository;
        this.contractRepository = contractRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.selfActionGuard = selfActionGuard;
        this.emailOutboxService = emailOutboxService;
        this.assetRepository = assetRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.settingRepository = settingRepository;
    }

    /**
     * Returns a paginated, filtered list of employees and drafts (A04 / D-W9).
     *
     * <p>HR sees all employees including drafts, with all fields populated.
     * IT admin sees all employees with a reduced field set (D-W9, FRD-01 Wave 3):
     * {@code employeeCode, firstName, lastName, jobTitle, stage, assetCount} — no manager,
     * no department, no accountStatus, no drafts, no export. The role branching is done
     * server-side; the caller's role is never a request parameter (R-API-2).</p>
     *
     * @param stage        filter by lifecycle stage; null = all stages
     * @param departmentId filter by department; null = all departments
     * @param managerId    filter by manager; null = all managers
     * @param q            free-text search; null = no search
     * @param sortField    sort column (default: lastName)
     * @param page         zero-based page index
     * @param size         page size (default 20)
     * @param caller       the authenticated employee making the request
     * @return page of employee list items merged with draft rows (HR) or field-reduced (IT admin)
     */
    @Transactional(readOnly = true)
    public EmployeePageDto listEmployees(final EmployeeStage stage,
                                         final Long departmentId,
                                         final Long managerId,
                                         final String q,
                                         final String sortField,
                                         final int page,
                                         final int size,
                                         final Employee caller) {

        final String sort = (sortField != null && !sortField.isBlank()) ? sortField : "lastName";

        // Fix #6: IT admin can only sort by the columns their response shape renders.
        // An arbitrary sort property would allow data-ordering attacks via hidden columns.
        // HR's existing sort behaviour (any property) predates Wave 3 and is unchanged.
        if (caller.getAppRole() == AppRole.IT_ADMIN && !IT_ADMIN_SORT_ALLOWLIST.contains(sort)) {
            throw ApiException.fieldValidation(
                    List.of(Map.of("field", "sort", "message", "Invalid sort field.")));
        }

        final PageRequest pageable = PageRequest.of(page, size, Sort.by(sort));

        if (caller.getAppRole() == AppRole.IT_ADMIN) {
            // D-W9 (FRD-01 Wave 3): IT admin gets a reduced read-only projection.
            // No draft rows (drafts are an HR-only concept), no export (wireframe p-it-employees).
            final Page<Employee> empPage = employeeRepository.findFiltered(
                    stage, null, null, q, pageable);

            final List<Long> ids = empPage.getContent().stream()
                    .map(Employee::getId).toList();

            // Batch-load asset counts in one query to prevent N+1.
            // N+1 prevention: without this, we would issue one COUNT query per employee row.
            final Map<Long, Integer> assetCounts = new HashMap<>();
            if (!ids.isEmpty()) {
                assetRepository.countIssuedByEmployeeIds(ids)
                        .forEach(row -> assetCounts.put((Long) row[0], ((Long) row[1]).intValue()));
            }

            final List<EmployeeListItemDto> items = empPage.getContent().stream()
                    .map(e -> new EmployeeListItemDto(
                            e.getId(), null,
                            e.getFirstName(), e.getLastName(),
                            e.getJobTitle() != null ? e.getJobTitle().getName() : null,
                            null, null,
                            e.getStage() != null ? e.getStage().name() : null,
                            e.getEmployeeCode(), null, false,
                            assetCounts.getOrDefault(e.getId(), 0)))
                    .collect(Collectors.toList());

            return new EmployeePageDto(items, empPage.getTotalElements(),
                    empPage.getTotalPages(), page, size);
        }

        // WS-02: managers can only see their own team
        final Long effectiveManagerId = (caller.getAppRole() == AppRole.MANAGER)
                ? caller.getId()
                : managerId;

        final Page<Employee> empPage = employeeRepository.findFiltered(
                stage, departmentId, effectiveManagerId, q, pageable);

        final List<EmployeeListItemDto> items = empPage.getContent().stream()
                .map(this::toListItem)
                .collect(Collectors.toList());

        // If HR, append draft rows
        if (caller.getAppRole() == AppRole.HR) {
            final List<EmployeeListItemDto> drafts = draftRepository.findAll().stream()
                    .map(d -> new EmployeeListItemDto(
                            null, d.getId(), d.getFirstName(), d.getLastName(),
                            null, null, null, "DRAFT", null, null, true, null))
                    .collect(Collectors.toList());
            items.addAll(drafts);
        }

        return new EmployeePageDto(items, empPage.getTotalElements(),
                empPage.getTotalPages(), page, size);
    }

    /**
     * Returns overview data for a single employee (A31).
     *
     * <p>HR can see any employee. Managers can only see employees in their own team (WS-02).
     * Employees can only see themselves.</p>
     *
     * @param employeeId the employee to view
     * @param caller     the authenticated employee
     * @return employee overview data
     * @throws ApiException 404 NOT_FOUND if the employee does not exist
     * @throws ApiException 403 FORBIDDEN if the caller is not authorised to view this employee
     */
    @Transactional(readOnly = true)
    public EmployeeOverviewDto getOverview(final Long employeeId, final Employee caller) {
        final Employee emp = findOrThrow(employeeId);
        checkViewAccess(emp, caller);
        return toOverview(emp);
    }

    /**
     * Returns profile data for a single employee (A32).
     *
     * @param employeeId the employee to view
     * @param caller     the authenticated employee
     * @return employee profile including appRole and loginEmail
     */
    @Transactional(readOnly = true)
    public EmployeeProfileDto getProfile(final Long employeeId, final Employee caller) {
        final Employee emp = findOrThrow(employeeId);
        checkViewAccess(emp, caller);
        return toProfile(emp);
    }

    /**
     * Updates an employee's profile fields (A33).
     *
     * <p>Only HR may call this endpoint. Every changed field is written to {@code audit_log}
     * (WS-05). Changing {@code appRole} or the login e-mail also requires a Keycloak update —
     * this is queued as a post-commit action (R-ARC-5) by the caller.</p>
     *
     * <p>Transaction: covers the employee row update + audit_log inserts. Partial failure
     * (e.g. DB written but Keycloak call fails) is handled by retrying the Keycloak call
     * after commit (R-ARC-5); the DB state is the authoritative record.</p>
     *
     * @param employeeId the employee to update
     * @param request    the updated profile fields
     * @param caller     the HR user performing the update
     * @return updated profile DTO
     */
    @Transactional
    @PreAuthorize("hasRole('HR')")
    public EmployeeProfileDto updateProfile(final Long employeeId,
                                            final UpdateProfileRequest request,
                                            final Employee caller) {
        selfActionGuard.forbidSelf(caller, employeeId);
        final Employee emp = findOrThrow(employeeId);
        final String before = toJson(emp);

        // Apply changes, recording each changed field
        applyProfileChanges(emp, request, caller, before);

        final OffsetDateTime now = OffsetDateTime.now(clock);
        emp.setUpdatedAt(now);
        emp.setUpdatedBy(caller.getId());
        employeeRepository.save(emp);
        return toProfile(emp);
    }

    /**
     * Resends the "Set your password" e-mail to the employee (A34).
     *
     * <p>Only allowed if the employee has NOT yet activated their account ({@code account_status != ACTIVE}).
     * Writing the outbox row happens inside the transaction.</p>
     *
     * @param employeeId the employee to re-invite
     * @param caller     the HR user performing the action
     * @throws ApiException 409 ALREADY_LOGGED_IN if the employee's account is already ACTIVE
     */
    @Transactional
    @PreAuthorize("hasRole('HR')")
    public void resendInvite(final Long employeeId, final Employee caller) {
        selfActionGuard.forbidSelf(caller, employeeId);
        final Employee emp = findOrThrow(employeeId);
        if (emp.getAccountStatus() == AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCodes.ALREADY_LOGGED_IN, HttpStatus.CONFLICT,
                    "Employee has already logged in; cannot resend invite");
        }
        // Keycloak owns the set-password link (F01 new-hire login, 2026-10-19). If the account was
        // never provisioned (idp_subject null — e.g. an employee created before this feature, or a
        // create whose Keycloak call had failed), create it now; otherwise just re-send the link.
        String kcUserId = emp.getIdpSubject();
        if (kcUserId == null) {
            kcUserId = keycloakAdminClient.createUserWithRole(
                    emp.getPrivateEmail(), emp.getPrivateEmail(),
                    emp.getFirstName(), emp.getLastName(), emp.getAppRole().name());
            emp.setIdpSubject(kcUserId);
            emp.setAccountStatus(AccountStatus.INVITED);
            employeeRepository.save(emp);
        }
        final int setPasswordLinkDays = settingRepository.findById("SET_PASSWORD_LINK_DAYS")
                .map(s -> s.getValueInt() != null ? s.getValueInt() : 7).orElse(7);
        keycloakAdminClient.sendSetPasswordEmail(kcUserId, setPasswordLinkDays * 86400);
        auditLogService.record(emp, "EMPLOYEE", emp.getId(), "RESEND_INVITE", caller, null, null);
        log.info("Resend invite (Keycloak set-password) sent for employee {} by HR {}",
                emp.getId(), caller.getId());
    }

    /**
     * Returns the audit history for an employee (A35).
     *
     * @param employeeId the employee whose history to load
     * @param page       zero-based page index
     * @param caller     the authenticated HR user
     * @return a page of audit entries
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('HR')")
    public HistoryPageDto getHistory(final Long employeeId, final int page, final Employee caller) {
        findOrThrow(employeeId); // check existence
        // Page is loaded via AuditLogRepository in the controller
        return new HistoryPageDto(employeeId, page);
    }

    // ── A29 My Profile (employee self-view) ───────────────────────────────────

    /**
     * Returns the calling employee's own profile (A29, WS-03).
     *
     * <p>Only the authenticated employee can see their own profile via this endpoint.
     * HR uses {@link #getProfile(Long, Employee)} to view any employee's profile.</p>
     *
     * @param caller the authenticated employee (the subject of the response)
     * @return the caller's own profile data
     */
    @Transactional(readOnly = true)
    public EmployeeProfileDto getMyProfile(final Employee caller) {
        final Employee emp = findOrThrow(caller.getId());
        return toProfile(emp);
    }

    // ── A30 Update My Profile (employee self-edit) ────────────────────────────

    /**
     * Updates the calling employee's own profile (A30, WS-03).
     *
     * <p>WS-03 restriction: an employee may only update their own contact details and emergency
     * contact. They cannot change employment details (firstName, lastName, dateOfBirth, workEmail,
     * appRole). These fields are silently ignored if present in the request.</p>
     *
     * <p>Transaction: covers the employee row update and the audit_log entry. Partial failure
     * (one row updated, audit fails) rolls both back — atomicity preserved.</p>
     *
     * @param caller  the authenticated employee
     * @param request the updated contact fields and optional version
     * @return updated profile DTO
     * @throws com.cubeworks.hr.common.error.ApiException 409 STALE_VERSION on optimistic lock mismatch
     */
    @Transactional
    public EmployeeProfileDto updateMyProfile(final Employee caller,
                                               final UpdateMyProfileRequest request) {
        final Employee emp = findOrThrow(caller.getId());

        // Cross-field validation: emergencyName and emergencyPhone are a pair (AC S10)
        final boolean hasName = request.emergencyName() != null && !request.emergencyName().isBlank();
        final boolean hasPhone = request.emergencyPhone() != null && !request.emergencyPhone().isBlank();
        if (hasName && !hasPhone) {
            throw ApiException.fieldValidation(
                    List.of(Map.of("field", "emergencyPhone",
                            "message", "Add an emergency contact name and phone number.")));
        }
        if (!hasName && hasPhone) {
            throw ApiException.fieldValidation(
                    List.of(Map.of("field", "emergencyContact",
                            "message", "Add an emergency contact name and phone number.")));
        }

        // Explicit optimistic-lock version check (R-DB-5).
        // For managed entities, Hibernate does not use a manually-set version for the WHERE clause;
        // it uses the loaded snapshot. We therefore compare the caller's version explicitly and
        // throw 409 STALE_VERSION if it does not match the current DB version.
        if (request.version() != null && !request.version().equals(emp.getVersion())) {
            throw new ApiException(ErrorCodes.STALE_VERSION, HttpStatus.CONFLICT,
                    "Version conflict — reload and retry");
        }

        // Apply only the WS-03-allowed fields
        if (request.mobilePhone() != null) emp.setMobilePhone(request.mobilePhone());
        if (request.street() != null) emp.setStreet(request.street());
        if (request.postcode() != null) emp.setPostcode(request.postcode());
        if (request.city() != null) emp.setCity(request.city());
        if (request.emergencyName() != null) emp.setEmergencyName(
                request.emergencyName().isBlank() ? null : request.emergencyName());
        if (request.emergencyRelationship() != null) emp.setEmergencyRelationship(
                request.emergencyRelationship().isBlank() ? null : request.emergencyRelationship());
        if (request.emergencyPhone() != null) emp.setEmergencyPhone(
                request.emergencyPhone().isBlank() ? null : request.emergencyPhone());

        final OffsetDateTime now = OffsetDateTime.now(clock);
        emp.setUpdatedAt(now);
        emp.setUpdatedBy(caller.getId());
        employeeRepository.save(emp);

        auditLogService.record(emp, "EMPLOYEE", emp.getId(), "UPDATE_MY_PROFILE",
                caller, null, null);

        return toProfile(emp);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private Employee findOrThrow(final Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Employee " + id + " not found"));
    }

    private void checkViewAccess(final Employee target, final Employee caller) {
        if (caller.getAppRole() == AppRole.HR) return; // HR sees all
        if (caller.getAppRole() == AppRole.MANAGER) {
            // Manager sees only direct reports (WS-02)
            if (target.getManager() == null
                    || !target.getManager().getId().equals(caller.getId())) {
                // Also allow manager to see themselves
                if (!target.getId().equals(caller.getId())) {
                    throw new ApiException(ErrorCodes.FORBIDDEN, HttpStatus.FORBIDDEN,
                            "Managers can only view their own direct reports");
                }
            }
            return;
        }
        // Employee or IT_ADMIN can only see themselves
        if (!target.getId().equals(caller.getId())) {
            throw new ApiException(ErrorCodes.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "You can only view your own profile");
        }
    }

    private void applyProfileChanges(final Employee emp, final UpdateProfileRequest request,
                                     final Employee caller, final String before) {
        boolean changed = false;

        if (request.firstName() != null && !request.firstName().equals(emp.getFirstName())) {
            emp.setFirstName(request.firstName()); changed = true;
        }
        if (request.lastName() != null && !request.lastName().equals(emp.getLastName())) {
            emp.setLastName(request.lastName()); changed = true;
        }
        if (request.appRole() != null) {
            final AppRole newRole = AppRole.valueOf(request.appRole());
            if (newRole != emp.getAppRole()) {
                emp.setAppRole(newRole); changed = true;
            }
        }

        if (changed) {
            final String after = toJson(emp);
            auditLogService.record(emp, "EMPLOYEE", emp.getId(), "UPDATE_PROFILE",
                    caller, before, after);
        }
    }

    private String toJson(final Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (final JsonProcessingException e) {
            return "{}";
        }
    }

    private EmployeeListItemDto toListItem(final Employee e) {
        return new EmployeeListItemDto(
                e.getId(), null,
                e.getFirstName(), e.getLastName(),
                e.getJobTitle() != null ? e.getJobTitle().getName() : null,
                e.getDepartment() != null ? e.getDepartment().getName() : null,
                e.getManager() != null
                        ? e.getManager().getFirstName() + " " + e.getManager().getLastName()
                        : null,
                e.getStage() != null ? e.getStage().name() : null,
                e.getEmployeeCode(),
                e.getAccountStatus() != null ? e.getAccountStatus().name() : null,
                false,
                null // assetCount: null for HR rows; populated only for IT admin (D-W9)
        );
    }

    private EmployeeOverviewDto toOverview(final Employee e) {
        return new EmployeeOverviewDto(
                e.getId(), e.getEmployeeCode(),
                e.getFirstName(), e.getLastName(),
                e.getPrivateEmail(), e.getWorkEmail(),
                e.getMobilePhone(),
                e.getDateOfBirth() != null ? e.getDateOfBirth().toString() : null,
                e.getNationality(),
                e.getStreet(), e.getPostcode(), e.getCity(),
                e.getJobTitle() != null ? e.getJobTitle().getName() : null,
                e.getDepartment() != null ? e.getDepartment().getName() : null,
                e.getGrade() != null ? e.getGrade().name() : null,
                e.getManager() != null
                        ? e.getManager().getFirstName() + " " + e.getManager().getLastName()
                        : null,
                e.getStage() != null ? e.getStage().name() : null,
                e.getAccountStatus() != null ? e.getAccountStatus().name() : null
        );
    }

    private EmployeeProfileDto toProfile(final Employee e) {
        // Employment card (S10/S11): read-only for the employee (WS-03), but still part of
        // "their own profile" — sourced from the current contract, not duplicated onto Employee.
        final Contract contract = contractRepository.findByEmployeeIdAndIsCurrentTrue(e.getId())
                .orElse(null);
        return new EmployeeProfileDto(
                e.getId(), e.getFirstName(), e.getLastName(),
                e.getPrivateEmail(), e.getWorkEmail(),
                e.getMobilePhone(),
                e.getDateOfBirth() != null ? e.getDateOfBirth().toString() : null,
                e.getNationality(), e.getStreet(), e.getPostcode(), e.getCity(),
                e.getEmergencyName(), e.getEmergencyRelationship(), e.getEmergencyPhone(),
                e.getJobTitle() != null ? e.getJobTitle().getName() : null,
                e.getDepartment() != null ? e.getDepartment().getName() : null,
                e.getGrade() != null ? e.getGrade().name() : null,
                e.getAppRole() != null ? e.getAppRole().name() : null,
                e.getAccountStatus() != null ? e.getAccountStatus().name() : null,
                e.getWorkEmail(),
                e.getVersion(),
                contract != null && contract.getContractType() != null ? contract.getContractType().name() : null,
                contract != null && contract.getStartDate() != null ? contract.getStartDate().toString() : null,
                contract != null ? contract.getHoursPerWeek() : null
        );
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    /**
     * A single row in the Employees list (S1, A04).
     *
     * <p>The {@code assetCount} field is populated only for the IT admin's read-only variant
     * (D-W9, FRD-01 Wave 3) and is {@code null} for HR rows — the frontend distinguishes
     * which columns to render by reading the caller's role from the JWT, not by probing for
     * field presence (R-UI-7: hiding a button/column is not the only protection, but the
     * field being null is a harmless consequence of the role-branching, not the control itself).</p>
     *
     * @param id          surrogate PK (null for draft rows)
     * @param draftId     draft PK (null for real employees)
     * @param firstName   first name
     * @param lastName    last name
     * @param jobTitle    job title name (null for drafts)
     * @param department  department name (null for drafts)
     * @param managerName full name of the manager (null for drafts)
     * @param stage       stage code or "DRAFT"
     * @param employeeCode the E-XXXX code (null for drafts)
     * @param accountStatus account status (null for drafts)
     * @param draft       true when this row comes from onboarding_draft
     * @param assetCount  count of issued assets (null for HR; populated for IT admin, D-W9)
     */
    public record EmployeeListItemDto(Long id, Long draftId,
                                      String firstName, String lastName,
                                      String jobTitle, String department,
                                      String managerName, String stage,
                                      String employeeCode, String accountStatus,
                                      boolean draft,
                                      // Fix #9: suppress null assetCount from HR responses (HR path
                                      // always passes null; only IT admin populates this field).
                                      @JsonInclude(JsonInclude.Include.NON_NULL) Integer assetCount) {}

    /** Wrapper for a page of employee list items (A04). */
    public record EmployeePageDto(List<EmployeeListItemDto> items,
                                  long totalElements, int totalPages,
                                  int page, int size) {}

    /** Overview tab data (A31). */
    public record EmployeeOverviewDto(Long id, String employeeCode,
                                      String firstName, String lastName,
                                      String privateEmail, String workEmail,
                                      String mobilePhone, String dateOfBirth,
                                      String nationality,
                                      String street, String postcode, String city,
                                      String jobTitle, String department, String grade,
                                      String managerName, String stage,
                                      String accountStatus) {}

    /**
     * Profile tab data including HR-only fields (A32), also used for the employee's own
     * profile (A29/A30, S10). {@code contractType}/{@code startDate}/{@code hoursPerWeek}
     * are the read-only "Employment" card (WS-03) — sourced from the current contract.
     */
    public record EmployeeProfileDto(Long id, String firstName, String lastName,
                                     String privateEmail, String workEmail,
                                     String mobilePhone, String dateOfBirth,
                                     String nationality,
                                     String street, String postcode, String city,
                                     String emergencyName, String emergencyRelationship,
                                     String emergencyPhone,
                                     String jobTitle, String department, String grade,
                                     String appRole, String accountStatus,
                                     String loginEmail, Integer version,
                                     String contractType, String startDate, Short hoursPerWeek) {}

    /** Profile update request (A33). Only HR-editable fields are in this record. */
    public record UpdateProfileRequest(String firstName, String lastName, String appRole) {}

    /**
     * Employee self-service profile update (A30, WS-03).
     *
     * <p>Only the contact details an employee is allowed to change. Employment fields
     * (firstName, lastName, dateOfBirth, workEmail, appRole) are not present here so
     * they cannot be accidentally accepted from request JSON (Jackson ignores unknown
     * properties and the controller maps to this record, never to the entity).</p>
     *
     * <p>Jakarta Bean Validation fires before the service: a blank pattern or the cross-field
     * emergency-contact check (done in the service layer) produces 400 VALIDATION_FAILED.</p>
     *
     * @param mobilePhone          updated phone; null = keep existing
     * @param street               updated street; null = keep existing
     * @param postcode             Dutch postcode, format "0000 AA"; null = keep existing
     * @param city                 updated city; null = keep existing
     * @param emergencyName        emergency contact name; null = keep existing, blank = clear
     * @param emergencyRelationship emergency contact relationship; null = keep existing
     * @param emergencyPhone       emergency contact phone; null = keep existing, blank = clear
     * @param version              optimistic-lock version (R-DB-5); null skips the version check
     */
    public record UpdateMyProfileRequest(
            @Pattern(regexp = "\\+[0-9 ]{5,20}",
                     message = "Enter a valid phone number, e.g. +31 6 12345678.")
            String mobilePhone,
            String street,
            @Pattern(regexp = "[0-9]{4} [A-Z]{2}",
                     message = "Enter a valid postcode, e.g. 3511 AV.")
            String postcode,
            String city,
            String emergencyName,
            String emergencyRelationship,
            String emergencyPhone,
            Integer version
    ) {}

    /** Pagination metadata for history (A35). */
    public record HistoryPageDto(Long employeeId, int page) {}
}
