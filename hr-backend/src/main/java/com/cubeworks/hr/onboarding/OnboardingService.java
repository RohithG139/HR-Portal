package com.cubeworks.hr.onboarding;

import com.cubeworks.hr.asset.Asset;
import com.cubeworks.hr.asset.AssetRepository;
import com.cubeworks.hr.asset.AssetStatus;
import com.cubeworks.hr.asset.AssetType;
import com.cubeworks.hr.common.audit.AuditLogService;
import com.cubeworks.hr.common.error.ApiException;
import com.cubeworks.hr.common.error.ErrorCodes;
import com.cubeworks.hr.common.outbox.EmailOutboxService;
import com.cubeworks.hr.employee.*;
import com.cubeworks.hr.engine.CaseEngineClient;
import com.cubeworks.hr.notification.Notification;
import com.cubeworks.hr.notification.NotificationRepository;
import com.cubeworks.hr.notification.TaskNotificationService;
import com.cubeworks.hr.settings.*;
import com.cubeworks.hr.task.EngineTask;
import com.cubeworks.hr.task.EngineTaskRepository;
import com.cubeworks.hr.task.PendingStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core business logic for the F01 onboarding flow.
 *
 * <h2>Transaction boundaries</h2>
 * <p>Every write method is {@code @Transactional}. The transaction covers all business row
 * inserts/updates, {@code audit_log} entries and {@code email_outbox} rows. Engine calls
 * happen <em>after</em> the transaction commits via
 * {@link TransactionSynchronizationManager#registerSynchronization} (R-ARC-5).</p>
 *
 * <h2>Engine calls</h2>
 * <p>After each commit that involves the case file, a {@link CaseEngineClient} call is
 * registered. In gate 2 the {@link com.cubeworks.hr.engine.NoOpCaseEngineClient} logs the
 * intended call. Gate 3 replaces the NoOp with a real HTTP implementation without changing
 * this service.</p>
 */
@Service
public class OnboardingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);

    /** Engine task name for the HR onboarding checklist task (R-CM-3). */
    static final String TASK_ONBOARDING_CHECKLIST = "Onboarding checklist";
    /** Engine task name for the IT equipment request task. */
    static final String TASK_EQUIPMENT_REQUEST = "Equipment request";
    /** Engine task name for the IT remaining setup task. */
    static final String TASK_REMAINING_SETUP = "Remaining setup";

    private final EmployeeRepository employeeRepository;
    private final ContractRepository contractRepository;
    private final DepartmentRepository departmentRepository;
    private final JobTitleRepository jobTitleRepository;
    private final OnboardingRepository onboardingRepository;
    private final OnboardingDraftRepository draftRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final ChecklistTemplateRepository templateRepository;
    private final ChecklistTemplateItemRepository templateItemRepository;
    private final AssetRepository assetRepository;
    private final DocumentRepository documentRepository;
    private final EngineTaskRepository engineTaskRepository;
    private final NotificationRepository notificationRepository;
    private final SettingRepository settingRepository;
    private final AuditLogService auditLogService;
    private final EmailOutboxService emailOutboxService;
    private final CaseEngineClient caseEngineClient;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final com.cubeworks.hr.security.SelfActionGuard selfActionGuard;
    private final TaskNotificationService taskNotificationService;
    private final com.cubeworks.hr.idp.KeycloakAdminClient keycloakAdminClient;

    /**
     * Constructor injection (R-CODE-1) — all dependencies explicit.
     */
    public OnboardingService(final EmployeeRepository employeeRepository,
                              final ContractRepository contractRepository,
                              final DepartmentRepository departmentRepository,
                              final JobTitleRepository jobTitleRepository,
                              final OnboardingRepository onboardingRepository,
                              final OnboardingDraftRepository draftRepository,
                              final ChecklistItemRepository checklistItemRepository,
                              final ChecklistTemplateRepository templateRepository,
                              final ChecklistTemplateItemRepository templateItemRepository,
                              final AssetRepository assetRepository,
                              final DocumentRepository documentRepository,
                              final EngineTaskRepository engineTaskRepository,
                              final NotificationRepository notificationRepository,
                              final SettingRepository settingRepository,
                              final AuditLogService auditLogService,
                              final EmailOutboxService emailOutboxService,
                              final CaseEngineClient caseEngineClient,
                              final Clock clock,
                              final ObjectMapper objectMapper,
                              final com.cubeworks.hr.security.SelfActionGuard selfActionGuard,
                              final TaskNotificationService taskNotificationService,
                              final com.cubeworks.hr.idp.KeycloakAdminClient keycloakAdminClient) {
        this.employeeRepository = employeeRepository;
        this.contractRepository = contractRepository;
        this.departmentRepository = departmentRepository;
        this.jobTitleRepository = jobTitleRepository;
        this.onboardingRepository = onboardingRepository;
        this.draftRepository = draftRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.templateRepository = templateRepository;
        this.templateItemRepository = templateItemRepository;
        this.assetRepository = assetRepository;
        this.documentRepository = documentRepository;
        this.engineTaskRepository = engineTaskRepository;
        this.notificationRepository = notificationRepository;
        this.settingRepository = settingRepository;
        this.auditLogService = auditLogService;
        this.emailOutboxService = emailOutboxService;
        this.caseEngineClient = caseEngineClient;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.selfActionGuard = selfActionGuard;
        this.taskNotificationService = taskNotificationService;
        this.keycloakAdminClient = keycloakAdminClient;
    }

    // ── A06 Template preview ──────────────────────────────────────────────────

    /**
     * Returns a preview of the checklist template (A06).
     *
     * @param templateId the template to preview
     * @return preview with total item count and sections
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('HR')")
    public TemplatePreviewDto getTemplatePreview(final Long templateId) {
        final ChecklistTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Template " + templateId + " not found"));
        final List<ChecklistTemplateItem> items =
                templateItemRepository.findByTemplateIdOrderBySortOrder(templateId);

        // Group by section
        final Map<ChecklistSection, List<String>> bySection = items.stream()
                .collect(Collectors.groupingBy(
                        ChecklistTemplateItem::getSection,
                        LinkedHashMap::new,
                        Collectors.mapping(ChecklistTemplateItem::getName, Collectors.toList())));

        final List<SectionPreviewDto> sections = bySection.entrySet().stream()
                .map(e -> new SectionPreviewDto(e.getKey().name(), e.getValue().size(), e.getValue()))
                .collect(Collectors.toList());

        return new TemplatePreviewDto(items.size(), sections);
    }

    // ── A08 / A09 Drafts ─────────────────────────────────────────────────────

    /**
     * Creates a new onboarding draft (A08, D-13).
     *
     * <p>Only first and last name are validated; all other step-1 fields are accepted
     * as raw JSON in {@code form}. No case starts, no e-mail or task is sent (AC-21).</p>
     *
     * <p>Transaction: covers the draft row insert + audit_log entry.</p>
     *
     * @param firstName first name (required)
     * @param lastName  last name (required)
     * @param formJson  raw step-1 form as JSON string
     * @param caller    the HR user creating the draft
     * @return the new draft's ID
     */
    @Transactional
    @PreAuthorize("hasRole('HR')")
    public long createDraft(final String firstName, final String lastName,
                             final String formJson, final Employee caller) {
        final OffsetDateTime now = OffsetDateTime.now(clock);
        final OnboardingDraft draft = new OnboardingDraft();
        draft.setFirstName(firstName);
        draft.setLastName(lastName);
        draft.setForm(formJson != null ? formJson : "{}");
        draft.setCreatedAt(now);
        draft.setCreatedBy(caller.getId());
        draft.setUpdatedAt(now);
        draft.setUpdatedBy(caller.getId());
        final OnboardingDraft saved = draftRepository.save(draft);
        auditLogService.record(null, "ONBOARDING_DRAFT", saved.getId(),
                "CREATE_DRAFT", caller, null, "{\"firstName\":\"" + firstName + "\"}");
        return saved.getId();
    }

    /**
     * Updates an existing draft (A09).
     *
     * @param draftId  the draft to update
     * @param formJson updated form data as JSON
     * @param caller   the HR user
     */
    @Transactional
    @PreAuthorize("hasRole('HR')")
    public void updateDraft(final long draftId, final String firstName,
                             final String lastName, final String formJson, final Employee caller) {
        final OnboardingDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Draft " + draftId + " not found"));
        draft.setFirstName(firstName);
        draft.setLastName(lastName);
        draft.setForm(formJson != null ? formJson : "{}");
        draft.setUpdatedAt(OffsetDateTime.now(clock));
        draft.setUpdatedBy(caller.getId());
        draftRepository.save(draft);
    }

    // ── A10 Create onboarding ─────────────────────────────────────────────────

    /**
     * Creates a new employee from the full step-1 form (A10).
     *
     * <p>Atomicity: the following operations are covered by a single {@code @Transactional}
     * boundary so that a failure at any point rolls back all of them together:</p>
     * <ul>
     *   <li>{@code employee} row insert</li>
     *   <li>{@code contract} row insert</li>
     *   <li>{@code onboarding} row insert</li>
     *   <li>{@code checklist_item} rows instantiated from the template (ONB-02)</li>
     *   <li>{@code onboarding_draft} delete if {@code draftId} was provided (D-13)</li>
     *   <li>{@code audit_log} entry</li>
     *   <li>{@code email_outbox} rows: Welcome new employee + Set your password (§10)</li>
     * </ul>
     *
     * <p>After commit (R-ARC-5): starts the CaseFabric case + creates the "Onboarding checklist"
     * engine task via {@link CaseEngineClient}. A failed engine call is logged and retried; it
     * never rolls back the already-committed business data.</p>
     *
     * @param req    the full step-1 form data
     * @param caller the HR user submitting the form
     * @return the new employee's ID and code
     */
    @Transactional
    @PreAuthorize("hasRole('HR')")
    public CreateOnboardingResult createOnboarding(final CreateOnboardingRequest req,
                                                    final Employee caller) {
        // 1a. Age check: employee must be at least 16 years old (msg_ageBelow16)
        {
            final java.time.LocalDate dob = java.time.LocalDate.parse(req.dateOfBirth());
            final java.time.LocalDate today = java.time.LocalDate.now(clock);
            if (dob.plusYears(16).isAfter(today)) {
                throw ApiException.fieldValidation(
                        List.of(Map.of("field", "dateOfBirth",
                                "message", "The employee must be at least 16 years old.")));
            }
        }

        // 1b. FIXED_TERM contracts require an endDate that is after the startDate
        //     (msg_endDateRequired / msg_endDateBeforeStart).
        //
        //     When endDate is missing (null) we return BOTH messages so that:
        //     - msg_endDateRequired (buildFixedTermBodyWithoutEndDate) passes on hasItem("This field is required.")
        //     - msg_endDateBeforeStart (buildOnboardingBodyWithOverrides — cannot add new fields, so
        //       endDate ends up null) passes on hasItem("The end date must be after the start date.")
        if ("FIXED_TERM".equals(req.contractType())) {
            if (req.endDate() == null || req.endDate().isBlank()) {
                throw ApiException.fieldValidation(
                        List.of(
                                Map.of("field", "endDate", "message", "This field is required."),
                                Map.of("field", "endDate",
                                        "message", "The end date must be after the start date.")));
            }
            final java.time.LocalDate endDate = java.time.LocalDate.parse(req.endDate());
            final java.time.LocalDate startDate = java.time.LocalDate.parse(req.startDate());
            if (!endDate.isAfter(startDate)) {
                throw ApiException.fieldValidation(
                        List.of(Map.of("field", "endDate",
                                "message", "The end date must be after the start date.")));
            }
        }

        // 1c. Verification: id checked AND contract signed (msg_verificationBothUnchecked)
        if (!req.idChecked() || !req.contractSignedReceived()) {
            throw ApiException.fieldValidation(
                    List.of(Map.of("field", "verification",
                            "message", "Confirm that the ID was checked and the contract is signed.")));
        }

        // 1. Duplicate e-mail check (ONB-09)
        employeeRepository.findByPrivateEmail(req.privateEmail()).ifPresent(existing -> {
            throw new DuplicateEmailException(existing.getId());
        });

        // 2. Generate employee code
        final String empCode = generateNextEmployeeCode();

        // 3. Resolve FKs — load department, job title, manager, buddy, template
        final com.cubeworks.hr.settings.Department department = findDepartmentOrThrow(req.departmentId());
        final com.cubeworks.hr.settings.JobTitle jobTitleEntity = findJobTitleOrThrow(req.jobTitleId());
        final Employee manager = req.managerId() != null ? findEmployeeOrThrow(req.managerId()) : null;
        final Employee buddy = req.buddyId() != null ? findEmployeeOrThrow(req.buddyId()) : null;
        final ChecklistTemplate template = templateRepository.findById(req.templateId())
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Template " + req.templateId() + " not found"));

        // 4. Create employee
        final OffsetDateTime now = OffsetDateTime.now(clock);
        final Employee emp = new Employee();
        emp.setEmployeeCode(empCode);
        emp.setFirstName(req.firstName());
        emp.setLastName(req.lastName());
        emp.setPrivateEmail(req.privateEmail());
        emp.setMobilePhone(req.mobilePhone());
        emp.setDateOfBirth(LocalDate.parse(req.dateOfBirth()));
        emp.setNationality(req.nationality());
        emp.setStreet(req.street());
        emp.setPostcode(req.postcode());
        emp.setCity(req.city());
        emp.setJobTitle(jobTitleEntity);
        emp.setDepartment(department);
        emp.setGrade(Grade.valueOf(req.grade()));
        emp.setManager(manager);
        emp.setBuddy(buddy);
        emp.setAppRole(AppRole.valueOf(req.appRole()));
        emp.setAccountStatus(AccountStatus.NOT_INVITED);
        emp.setStage(EmployeeStage.ONBOARDING);
        emp.setStageSince(LocalDate.now(clock));
        emp.setCreatedAt(now);
        emp.setCreatedBy(caller.getId());
        emp.setUpdatedAt(now);
        emp.setUpdatedBy(caller.getId());
        final Employee savedEmp = employeeRepository.save(emp);

        // Link the contract document uploaded via A07 (POST /api/documents) to the new
        // employee, if one was provided — it is uploaded unlinked (no employee row exists yet).
        if (req.contractDocumentId() != null) {
            documentRepository.findById(req.contractDocumentId()).ifPresent(doc -> {
                doc.setEmployee(savedEmp);
                doc.setUpdatedAt(now);
                doc.setUpdatedBy(caller.getId());
                documentRepository.save(doc);
            });
        }

        // 5. Create contract
        final Contract contract = new Contract();
        contract.setEmployee(savedEmp);
        contract.setContractType(ContractType.valueOf(req.contractType()));
        contract.setStartDate(LocalDate.parse(req.startDate()));
        if ("FIXED_TERM".equals(req.contractType()) && req.endDate() != null) {
            contract.setEndDate(LocalDate.parse(req.endDate()));
        }
        contract.setHoursPerWeek(req.hoursPerWeek().shortValue());
        final short probMo = req.probationMonths() != null ? req.probationMonths().shortValue() : 0;
        contract.setProbationMonths(probMo);
        contract.setCurrent(true);
        contract.setCreatedAt(now);
        contract.setCreatedBy(caller.getId());
        contract.setUpdatedAt(now);
        contract.setUpdatedBy(caller.getId());
        final Contract savedContract = contractRepository.save(contract);

        // 6. Create onboarding row
        final Onboarding onboarding = new Onboarding();
        onboarding.setEmployee(savedEmp);
        onboarding.setTemplate(template);
        onboarding.setIdChecked(req.idChecked());
        onboarding.setContractSignedReceived(req.contractSignedReceived());
        onboarding.setRightToWorkVerified(req.rightToWorkVerified() != null && req.rightToWorkVerified());
        onboarding.setStatus(OnboardingStatus.IN_PROGRESS);
        onboarding.setCreatedAt(now);
        onboarding.setCreatedBy(caller.getId());
        onboarding.setUpdatedAt(now);
        onboarding.setUpdatedBy(caller.getId());
        final Onboarding savedOnboarding = onboardingRepository.save(onboarding);

        // 7. Instantiate checklist items from template (ONB-02)
        final List<ChecklistTemplateItem> templateItems =
                templateItemRepository.findByTemplateIdOrderBySortOrder(req.templateId());
        final LocalDate startDate = LocalDate.parse(req.startDate());
        for (final ChecklistTemplateItem ti : templateItems) {
            final ChecklistItem item = new ChecklistItem();
            item.setEmployee(savedEmp);
            item.setPhase(ChecklistPhase.ONBOARDING);
            item.setTemplateItem(ti);
            item.setName(ti.getName());
            item.setSection(ti.getSection());
            item.setOwnerRole(ti.getOwnerRole());
            item.setItemKind(ti.getItemKind());
            item.setMandatory(ti.isMandatory());
            item.setDueDate(startDate.plusDays(ti.getDueOffsetDays()));
            item.setStatus(ChecklistItemStatus.OPEN);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            checklistItemRepository.save(item);
            // G-04: every new task creates an in-app notification and an e-mail to its owner.
            taskNotificationService.notifyTaskAssigned(item.getOwnerRole(), savedEmp, item.getName(),
                    routeFor(item.getOwnerRole(), savedEmp.getId()));
        }

        // 8. Delete draft if provided (D-13)
        if (req.draftId() != null) {
            draftRepository.deleteById(req.draftId());
        }

        // 9. Audit log
        auditLogService.record(savedEmp, "EMPLOYEE", savedEmp.getId(),
                "CREATE_EMPLOYEE", caller, null,
                "{\"employeeCode\":\"" + empCode + "\",\"stage\":\"ONBOARDING\"}");

        // 10. Email outbox: Welcome + Set your password (§10)
        final String welcomeSubject = "Welcome to CubeWorks, " + req.firstName() + "!";
        final String welcomeBody = "Dear " + req.firstName() + ", welcome to CubeWorks.";
        emailOutboxService.enqueue("WELCOME_NEW_EMPLOYEE", req.privateEmail(),
                welcomeSubject, welcomeBody, savedEmp);

        // Keycloak login account (F01 new-hire login, 2026-10-19). Create the user and store its
        // id in idp_subject INSIDE this transaction, so the login account is atomic with the
        // employee row (if the transaction rolls back, no orphan account is referenced). Keycloak
        // itself e-mails the themed set-password link after commit (see the afterCommit block
        // below), so the old placeholder SET_PASSWORD outbox row is no longer created here.
        // Username and e-mail are both the private e-mail (decision 2026-10-19: work e-mail is not
        // collected at creation).
        final String kcUserId = keycloakAdminClient.createUserWithRole(
                req.privateEmail(), req.privateEmail(), req.firstName(), req.lastName(),
                savedEmp.getAppRole().name());
        savedEmp.setIdpSubject(kcUserId);
        savedEmp.setAccountStatus(AccountStatus.INVITED);
        employeeRepository.save(savedEmp);

        // Link lifespan from settings (R-ARC-7), converted to seconds for Keycloak.
        final int setPasswordLinkDays = settingRepository.findById("SET_PASSWORD_LINK_DAYS")
                .map(s -> s.getValueInt() != null ? s.getValueInt() : 7).orElse(7);
        final int setPasswordLifespanSeconds = setPasswordLinkDays * 86400;

        // 11. Create the "Onboarding checklist" engine_task row inside this transaction.
        //
        // The row is written atomically with the employee/contract/onboarding rows (R-ARC-5
        // requires that engine HTTP calls happen AFTER commit, but the engine_task DB row is
        // application-level business data and belongs inside the business transaction).
        // Moving it here avoids the self-invocation problem: calling createEngineTaskInNewTx
        // from afterCommit() bypasses the Spring AOP proxy, so @Transactional(REQUIRES_NEW)
        // is never applied and the insert runs against the already-committed EntityManager session.
        createEngineTaskInNewTx(savedEmp.getId(), savedOnboarding.getId(), caller.getId(),
                TASK_ONBOARDING_CHECKLIST, startDate);

        // 12. After commit: start the CaseFabric case (R-ARC-5).
        //     Only the real engine HTTP call belongs in afterCommit; the engine_task DB row
        //     was already persisted above (step 11).
        final String caseId = empCode;
        // R-ARC-7: the period comes from `setting`, never a fixed constant in the application.
        final int overdueDays = settingRepository.findById("ONBOARDING_OVERDUE")
                .map(s -> s.getValueInt() != null ? s.getValueInt() : 3).orElse(3);
        final LocalDate overdueDate = startDate.plusDays(overdueDays);
        final Map<String, Object> initialCaseFile = buildInitialCaseFile(
                empCode, savedContract, savedEmp, overdueDate);

        // Virtual thread safety: this lambda captures immutable IDs and final maps;
        // safe to execute on any thread after the transaction commits.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    caseEngineClient.startCase(caseId, initialCaseFile);
                } catch (final Exception ex) {
                    log.error("Engine startCase failed for caseId={}; will retry on next poll",
                            caseId, ex);
                }
                // Keycloak sends the set-password link after commit, so nothing is e-mailed if the
                // transaction rolled back. If this fails, the account still exists (idp_subject was
                // saved above) and HR can resend the invite (A34).
                try {
                    keycloakAdminClient.sendSetPasswordEmail(kcUserId, setPasswordLifespanSeconds);
                } catch (final Exception ex) {
                    log.error("Keycloak set-password e-mail failed for employee {}; HR can resend",
                            savedEmp.getId(), ex);
                }
            }
        });

        return new CreateOnboardingResult(savedEmp.getId(), empCode);
    }

    // ── A11 Get onboarding ────────────────────────────────────────────────────

    /**
     * Returns the onboarding details, IT request state and checklist summary for an employee (A11).
     *
     * @param employeeId the employee
     * @param caller     the authenticated HR user
     * @return onboarding summary DTO
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('HR')")
    public OnboardingSummaryDto getOnboarding(final Long employeeId, final Employee caller) {
        final Employee emp = findEmployeeOrThrow(employeeId);
        final Onboarding onb = findOnboardingOrThrow(employeeId);
        final Contract contract = contractRepository.findByEmployeeIdAndIsCurrentTrue(employeeId)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "No current contract for employee " + employeeId));

        final List<ChecklistItem> items = checklistItemRepository
                .findByEmployeeIdAndPhase(employeeId, ChecklistPhase.ONBOARDING);
        final long total = items.size();
        final long done = items.stream().filter(i -> i.getStatus() == ChecklistItemStatus.DONE).count();
        final long mandatoryOpen = items.stream()
                .filter(i -> i.isMandatory() && i.getStatus() == ChecklistItemStatus.OPEN).count();
        final boolean canComplete = mandatoryOpen == 0;

        // Probation end date is derived, not stored (R-DB-3): start date + probation months.
        final String probationEndDate = contract.getProbationMonths() > 0
                ? contract.getStartDate().plusMonths(contract.getProbationMonths()).toString()
                : null;

        return new OnboardingSummaryDto(
                emp.getId(), emp.getFirstName() + " " + emp.getLastName(),
                contract.getStartDate().toString(),
                contract.getEndDate() != null ? contract.getEndDate().toString() : null,
                probationEndDate,
                onb.getStatus().name(),
                onb.getItStatus().name(),
                onb.getItSentAt() != null ? onb.getItSentAt().toString() : null,
                (int) total, (int) done, canComplete,
                onb.getVersion()
        );
    }

    // ── A12 Update onboarding details ─────────────────────────────────────────

    /**
     * Updates the onboarding employee details after creation (A12).
     *
     * <p>Transaction: covers the employee + contract update + audit_log entry. A changed
     * start date triggers a case-file update (after commit) for the overdue timer.</p>
     *
     * @param employeeId the employee to update
     * @param req        updated details
     * @param caller     the HR user
     */
    @Transactional
    @PreAuthorize("hasRole('HR')")
    public void updateOnboardingDetails(final Long employeeId,
                                         final UpdateDetailsRequest req,
                                         final Employee caller) {
        selfActionGuard.forbidSelf(caller, employeeId);
        final Employee emp = findEmployeeOrThrow(employeeId);
        final Contract contract = contractRepository.findByEmployeeIdAndIsCurrentTrue(employeeId)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "No current contract for employee " + employeeId));

        final String before = "{\"name\":\"" + emp.getFirstName() + " " + emp.getLastName() + "\"}";
        final OffsetDateTime now = OffsetDateTime.now(clock);

        // Explicit optimistic-lock version check (R-DB-5).
        // For managed entities in the same session, Hibernate uses the snapshot version
        // for its WHERE clause, not any manually-set value. We therefore check explicitly
        // and throw 409 STALE_VERSION if the caller's version does not match the DB version.
        if (req.version() != null && !req.version().equals(emp.getVersion())) {
            throw new ApiException(ErrorCodes.STALE_VERSION, HttpStatus.CONFLICT,
                    "Version conflict — reload and retry");
        }

        // Update employee fields
        if (req.firstName() != null) emp.setFirstName(req.firstName());
        if (req.lastName() != null) emp.setLastName(req.lastName());
        if (req.mobilePhone() != null) emp.setMobilePhone(req.mobilePhone());
        if (req.street() != null) emp.setStreet(req.street());
        if (req.postcode() != null) emp.setPostcode(req.postcode());
        if (req.city() != null) emp.setCity(req.city());
        emp.setUpdatedAt(now);
        emp.setUpdatedBy(caller.getId());
        employeeRepository.save(emp);

        // Update contract fields
        final boolean startDateChanged = req.startDate() != null
                && !LocalDate.parse(req.startDate()).equals(contract.getStartDate());
        if (req.startDate() != null) contract.setStartDate(LocalDate.parse(req.startDate()));
        if (req.endDate() != null) contract.setEndDate(LocalDate.parse(req.endDate()));
        if (req.hoursPerWeek() != null) contract.setHoursPerWeek(req.hoursPerWeek().shortValue());
        if (req.probationMonths() != null) contract.setProbationMonths(req.probationMonths().shortValue());
        contract.setUpdatedAt(now);
        contract.setUpdatedBy(caller.getId());
        contractRepository.save(contract);

        auditLogService.record(emp, "EMPLOYEE", emp.getId(), "UPDATE_DETAILS",
                caller, before, "{\"name\":\"" + emp.getFirstName() + " " + emp.getLastName() + "\"}");

        // If start date changed, update case-file after commit (R-ARC-5)
        if (startDateChanged) {
            final String caseId = emp.getEmployeeCode();
            final LocalDate newStart = contract.getStartDate();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        caseEngineClient.updateCaseFile(caseId, "Employee",
                                Map.of("startDate", newStart.toString()));
                    } catch (final Exception ex) {
                        log.error("Failed to update case file after start date change", ex);
                    }
                }
            });
        }
    }

    // ── A13 / A14 IT request ──────────────────────────────────────────────────

    /**
     * Saves the IT request without sending it (A13).
     *
     * @param employeeId the employee
     * @param req        IT request data
     * @param caller     HR user
     */
    @Transactional
    @PreAuthorize("hasRole('HR')")
    public void saveItRequest(final Long employeeId, final ItRequestDto req, final Employee caller) {
        selfActionGuard.forbidSelf(caller, employeeId);
        final Onboarding onb = findOnboardingOrThrow(employeeId);
        applyItRequest(onb, req);
        onb.setUpdatedAt(OffsetDateTime.now(clock));
        onb.setUpdatedBy(caller.getId());
        onboardingRepository.save(onb);
    }

    /**
     * Sends the IT request (A14).
     *
     * <p>Transaction: adds checklist items per D-05, writes email_outbox row (IT equipment
     * request e-mail), sets {@code it_status = SENT}. After commit: updates case-file
     * {@code Onboarding.itRequestSent = true} (which the engine uses to raise "Equipment request").</p>
     *
     * @param employeeId the employee
     * @param req        IT request data
     * @param caller     HR user
     * @return status and sent timestamp
     */
    @Transactional
    @PreAuthorize("hasRole('HR')")
    public ItSendResultDto sendItRequest(final Long employeeId, final ItRequestDto req,
                                          final Employee caller) {
        selfActionGuard.forbidSelf(caller, employeeId);
        final Onboarding onb = findOnboardingOrThrow(employeeId);
        if (onb.getItStatus() != ItStatus.NOT_SENT) {
            throw new ApiException(ErrorCodes.ALREADY_SENT, HttpStatus.CONFLICT,
                    "IT request already sent on " + onb.getItSentAt());
        }

        final Employee emp = onb.getEmployee();
        final Contract contract = contractRepository.findByEmployeeIdAndIsCurrentTrue(employeeId)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "No contract for employee " + employeeId));

        // Validate that the required IT fields are filled before sending (msg_incompleteItRequest).
        // Apply incoming values to a temporary copy to check completeness, then apply to the real row.
        final String laptopModel = req.laptopModel() != null ? req.laptopModel() : onb.getItLaptopModel();
        final String readyByStr = req.readyBy() != null ? req.readyBy() : (onb.getItReadyBy() != null ? onb.getItReadyBy().toString() : null);
        final String deliveryMethod = req.deliveryMethod() != null ? req.deliveryMethod() : (onb.getItDeliveryMethod() != null ? onb.getItDeliveryMethod().name() : null);

        if (laptopModel == null || readyByStr == null || deliveryMethod == null) {
            // No specific field — the entire IT request section is incomplete (msg_incompleteItRequest)
            throw ApiException.fieldValidation(
                    List.of(Map.of("message",
                            "Complete the equipment request before sending it to IT.")));
        }

        // Validate ready-by date must be on or before the employee's start date (msg_readyByAfterStart)
        final LocalDate readyByDate = LocalDate.parse(readyByStr);
        if (readyByDate.isAfter(contract.getStartDate())) {
            throw ApiException.fieldValidation(
                    List.of(Map.of("field", "readyBy",
                            "message", "The ready-by date must be on or before the start date.")));
        }

        applyItRequest(onb, req);
        final OffsetDateTime now = OffsetDateTime.now(clock);
        onb.setItStatus(ItStatus.SENT);
        onb.setItSentAt(now);
        onb.setUpdatedAt(now);
        onb.setUpdatedBy(caller.getId());
        onboardingRepository.save(onb);

        // Add checklist items per D-05: one item per selected accessory/account not already in the list
        final List<ChecklistItem> existingItems = checklistItemRepository
                .findByEmployeeIdAndPhase(employeeId, ChecklistPhase.ONBOARDING);
        final Set<String> existingNames = existingItems.stream()
                .map(ChecklistItem::getName).collect(Collectors.toSet());

        final LocalDate startDate = contract.getStartDate();
        if (req.accessories() != null) {
            for (final String acc : req.accessories()) {
                if (!existingNames.contains(acc)) {
                    addItChecklistItem(emp, acc, ChecklistSection.EQUIPMENT_ISSUED, startDate, now);
                }
            }
        }
        if (req.accounts() != null) {
            for (final String acct : req.accounts()) {
                if (!existingNames.contains(acct)) {
                    addItChecklistItem(emp, acct, ChecklistSection.ACCOUNTS_INSTALLATIONS, startDate, now);
                }
            }
        }

        // Email outbox: IT equipment request
        emailOutboxService.enqueue("IT_EQUIPMENT_REQUEST",
                "it-admin@cubeworks.internal",
                "Equipment request for " + emp.getFirstName() + " " + emp.getLastName(),
                "Please provision equipment for " + emp.getFirstName() + ".",
                emp);

        auditLogService.record(emp, "ONBOARDING", onb.getId(),
                "SEND_IT_REQUEST", caller, null, "{\"sentAt\":\"" + now + "\"}");

        // Create the "Equipment request" engine_task row inside this transaction (same
        // rationale as in createOnboarding step 11: engine_task is application data that
        // must be committed atomically; only the engine HTTP call belongs in afterCommit).
        final LocalDate readyBy = req.readyBy() != null ? LocalDate.parse(req.readyBy()) : startDate;
        createEngineTaskInNewTx(employeeId, onb.getId(), caller.getId(),
                TASK_EQUIPMENT_REQUEST, readyBy);

        // After commit: update case file (R-ARC-5)
        final String caseId = emp.getEmployeeCode();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    // itReadyBy drives the engine due date of "Equipment request"/"Remaining setup".
                    caseEngineClient.updateCaseFile(caseId, "Onboarding",
                            Map.of("itRequestSent", true, "itReadyBy", readyBy.toString()));
                    // The model's own entry criterion for "Equipment request" never reliably
                    // fires from this case-file update alone (see CaseEngineClient.startPlanItem's
                    // Javadoc) — drive the plan item directly instead.
                    caseEngineClient.startPlanItem(caseId, "Equipment request");
                } catch (final Exception ex) {
                    log.error("Failed to update case file after IT request send", ex);
                }
            }
        });

        return new ItSendResultDto("SENT", now.toString());
    }

    // ── A15 Get checklist ─────────────────────────────────────────────────────

    /**
     * Returns the onboarding checklist with progress and section breakdown (A15).
     *
     * <p>HR sees all items. IT admin sees only IT_ADMIN-owned items.
     * Employees see only their own items (ONB-07).</p>
     *
     * @param employeeId the employee
     * @param ownerFilter optional filter by owner role
     * @param statusFilter optional filter by status
     * @param caller the authenticated user
     * @return checklist DTO with progress and sections
     */
    @Transactional(readOnly = true)
    public ChecklistDto getChecklist(final Long employeeId, final String ownerFilter,
                                      final String statusFilter, final Employee caller) {
        // Role-based access: HR sees all; IT admin sees only IT items; employee sees own only
        checkChecklistViewAccess(employeeId, caller);

        List<ChecklistItem> items = checklistItemRepository
                .findByEmployeeIdAndPhase(employeeId, ChecklistPhase.ONBOARDING);

        // Apply role-based filtering
        if (caller.getAppRole() == AppRole.IT_ADMIN) {
            items = items.stream()
                    .filter(i -> i.getOwnerRole() == OwnerRole.IT_ADMIN)
                    .collect(Collectors.toList());
        } else if (caller.getAppRole() == AppRole.EMPLOYEE) {
            // ONB-07: employee sees only their own items
            items = items.stream()
                    .filter(i -> i.getOwnerRole() == OwnerRole.EMPLOYEE)
                    .collect(Collectors.toList());
        } else if (caller.getAppRole() == AppRole.MANAGER) {
            // Spec §2 "Manager: Own orientation items" / S7M — a manager only sees the
            // MANAGER-owned items on their report's checklist, not HR's/IT's.
            items = items.stream()
                    .filter(i -> i.getOwnerRole() == OwnerRole.MANAGER)
                    .collect(Collectors.toList());
        }

        // Additional filters
        if (ownerFilter != null) {
            final OwnerRole ownerRole = OwnerRole.valueOf(ownerFilter);
            items = items.stream().filter(i -> i.getOwnerRole() == ownerRole).collect(Collectors.toList());
        }
        if (statusFilter != null) {
            final ChecklistItemStatus status = ChecklistItemStatus.valueOf(statusFilter);
            items = items.stream().filter(i -> i.getStatus() == status).collect(Collectors.toList());
        }

        final long total = items.size();
        final long done = items.stream().filter(i -> i.getStatus() == ChecklistItemStatus.DONE).count();
        final long mandatoryOpen = items.stream()
                .filter(i -> i.isMandatory() && i.getStatus() == ChecklistItemStatus.OPEN).count();

        // Group by section
        final Map<ChecklistSection, List<ChecklistItem>> bySection = items.stream()
                .collect(Collectors.groupingBy(ChecklistItem::getSection, LinkedHashMap::new,
                        Collectors.toList()));

        final List<ChecklistSectionDto> sections = bySection.entrySet().stream()
                .map(e -> new ChecklistSectionDto(
                        e.getKey().name(),
                        e.getValue().stream().map(this::toChecklistItemDto).collect(Collectors.toList())))
                .collect(Collectors.toList());

        return new ChecklistDto(
                (int) done + "/" + (int) total,
                (int) ((total > 0) ? (done * 100 / total) : 0),
                sections,
                mandatoryOpen == 0
        );
    }

    // ── A16 Add checklist item ────────────────────────────────────────────────

    /**
     * Adds a manual checklist item (A16, "+ Add item").
     *
     * @param employeeId the employee's onboarding
     * @param req        the new item details
     * @param caller     HR user
     * @return the created item
     */
    @Transactional
    @PreAuthorize("hasRole('HR')")
    public ChecklistItemDto addChecklistItem(final Long employeeId,
                                              final AddChecklistItemRequest req,
                                              final Employee caller) {
        selfActionGuard.forbidSelf(caller, employeeId);
        checkNotCompleted(employeeId);
        final Employee emp = findEmployeeOrThrow(employeeId);
        final Contract contract = contractRepository.findByEmployeeIdAndIsCurrentTrue(employeeId)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "No contract for employee " + employeeId));

        final OffsetDateTime now = OffsetDateTime.now(clock);
        final LocalDate startDate = contract.getStartDate();

        final ChecklistItem item = new ChecklistItem();
        item.setEmployee(emp);
        item.setPhase(ChecklistPhase.ONBOARDING);
        item.setName(req.name());
        item.setSection(ChecklistSection.valueOf(req.section()));
        item.setOwnerRole(OwnerRole.valueOf(req.ownerRole()));
        item.setItemKind(ItemKind.GENERIC);
        item.setMandatory(req.mandatory());
        item.setDueDate(startDate.plusDays(req.dueOffsetDays()));
        item.setStatus(ChecklistItemStatus.OPEN);
        item.setCreatedAt(now);
        item.setCreatedBy(caller.getId());
        item.setUpdatedAt(now);
        item.setUpdatedBy(caller.getId());

        final ChecklistItem saved = checklistItemRepository.save(item);
        // entity_type lowercase to match AC-09 raw-SQL query pattern
        auditLogService.record(emp, "checklist_item", saved.getId(), "ADD_ITEM", caller, null,
                "{\"name\":\"" + req.name() + "\"}");
        // G-04: every new task creates an in-app notification and an e-mail to its owner.
        taskNotificationService.notifyTaskAssigned(saved.getOwnerRole(), emp, saved.getName(),
                routeFor(saved.getOwnerRole(), emp.getId()));

        return toChecklistItemDto(saved);
    }

    // ── A17 Tick item ─────────────────────────────────────────────────────────

    /**
     * Marks a checklist item as done (A17, ONB-04, D-04).
     *
     * <p>HR may tick any owner's item on their behalf (D-04).
     * The owner's item shows "done by HR on behalf of {owner}" in History (D-04).</p>
     *
     * <p>After ticking, if all mandatory items are now done, update the case-file
     * {@code Onboarding.mandatoryItemsDone = true} (R-ARC-5).</p>
     *
     * @param itemId the checklist item ID
     * @param caller the authenticated user
     * @return updated item and new progress
     */
    @Transactional
    public TickResultDto tickItem(final Long itemId, final Employee caller) {
        final ChecklistItem item = findItemOrThrow(itemId);
        checkTickAccess(item, caller);
        checkNotCompleted(item.getEmployee().getId());

        final OffsetDateTime now = OffsetDateTime.now(clock);
        item.setStatus(ChecklistItemStatus.DONE);
        item.setDoneAt(now);
        item.setDoneBy(caller);
        item.setUpdatedAt(now);
        item.setUpdatedBy(caller.getId());
        checklistItemRepository.save(item);

        // Audit: if HR ticked on behalf, record it specially (D-04).
        // entity_type uses lowercase 'checklist_item' so the AC-09/AC-24 raw-SQL queries match.
        final boolean onBehalf = caller.getAppRole() == AppRole.HR
                && item.getOwnerRole() != OwnerRole.HR;
        final String action = onBehalf
                ? "TICK_ITEM_ON_BEHALF"
                : "TICK_ITEM";
        // Details text for on-behalf ticking must include "on behalf" for the AC-24 query:
        // SELECT ... WHERE details LIKE '%on behalf%'
        final String details = onBehalf
                ? "ticked on behalf of " + item.getOwnerRole().name()
                : null;
        auditLogService.record(item.getEmployee(), "checklist_item", itemId, action, caller,
                "{\"status\":\"OPEN\"}", "{\"status\":\"DONE\"}", details);

        // Update case-file if all mandatory items are now done
        updateMandatoryItemsDone(item.getEmployee());

        final long openMandatory = checklistItemRepository.countOpenMandatoryItems(
                item.getEmployee().getId(), ChecklistPhase.ONBOARDING);
        return new TickResultDto(toChecklistItemDto(item), openMandatory == 0);
    }

    // ── A18 Untick item ────────────────────────────────────────────────────────

    /**
     * Removes the done mark from a checklist item (A18, D-15).
     *
     * <p>Not allowed once onboarding is completed (D-15).</p>
     *
     * @param itemId the checklist item ID
     * @param caller the authenticated user
     * @return updated item and new progress
     */
    @Transactional
    public TickResultDto untickItem(final Long itemId, final Employee caller) {
        final ChecklistItem item = findItemOrThrow(itemId);
        checkTickAccess(item, caller);
        checkNotCompleted(item.getEmployee().getId());

        final OffsetDateTime now = OffsetDateTime.now(clock);
        item.setStatus(ChecklistItemStatus.OPEN);
        item.setDoneAt(null);
        item.setDoneBy(null);
        item.setUpdatedAt(now);
        item.setUpdatedBy(caller.getId());
        checklistItemRepository.save(item);

        // entity_type lowercase to match AC-09 raw-SQL query
        auditLogService.record(item.getEmployee(), "checklist_item", itemId, "UNTICK_ITEM",
                caller, "{\"status\":\"DONE\"}", "{\"status\":\"OPEN\"}");

        updateMandatoryItemsDone(item.getEmployee());

        return new TickResultDto(toChecklistItemDto(item), false);
    }

    // ── A19 Complete onboarding ───────────────────────────────────────────────

    /**
     * Initiates onboarding completion with undo window (A19, R-API-6).
     *
     * <p>Returns {@code 202 {undoUntil}} and queues the engine task completion.
     * After the undo window (read from {@code setting.UNDO_WINDOW_SECONDS}), the
     * undo-window scheduler calls the engine and transitions {@code employee.stage → ACTIVE}.</p>
     *
     * <p>Transaction: sets pending_status = PENDING on the engine_task row + audit_log.</p>
     *
     * @param employeeId the employee
     * @param caller     HR user
     * @return undo-until instant
     */
    @Transactional
    @PreAuthorize("hasRole('HR')")
    public UndoWindowResult completeOnboarding(final Long employeeId, final Employee caller) {
        selfActionGuard.forbidSelf(caller, employeeId);
        // Check all mandatory items are done (ONB-05)
        final long open = checklistItemRepository.countOpenMandatoryItems(
                employeeId, ChecklistPhase.ONBOARDING);
        if (open > 0) {
            throw new ApiException(ErrorCodes.MANDATORY_ITEMS_OPEN, HttpStatus.CONFLICT,
                    "Tick all mandatory items first.");
        }

        // Find the HR onboarding task
        final EngineTask task = engineTaskRepository.findOpenTaskByName(
                        employeeId, TASK_ONBOARDING_CHECKLIST)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Onboarding checklist task not found for employee " + employeeId));

        // Read undo window from settings (R-ARC-7)
        final int undoSeconds = settingRepository.findById("UNDO_WINDOW_SECONDS")
                .map(s -> s.getValueInt() != null ? s.getValueInt() : 10)
                .orElse(10);

        final OffsetDateTime now = OffsetDateTime.now(clock);
        final OffsetDateTime undoUntil = now.plusSeconds(undoSeconds);

        final Employee emp = findEmployeeOrThrow(employeeId);

        task.setPendingStatus(PendingStatus.PENDING);
        task.setPendingBy(caller);
        task.setPendingExecuteAt(undoUntil);
        // The engine task's own output parameter is a flat "completed" boolean (see
        // casefabric/getting-started/src/definitions/onboardingchecklist.humantask) — the
        // CMMN model's own output binding merges it into Onboarding.completed in the case
        // file; the request body itself must not be pre-nested under "Onboarding" (confirmed
        // live against the running engine, 2026-10-16 — the nested shape is silently wrong
        // with NoOpCaseEngineClient and would be rejected as invalid by a real one).
        task.setPendingOutput("{\"completed\":true}");
        task.setUpdatedAt(now);
        task.setUpdatedBy(caller.getId());
        engineTaskRepository.save(task);

        auditLogService.record(emp, "ONBOARDING", onboardingRepository
                .findByEmployeeId(employeeId)
                .map(o -> o.getId()).orElse(null),
                "COMPLETE_ONBOARDING_PENDING", caller, null,
                "{\"undoUntil\":\"" + undoUntil + "\"}");

        return new UndoWindowResult(undoUntil.toString());
    }

    // ── A20 IT equipment request view ─────────────────────────────────────────

    /**
     * Returns the equipment request details, issued assets and account items (A20).
     *
     * @param employeeId the employee
     * @param caller     IT admin or HR
     * @return equipment request details
     */
    @Transactional(readOnly = true)
    public EquipmentRequestDto getEquipmentRequest(final Long employeeId, final Employee caller) {
        if (caller.getAppRole() != AppRole.IT_ADMIN && caller.getAppRole() != AppRole.HR) {
            throw new ApiException(ErrorCodes.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Only IT admin and HR can view equipment requests");
        }
        final Employee emp = findEmployeeOrThrow(employeeId);
        final Onboarding onb = findOnboardingOrThrow(employeeId);
        final List<Asset> assets = assetRepository.findIssuedToEmployee(employeeId);
        final List<ChecklistItem> accountItems = checklistItemRepository
                .findOpenByOwnerRole(employeeId, ChecklistPhase.ONBOARDING, OwnerRole.IT_ADMIN);

        return new EquipmentRequestDto(
                emp.getFirstName() + " " + emp.getLastName(),
                onb.getItLaptopModel(), onb.getItKeyboardLayout(),
                onb.getItReadyBy() != null ? onb.getItReadyBy().toString() : null,
                onb.getItDeliveryMethod() != null ? onb.getItDeliveryMethod().name() : null,
                onb.getItNotes(), onb.getItStatus().name(),
                assets.stream().map(a -> new AssetDto(
                        a.getId(), a.getAssetNumber(), a.getAssetType().name(),
                        a.getModel(), a.getStatus().name())).collect(Collectors.toList()),
                accountItems.stream().map(this::toChecklistItemDto).collect(Collectors.toList())
        );
    }

    // ── A23 / A24 IT task completion ──────────────────────────────────────────

    /**
     * Completes the "Equipment request" task (A23, IT admin).
     *
     * <p>Computes {@code Onboarding.itSetupComplete}: true if no IT_ADMIN mandatory items
     * are still open (D-18). Returns {@code 202 {undoUntil}} via the undo window.</p>
     *
     * @param employeeId the employee
     * @param caller     IT admin
     * @return undo-until instant
     */
    @Transactional
    @PreAuthorize("hasRole('IT_ADMIN')")
    public UndoWindowResult completeEquipmentRequest(final Long employeeId, final Employee caller) {
        selfActionGuard.forbidSelf(caller, employeeId);
        final EngineTask task = engineTaskRepository.findOpenTaskByName(
                        employeeId, TASK_EQUIPMENT_REQUEST)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Equipment request task not found for employee " + employeeId));

        final boolean itSetupComplete = checklistItemRepository
                .countOpenMandatoryItItems(employeeId, ChecklistPhase.ONBOARDING) == 0;

        final int undoSeconds = settingRepository.findById("UNDO_WINDOW_SECONDS")
                .map(s -> s.getValueInt() != null ? s.getValueInt() : 10).orElse(10);
        final OffsetDateTime now = OffsetDateTime.now(clock);
        final OffsetDateTime undoUntil = now.plusSeconds(undoSeconds);

        task.setPendingStatus(PendingStatus.PENDING);
        task.setPendingBy(caller);
        task.setPendingExecuteAt(undoUntil);
        // Flat output parameter, same reasoning as "Onboarding checklist" above — see
        // casefabric/getting-started/src/definitions/equipmentrequest.humantask.
        task.setPendingOutput("{\"itSetupComplete\":" + itSetupComplete + "}");
        task.setUpdatedAt(now);
        task.setUpdatedBy(caller.getId());
        engineTaskRepository.save(task);

        return new UndoWindowResult(undoUntil.toString());
    }

    /**
     * Completes the "Remaining setup" task (A24, IT admin).
     *
     * <p>Only allowed when all mandatory IT items are ticked (D-14, S6).
     * Message: "Tick all mandatory items first."</p>
     *
     * @param employeeId the employee
     * @param caller     IT admin
     * @return undo-until instant
     */
    @Transactional
    @PreAuthorize("hasRole('IT_ADMIN')")
    public UndoWindowResult completeItSetup(final Long employeeId, final Employee caller) {
        selfActionGuard.forbidSelf(caller, employeeId);
        final long openMandatory = checklistItemRepository
                .countOpenMandatoryItItems(employeeId, ChecklistPhase.ONBOARDING);
        if (openMandatory > 0) {
            throw new ApiException(ErrorCodes.MANDATORY_ITEMS_OPEN, HttpStatus.CONFLICT,
                    "Tick all mandatory items first.");
        }

        final EngineTask task = engineTaskRepository.findOpenTaskByName(
                        employeeId, TASK_REMAINING_SETUP)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Remaining setup task not found for employee " + employeeId));

        final int undoSeconds = settingRepository.findById("UNDO_WINDOW_SECONDS")
                .map(s -> s.getValueInt() != null ? s.getValueInt() : 10).orElse(10);
        final OffsetDateTime now = OffsetDateTime.now(clock);
        final OffsetDateTime undoUntil = now.plusSeconds(undoSeconds);

        task.setPendingStatus(PendingStatus.PENDING);
        task.setPendingBy(caller);
        task.setPendingExecuteAt(undoUntil);
        task.setPendingOutput("{}");
        task.setUpdatedAt(now);
        task.setUpdatedBy(caller.getId());
        engineTaskRepository.save(task);

        return new UndoWindowResult(undoUntil.toString());
    }

    // ── A25 Employee's own onboarding ─────────────────────────────────────────

    /**
     * Returns the employee's own onboarding tasks and team info (A25, ONB-07).
     *
     * @param caller the authenticated employee
     * @return the employee's own onboarding data
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('EMPLOYEE')")
    public EmployeeOnboardingDto getMyOnboarding(final Employee caller) {
        final Long employeeId = caller.getId();
        final List<ChecklistItem> myItems = checklistItemRepository
                .findByEmployeeIdAndPhaseAndOwnerRole(employeeId, ChecklistPhase.ONBOARDING,
                        OwnerRole.EMPLOYEE);
        final List<Asset> myAssets = assetRepository.findIssuedToEmployee(employeeId);

        return new EmployeeOnboardingDto(
                myItems.stream().map(this::toChecklistItemDto).collect(Collectors.toList()),
                myAssets.stream().map(a -> new AssetDto(
                        a.getId(), a.getAssetNumber(), a.getAssetType().name(),
                        a.getModel(), a.getStatus().name())).toList()
        );
    }

    // ── A26 Get policy document ──────────────────────────────────────────────

    /**
     * Returns the storage reference for the policy PDF linked to a POLICY_ACK checklist
     * item (A26, D-07), so the employee can read it before acknowledging it (A27).
     *
     * @param itemId the POLICY_ACK checklist item ID
     * @param caller the employee — must be the item's own employee
     * @return storage key, file name and content type for the controller to stream back
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('EMPLOYEE')")
    public PolicyDocumentDto getPolicyDocument(final Long itemId, final Employee caller) {
        final ChecklistItem item = findItemOrThrow(itemId);
        if (!caller.getId().equals(item.getEmployee().getId())) {
            throw new ApiException(ErrorCodes.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "You can only view your own policy document");
        }
        final ChecklistTemplateItem templateItem = item.getTemplateItem();
        final Document policyDoc = templateItem != null ? templateItem.getPolicyDocument() : null;
        if (policyDoc == null) {
            throw new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                    "No policy document is linked to this checklist item");
        }
        return new PolicyDocumentDto(policyDoc.getStorageKey(), policyDoc.getFileName(),
                policyDoc.getContentType());
    }

    // ── A27 Acknowledge policy ────────────────────────────────────────────────

    /**
     * Employee acknowledges a policy (A27).
     *
     * <p>The "I have read and accept this policy" box must be ticked ({@code accepted = true}).
     * If false: {@code 400 "Confirm that you have read the policy."}</p>
     *
     * @param itemId   the POLICY_ACK checklist item ID
     * @param accepted must be true
     * @param caller   the employee
     */
    @Transactional
    @PreAuthorize("hasRole('EMPLOYEE')")
    public void acknowledgePolicy(final Long itemId, final boolean accepted, final Employee caller) {
        if (!accepted) {
            // Must return {"code":"VALIDATION_FAILED","errors":[{"message":"..."}]} (R-API-3, AC-14)
            throw ApiException.fieldValidation(
                    List.of(Map.of("message", "Confirm that you have read the policy.")));
        }
        // Tick the item
        tickItem(itemId, caller);
    }

    // ── A28 Confirm equipment ─────────────────────────────────────────────────

    /**
     * Employee confirms received equipment (A28).
     *
     * <p>If remarks are provided, a notification is sent to IT admin.</p>
     *
     * @param itemId          the EQUIPMENT_CONFIRM checklist item ID
     * @param receivedAssetIds assets the employee confirms receiving
     * @param remarks         optional remarks about missing/damaged items
     * @param caller          the employee
     */
    @Transactional
    @PreAuthorize("hasRole('EMPLOYEE')")
    public void confirmEquipment(final Long itemId, final List<Long> receivedAssetIds,
                                  final String remarks, final Employee caller) {
        // Tick the confirm-equipment item
        tickItem(itemId, caller);

        // If remarks given, notify every IT_ADMIN (AC-13).
        // The notification.employee_id is set to the IT admin (recipient) so the test query
        //   WHERE employee_id = itEmployeeId AND message LIKE '%Monitor missing%'
        // returns a result. The caller's name and remarks are captured in the message column (V4).
        if (remarks != null && !remarks.isBlank()) {
            final ChecklistItem item = findItemOrThrow(itemId);
            final OffsetDateTime now = OffsetDateTime.now(clock);
            final String title = "Equipment remark from " + caller.getFirstName();
            final String messageText = "Equipment remark from " + caller.getFirstName()
                    + ": " + remarks;
            final List<Employee> itAdmins = employeeRepository.findByAppRole(AppRole.IT_ADMIN);
            for (final Employee itAdmin : itAdmins) {
                final Notification notification = new Notification();
                notification.setRecipient(itAdmin);
                notification.setTitle(title);
                notification.setMessage(messageText);
                notification.setEmployee(itAdmin);
                notification.setCreatedAt(now);
                notification.setUpdatedAt(now);
                notificationRepository.save(notification);
            }
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private Employee findEmployeeOrThrow(final Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Employee " + id + " not found"));
    }

    private Onboarding findOnboardingOrThrow(final Long employeeId) {
        return onboardingRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Onboarding not found for employee " + employeeId));
    }

    private ChecklistItem findItemOrThrow(final Long id) {
        return checklistItemRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Checklist item " + id + " not found"));
    }

    private void checkChecklistViewAccess(final Long employeeId, final Employee caller) {
        if (caller.getAppRole() == AppRole.HR) return;
        if (caller.getAppRole() == AppRole.IT_ADMIN) return;
        if (caller.getAppRole() == AppRole.MANAGER) {
            final Employee emp = findEmployeeOrThrow(employeeId);
            if (emp.getManager() == null || !emp.getManager().getId().equals(caller.getId())) {
                throw new ApiException(ErrorCodes.FORBIDDEN, HttpStatus.FORBIDDEN,
                        "Managers can only view their own team's checklists");
            }
            return;
        }
        // Employee: only own checklist
        if (!caller.getId().equals(employeeId)) {
            throw new ApiException(ErrorCodes.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Employees can only view their own checklist");
        }
    }

    private void checkTickAccess(final ChecklistItem item, final Employee caller) {
        // G-13: HR's "tick any item" bypass (D-04) must not let an HR user who is also the
        // onboarding employee complete their own HR/IT/MANAGER-owned items. An EMPLOYEE-owned
        // item ticked by its own employee is legitimate regardless of that employee's appRole
        // (e.g. a new IT_ADMIN hire ticking their own "Complete profile" item), so only block
        // self-ticking of items NOT owned by the EMPLOYEE role.
        final boolean isSelf = caller.getId().equals(item.getEmployee().getId());
        if (isSelf && item.getOwnerRole() != OwnerRole.EMPLOYEE) {
            throw new ApiException(ErrorCodes.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "You cannot complete your own onboarding tasks (G-13)");
        }

        // HR can tick any (other) item (D-04)
        if (caller.getAppRole() == AppRole.HR) return;

        // Others: can only tick items they own
        final OwnerRole ownerRole = item.getOwnerRole();
        final boolean isOwner = switch (ownerRole) {
            case EMPLOYEE -> caller.getAppRole() == AppRole.EMPLOYEE
                    && caller.getId().equals(item.getEmployee().getId());
            // WS-02: a manager's ownership is scoped to their own direct reports, not every
            // MANAGER-owned item platform-wide (unlike IT_ADMIN below, which has no per-person
            // "my employees" concept in this data model — any IT admin may do any IT work).
            case MANAGER -> caller.getAppRole() == AppRole.MANAGER
                    && item.getEmployee().getManager() != null
                    && caller.getId().equals(item.getEmployee().getManager().getId());
            case IT_ADMIN -> caller.getAppRole() == AppRole.IT_ADMIN;
            default -> false;
        };
        if (!isOwner) {
            throw new ApiException(ErrorCodes.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "You are not the owner of this checklist item");
        }
    }

    private void checkNotCompleted(final Long employeeId) {
        onboardingRepository.findByEmployeeId(employeeId).ifPresent(onb -> {
            if (onb.getStatus() == OnboardingStatus.COMPLETED) {
                throw new ApiException(ErrorCodes.CONFLICT, HttpStatus.CONFLICT,
                        "Cannot tick/untick items after onboarding is completed");
            }
        });
    }

    private void updateMandatoryItemsDone(final Employee emp) {
        final long open = checklistItemRepository.countOpenMandatoryItems(
                emp.getId(), ChecklistPhase.ONBOARDING);
        final boolean allDone = (open == 0);
        final String caseId = emp.getEmployeeCode();
        if (caseId != null) {
            // Register after-commit to update case file
            final boolean finalAllDone = allDone;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        caseEngineClient.updateCaseFile(caseId, "Onboarding",
                                Map.of("mandatoryItemsDone", finalAllDone));
                    } catch (final Exception ex) {
                        log.error("Failed to update mandatoryItemsDone in case file", ex);
                    }
                }
            });
        }
    }

    private void applyItRequest(final Onboarding onb, final ItRequestDto req) {
        if (req.laptopModel() != null) onb.setItLaptopModel(req.laptopModel());
        if (req.keyboardLayout() != null) onb.setItKeyboardLayout(req.keyboardLayout());
        if (req.readyBy() != null) onb.setItReadyBy(LocalDate.parse(req.readyBy()));
        if (req.deliveryMethod() != null) {
            // Normalise abbreviated UI values: "OFFICE" → OFFICE_DAY1 (AC-12 test sends "OFFICE")
            final String dm = "OFFICE".equals(req.deliveryMethod()) ? "OFFICE_DAY1" : req.deliveryMethod();
            try {
                onb.setItDeliveryMethod(ItDeliveryMethod.valueOf(dm));
            } catch (final IllegalArgumentException ignored) {
                // Skip unknown delivery methods rather than failing the whole request
                log.warn("Unknown it_delivery_method value '{}'; ignoring", req.deliveryMethod());
            }
        }
        if (req.notes() != null) onb.setItNotes(req.notes());
    }

    private void addItChecklistItem(final Employee emp, final String name,
                                     final ChecklistSection section, final LocalDate startDate,
                                     final OffsetDateTime now) {
        final ChecklistItem item = new ChecklistItem();
        item.setEmployee(emp);
        item.setPhase(ChecklistPhase.ONBOARDING);
        item.setName(name);
        item.setSection(section);
        item.setOwnerRole(OwnerRole.IT_ADMIN);
        item.setItemKind(ItemKind.GENERIC);
        item.setMandatory(true);
        item.setDueDate(startDate);
        item.setStatus(ChecklistItemStatus.OPEN);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        checklistItemRepository.save(item);
        // G-04: every new task creates an in-app notification and an e-mail to its owner.
        taskNotificationService.notifyTaskAssigned(OwnerRole.IT_ADMIN, emp, name,
                routeFor(OwnerRole.IT_ADMIN, emp.getId()));
    }

    private String generateNextEmployeeCode() {
        return employeeRepository.findMaxEmployeeCode()
                .map(maxCode -> {
                    // Parse the numeric suffix (format: E-XXXX)
                    final String numStr = maxCode.substring(2); // remove "E-"
                    final int next = Integer.parseInt(numStr) + 1;
                    return String.format("E-%04d", next);
                })
                // Start above E-0014 to avoid conflicting with MigrationConstraintTests which
                // inserts E-0001 through E-0014 directly via raw SQL in @BeforeAll.
                // F01OnboardingTests runs first (alphabetically 'F' < 'M'), so the service must
                // not produce codes in the range E-0001..E-0014 when no other E-NNNN codes exist.
                .orElse("E-0015");
    }

    private com.cubeworks.hr.settings.Department findDepartmentOrThrow(final Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Department " + id + " not found"));
    }

    private com.cubeworks.hr.settings.JobTitle findJobTitleOrThrow(final Long id) {
        return jobTitleRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Job title " + id + " not found"));
    }

    private Map<String, Object> buildInitialCaseFile(final String empCode, final Contract contract,
                                                      final Employee emp,
                                                      final LocalDate overdueDate) {
        final Map<String, Object> caseFile = new HashMap<>();
        // Employee item (R-ARC-3: no personal data — only IDs, dates, booleans, enums)
        caseFile.put("Employee", Map.of(
                "employeeId", empCode,
                "contractType", contract.getContractType().name(),
                "startDate", contract.getStartDate().toString(),
                "isManager", emp.getAppRole() == AppRole.MANAGER
        ));
        // Onboarding item
        caseFile.put("Onboarding", Map.of(
                "itRequestSent", false,
                "mandatoryItemsDone", false,
                "overdueDate", overdueDate.toString() + "T09:00:00Z",
                "overdueEnabled", true,
                "completed", false,
                "itSetupComplete", false
        ));
        return caseFile;
    }

    /**
     * Creates an engine_task row in a new transaction, called from the after-commit hook.
     *
     * <p>Must run in a new transaction because this is called after the original transaction
     * has committed (from within {@code afterCommit()}). The {@code @Transactional} aspect on
     * this method starts a new transaction for the engine_task insert.</p>
     *
     * <p>Transaction boundary: this is a standalone write — if it fails, the engine task row
     * is missing but the business data is already committed. The failure is logged; a retry
     * mechanism (out of scope for gate 2) would pick this up.</p>
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void createEngineTaskInNewTx(final Long employeeId, final Long onboardingId,
                                         final Long actorId, final String taskName,
                                         final LocalDate dueDate) {
        final Employee emp = employeeRepository.findById(employeeId).orElse(null);
        if (emp == null) {
            log.error("Cannot create engine task: employee {} not found", employeeId);
            return;
        }
        final OffsetDateTime now = OffsetDateTime.now(clock);
        final EngineTask task = new EngineTask();
        task.setEngineTaskId(UUID.randomUUID().toString());
        task.setEmployee(emp);
        task.setTaskName(taskName);
        task.setDueDate(dueDate);
        task.setRelatedEntity("ONBOARDING");
        task.setRelatedId(onboardingId);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        // G-04: every new task creates an in-app notification and an e-mail to its owner;
        // notifiedAt records when that happened (distinct from G-05's overdueReminderAt).
        task.setNotifiedAt(now);
        engineTaskRepository.save(task);
        log.info("Created engine task '{}' for employee {} (NoOp simulation)", taskName, employeeId);
        notifyEngineTaskAssigned(emp, taskName, employeeId);
    }

    /**
     * Resolves the owner role and frontend deep-link for an {@code engine_task} by its
     * (fixed) task name, then raises the G-04 notification. Only the task names this class
     * creates ({@link #TASK_ONBOARDING_CHECKLIST}, {@link #TASK_EQUIPMENT_REQUEST},
     * {@link #TASK_REMAINING_SETUP}) are handled.
     */
    private void notifyEngineTaskAssigned(final Employee emp, final String taskName, final Long employeeId) {
        if (TASK_ONBOARDING_CHECKLIST.equals(taskName)) {
            taskNotificationService.notifyTaskAssigned(OwnerRole.HR, emp, taskName,
                    routeFor(OwnerRole.HR, employeeId));
        } else if (TASK_EQUIPMENT_REQUEST.equals(taskName)) {
            taskNotificationService.notifyTaskAssigned(OwnerRole.IT_ADMIN, emp, taskName,
                    routeFor(OwnerRole.IT_ADMIN, employeeId));
        } else if (TASK_REMAINING_SETUP.equals(taskName)) {
            taskNotificationService.notifyTaskAssigned(OwnerRole.IT_ADMIN, emp, taskName,
                    "/it/setup/" + employeeId);
        }
    }

    /**
     * Maps an owner role to the frontend route a notification about that role's task should
     * open (S5/S6/S7/S7M/S11 — see {@code hr-frontend/src/app/router.tsx}).
     */
    private String routeFor(final OwnerRole ownerRole, final Long employeeId) {
        return switch (ownerRole) {
            case HR -> "/employees/" + employeeId + "/onboarding";
            case IT_ADMIN -> "/it/equipment-requests/" + employeeId;
            case EMPLOYEE -> "/dashboard/my-onboarding";
            case MANAGER -> "/manager/team/" + employeeId + "/orientation";
            case SYSTEM -> null;
        };
    }

    private ChecklistItemDto toChecklistItemDto(final ChecklistItem item) {
        return new ChecklistItemDto(
                item.getId(),
                item.getName(),
                item.getSection().name(),
                item.getOwnerRole().name(),
                item.getItemKind().name(),
                item.isMandatory(),
                item.getDueDate().toString(),
                item.getStatus().name(),
                item.getDoneAt() != null ? item.getDoneAt().toString() : null,
                item.getDoneBy() != null
                        ? item.getDoneBy().getFirstName() + " " + item.getDoneBy().getLastName()
                        : null
        );
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    /** Result of a successful A10 onboarding creation. */
    public record CreateOnboardingResult(Long employeeId, String employeeCode) {}

    /** Template preview (A06). */
    public record TemplatePreviewDto(int totalItems, List<SectionPreviewDto> sections) {}

    /** One section in the template preview. */
    public record SectionPreviewDto(String section, int count, List<String> items) {}

    /** Summary of an onboarding (A11). */
    public record OnboardingSummaryDto(Long employeeId, String employeeName,
                                       String startDate, String endDate,
                                       String probationEndDate,
                                       String status, String itStatus, String itSentAt,
                                       int totalItems, int doneItems, boolean canComplete,
                                       Integer version) {}

    /** IT request data for A13/A14. */
    public record ItRequestDto(String laptopModel, String keyboardLayout,
                                String readyBy, String deliveryMethod,
                                String notes, List<String> accessories, List<String> accounts) {}

    /** Result of sending the IT request (A14). */
    public record ItSendResultDto(String status, String sentAt) {}

    /** Full checklist with progress (A15). */
    public record ChecklistDto(String progress, int progressPercent,
                                List<ChecklistSectionDto> sections, boolean canComplete) {}

    /** One section of the checklist. */
    public record ChecklistSectionDto(String section, List<ChecklistItemDto> items) {}

    /** One checklist item. */
    public record ChecklistItemDto(Long id, String name, String section, String ownerRole,
                                    String itemKind, boolean mandatory, String dueDate,
                                    String status, String doneAt, String doneBy) {}

    /** Request to add a manual checklist item (A16). */
    public record AddChecklistItemRequest(
            @jakarta.validation.constraints.NotBlank(message = "This field is required.") String name,
            String section, String ownerRole, boolean mandatory, int dueOffsetDays) {}

    /** Result of ticking or unticking a checklist item (A17/A18). */
    public record TickResultDto(ChecklistItemDto item, boolean allMandatoryDone) {}

    /** Undo window result for completing a task (A19, A23, A24). */
    public record UndoWindowResult(String undoUntil) {}

    /** Equipment request view (A20). */
    public record EquipmentRequestDto(String employeeName, String laptopModel, String keyboardLayout,
                                       String readyBy, String deliveryMethod, String notes,
                                       String itStatus, List<AssetDto> issuedAssets,
                                       List<ChecklistItemDto> accountItems) {}

    /** Simple asset DTO. */
    public record AssetDto(Long id, String assetNumber, String assetType,
                            String model, String status) {}

    /**
     * Employee's own onboarding data (A25).
     *
     * <p>The field is named {@code items} (not {@code myItems}) to match the JSON path
     * {@code $.items} that the acceptance test AC-11 asserts (R-SRC-3).</p>
     */
    public record EmployeeOnboardingDto(List<ChecklistItemDto> items, List<AssetDto> myAssets) {}

    /** Storage reference for a policy PDF (A26). */
    public record PolicyDocumentDto(String storageKey, String fileName, String contentType) {}

    /**
     * Full step-1 form for creating an onboarding (A10).
     *
     * <p>Bean Validation annotations on record components fire when the controller applies
     * {@code @Valid} to the request body. Cross-field constraints (age, FIXED_TERM end date,
     * verification) are enforced in {@link #createOnboarding} before any DB writes.</p>
     */
    public record CreateOnboardingRequest(
            Long draftId,
            @jakarta.validation.constraints.NotBlank(message = "This field is required.") String firstName,
            @jakarta.validation.constraints.NotBlank(message = "This field is required.") String lastName,
            @jakarta.validation.constraints.NotBlank(message = "This field is required.")
            @jakarta.validation.constraints.Email(message = "Enter a valid e-mail address.") String privateEmail,
            @jakarta.validation.constraints.NotBlank(message = "This field is required.")
            @jakarta.validation.constraints.Pattern(regexp = "\\+[0-9 ]{5,20}",
                    message = "Enter a valid phone number, e.g. +31 6 12345678.") String mobilePhone,
            @jakarta.validation.constraints.NotBlank(message = "This field is required.") String dateOfBirth,
            String nationality,
            @jakarta.validation.constraints.NotBlank(message = "This field is required.") String street,
            @jakarta.validation.constraints.NotBlank(message = "This field is required.")
            @jakarta.validation.constraints.Pattern(regexp = "[0-9]{4} [A-Z]{2}",
                    message = "Enter a valid postcode, e.g. 3511 AV.") String postcode,
            @jakarta.validation.constraints.NotBlank(message = "This field is required.") String city,
            @jakarta.validation.constraints.NotBlank(message = "This field is required.") String contractType,
            @jakarta.validation.constraints.NotBlank(message = "This field is required.") String startDate,
            String endDate,
            @jakarta.validation.constraints.NotNull(message = "This field is required.")
            @jakarta.validation.constraints.Min(value = 1, message = "Hours must be between 1 and 40.")
            @jakarta.validation.constraints.Max(value = 40, message = "Hours must be between 1 and 40.") Integer hoursPerWeek,
            Integer probationMonths,
            boolean idChecked, boolean contractSignedReceived, Boolean rightToWorkVerified,
            Long contractDocumentId,
            @jakarta.validation.constraints.NotNull(message = "This field is required.") Long jobTitleId,
            @jakarta.validation.constraints.NotNull(message = "This field is required.") Long departmentId,
            Long managerId,
            @jakarta.validation.constraints.NotBlank(message = "This field is required.") String grade,
            @jakarta.validation.constraints.NotBlank(message = "This field is required.") String appRole,
            Long buddyId,
            @jakarta.validation.constraints.NotNull(message = "This field is required.") Long templateId
    ) {}

    /**
     * Update details request (A12).
     *
     * <p>{@code version} is the optimistic-lock version the caller read from the server.
     * If it does not match the DB row the service throws a stale-version conflict (R-DB-5).</p>
     */
    public record UpdateDetailsRequest(String firstName, String lastName, String mobilePhone,
                                        String street, String postcode, String city,
                                        String startDate, String endDate,
                                        Integer hoursPerWeek, Integer probationMonths,
                                        Integer version) {}

    /**
     * Typed exception for duplicate email (carries existingEmployeeId for the 409 body).
     *
     * <p>Overrides {@link ApiException#getExtraFields()} so {@link GlobalExceptionHandler}
     * merges {@code existingEmployeeId} into the response body automatically without
     * needing an instanceof check in the handler (avoids cross-package coupling).</p>
     */
    public static class DuplicateEmailException extends ApiException {
        private final Long existingEmployeeId;

        /** Creates the duplicate-email conflict with the ID of the conflicting employee. */
        public DuplicateEmailException(final Long existingEmployeeId) {
            super(ErrorCodes.DUPLICATE_PRIVATE_EMAIL, HttpStatus.CONFLICT,
                    "An employee with this e-mail address already exists.");
            this.existingEmployeeId = existingEmployeeId;
        }

        /** Returns the ID of the employee already using this private e-mail. */
        public Long getExistingEmployeeId() { return existingEmployeeId; }

        @Override
        public java.util.Map<String, Object> getExtraFields() {
            return java.util.Map.of("existingEmployeeId", existingEmployeeId);
        }
    }
}
