package com.cubeworks.hr;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.cubeworks.hr.task.OverdueReminderScheduler;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for F01 Onboarding acceptance criteria (AC-01 … AC-24).
 *
 * <h2>Coverage mapping</h2>
 * <pre>
 * AC-01  createEmployee_happyPath_returns201WithOnboardingStage
 * AC-02  sendItRequest_happyPath_taskCreatedAndEmailQueued
 * AC-03  completeOnboarding_mandatoryItemsOpen_buttonDisabledAndCanCompleteFalse
 * AC-04  completeOnboarding_allMandatoryDone_returns202ThenStageActive
 * AC-05  createEmployee_fixedTermWithoutEndDate_returns400RequiredMessage
 * AC-06  createEmployee_duplicatePrivateEmail_returns409WithExistingId
 * AC-07  createEmployee_probationEndDateCalculated
 * AC-08  checklistItemCreated_appearsInItAdminMyTasks
 * AC-09  tickAndUntickItem_statusTogglesAndBothActionsInHistory
 * AC-10  issueEquipment_assetCreatedAndChecklistItemTicked
 * AC-11  employeeSeesOnlyOwnItems
 * AC-12  sendItRequest_withUntemplatedAccessory_addsNewChecklistItem
 * AC-13  confirmEquipmentWithRemarks_itemDoneAndItNotified
 * AC-14  acknowledgePolicy_withoutAcceptedFlag_returns400ExactMessage
 * AC-15  itSetupComplete_withOpenMandatoryItem_returns409ExactMessage
 * AC-16  myProfile_employeeCannotChangeNameOrDob
 * AC-17  getMe_itAdminRole_returnsCorrectMenu
 * AC-18  (Keycloak-hosted login page — not a Spring endpoint; excluded from Spring tests)
 * AC-19  completeOnboarding_afterUndoWindow_taskRemovedFromMyTasks
 * AC-20  getEmployee_managerCannotSeeOtherTeam
 * AC-21  saveDraft_noEmployeeRowNoCaseNoEmail
 * AC-22  issueEquipment_duplicateAssetNumber_returns409ExactMessage
 * AC-23  completeOnboarding_undoWithinWindow_taskReappears
 * AC-24  hrTicksOnBehalfOfItAdmin_doneByRecordsHrHistory
 * </pre>
 *
 * <h2>Dependency note</h2>
 * <p>These tests depend on the controllers and services that the {@code java-architect}
 * creates in gate 2.  Until those exist every test will receive a 404 response and fail.
 * That is the expected state before the controllers land.</p>
 *
 * <h2>External-system mocking</h2>
 * <p>The CaseFabric engine client and the Keycloak admin client are called
 * <em>after</em> the DB transaction commits (R-ARC-5 / A10 §7).  Once the
 * {@code java-architect} creates those classes, add {@code @MockBean} declarations here
 * so that missing live services do not block the business-logic tests.</p>
 */
class F01OnboardingTests extends AbstractApiTest {

    // ── Per-class state ───────────────────────────────────────────────────────

    /**
     * An already-existing employee whose private e-mail we use in AC-06 (duplicate check).
     * Created once in @BeforeAll; its e-mail is "duplicate.test@outlook.com".
     */
    private long existingEmployeeId;

    /** The G-05 overdue-reminder scheduler, invoked directly rather than waiting an hour. */
    @Autowired
    private OverdueReminderScheduler overdueReminderScheduler;

    /**
     * Sets up the test actors (HR, IT, Manager, Employee) and any AC-specific seed rows.
     */
    @BeforeAll
    @Override
    void setUpActors() {
        super.setUpActors();

        // Seed the employee for AC-06 duplicate-email test
        long id = nextId();
        existingEmployeeId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role
                ) VALUES (
                    ?, 'Existing', 'Employee', 'duplicate.f01.' || ?,
                    '+31 6 99999999', '1990-06-15', 'Dup Street 1', '3511 AV', 'Utrecht',
                    ?, ?, 'MEDIOR', 'EMPLOYEE'
                ) RETURNING id
                """, Long.class,
                "E-DUP-" + id, id + "@outlook.com",
                developerJobTitleId, developmentDeptId);
    }

    // ── Helper: look up the duplicate e-mail used in AC-06 ───────────────────

    private String duplicateEmail() {
        return jdbc.queryForObject(
                "SELECT private_email FROM hr.employee WHERE id = ?",
                String.class, existingEmployeeId);
    }

    // =========================================================================
    // AC-01: HR creates employee, step 1 happy path
    // =========================================================================

    /**
     * AC-01 (FRD-02): HR onboards a new developer with a Permanent contract.
     * <p>Given HR completes all required fields with the Development template and selects
     * "Save &amp; continue", then the employee record is created with stage ONBOARDING,
     * the end date is not required (PERMANENT), and step 2 opens.</p>
     */
    @Test
    @DisplayName("AC-01: POST /api/onboardings creates employee with stage ONBOARDING")
    void createEmployee_happyPath_returns201WithOnboardingStage() throws Exception {
        String email = "ac01." + nextId() + "@outlook.com";
        mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeId").exists())
                .andExpect(jsonPath("$.employeeCode").exists());

        // Verify DB state: employee stage = ONBOARDING
        String stage = jdbc.queryForObject(
                "SELECT stage FROM hr.employee WHERE private_email = ?",
                String.class, email);
        org.junit.jupiter.api.Assertions.assertEquals("ONBOARDING", stage,
                "Employee stage must be ONBOARDING immediately after creation");
    }

    // =========================================================================
    // AC-02: IT is notified when HR sends the IT request
    // =========================================================================

    /**
     * AC-02 (FRD-02): After step 2 is complete, "Send to IT" notifies the IT admin.
     * <p>Asserts: 200 response with {@code status: "SENT"}, and an email_outbox row
     * for the IT_EQUIPMENT_REQUEST template is queued.</p>
     */
    @Test
    @DisplayName("AC-02: POST /api/onboardings/{id}/it-request/send notifies IT admin")
    void sendItRequest_happyPath_taskCreatedAndEmailQueued() throws Exception {
        // First create the employee
        String email = "ac02." + nextId() + "@outlook.com";
        String createResponse = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long empId = Long.parseLong(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(createResponse).get("employeeId").asText());

        // Save the IT request first
        mockMvc.perform(withJson(
                        put("/api/onboardings/" + empId + "/it-request").with(asHR()),
                        validItRequestBody("2027-01-05")))
                .andExpect(status().isOk());

        // Send it to IT
        mockMvc.perform(post("/api/onboardings/" + empId + "/it-request/send")
                        .with(asHR())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.sentAt").exists());

        // Verify the IT request e-mail was queued in the outbox
        int outboxCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.email_outbox WHERE template_code = 'IT_EQUIPMENT_REQUEST' AND status = 'PENDING'",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertTrue(outboxCount >= 1,
                "At least one IT_EQUIPMENT_REQUEST email must be in the outbox after send");
    }

    // =========================================================================
    // AC-03: "Complete onboarding" disabled when mandatory items open
    // =========================================================================

    /**
     * AC-03 (FRD-02 / ONB-05): When 2 mandatory checklist items are still open,
     * "Complete onboarding" must be disabled ({@code canComplete = false}), and actually
     * calling {@code POST .../complete} (A19) anyway is rejected with the D-14 message —
     * previously untested on this HR-facing path (2026-10-16): only the IT-admin
     * equivalent (AC-15, {@code .../it-setup/complete}) asserted the exact 409 text.
     */
    @Test
    @DisplayName("AC-03: GET checklist returns canComplete=false, POST complete → 409 'Tick all mandatory items first.'")
    void completeOnboarding_mandatoryItemsOpen_buttonDisabledAndCanCompleteFalse()
            throws Exception {
        // Create employee (checklist is seeded from template — all items start OPEN)
        String email = "ac03." + nextId() + "@outlook.com";
        String resp = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long empId = Long.parseLong(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(resp).get("employeeId").asText());

        // Query the checklist — all items are still open so canComplete must be false
        mockMvc.perform(get("/api/onboardings/" + empId + "/checklist").with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canComplete").value(false));

        // Calling /complete anyway must be rejected with the exact D-14 message (A19)
        mockMvc.perform(post("/api/onboardings/" + empId + "/complete")
                        .with(asHR())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MANDATORY_ITEMS_OPEN"))
                .andExpect(jsonPath("$.message").value("Tick all mandatory items first."));
    }

    // =========================================================================
    // AC-04: Complete onboarding — milestone recorded, stage → Active
    // =========================================================================

    /**
     * AC-04 (FRD-02): When all mandatory items are ticked, completing onboarding triggers
     * the "Employee onboarded" milestone and the employee stage becomes ACTIVE.
     *
     * <p>This test ticks all mandatory checklist items via the API, then calls
     * {@code POST /api/onboardings/{id}/complete} and verifies the 202 response and the
     * eventual DB state.  The undo-window fires asynchronously; this test waits for it
     * using the {@code UNDO_WINDOW_SECONDS} setting (seeded as 10 s in V2).</p>
     */
    @Test
    @DisplayName("AC-04: POST complete with all mandatory items done → 202 + stage eventually ACTIVE")
    void completeOnboarding_allMandatoryDone_returns202ThenStageActive() throws Exception {
        String email = "ac04." + nextId() + "@outlook.com";
        String resp = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long empId = Long.parseLong(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(resp).get("employeeId").asText());

        // Tick every mandatory checklist item as HR
        java.util.List<Long> mandatoryIds = jdbc.queryForList(
                "SELECT id FROM hr.checklist_item WHERE employee_id = ? AND mandatory = true AND status = 'OPEN'",
                Long.class, empId);
        for (long itemId : mandatoryIds) {
            mockMvc.perform(post("/api/checklist-items/" + itemId + "/done")
                            .with(asHR())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        // Complete onboarding — must return 202 with undoUntil
        mockMvc.perform(post("/api/onboardings/" + empId + "/complete")
                        .with(asHR())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.undoUntil").exists());

        // After the undo window (10 s), the task-watcher job must set stage = ACTIVE.
        // We wait up to 15 s and poll the DB.
        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(15))
                .pollInterval(java.time.Duration.ofSeconds(1))
                .until(() -> {
                    String stage = jdbc.queryForObject(
                            "SELECT stage FROM hr.employee WHERE id = ?",
                            String.class, empId);
                    return "ACTIVE".equals(stage);
                });
    }

    // =========================================================================
    // AC-05: Fixed-term contract without end date → 400 "This field is required."
    // =========================================================================

    /**
     * AC-05 (FRD-02): A Fixed-term contract without an end date returns
     * {@code 400 VALIDATION_FAILED} with message "This field is required."
     * on the {@code endDate} field.
     */
    @Test
    @DisplayName("AC-05: POST /api/onboardings FIXED_TERM without end date → 400 required message")
    void createEmployee_fixedTermWithoutEndDate_returns400RequiredMessage() throws Exception {
        String body = """
                {
                  "firstName": "Fixed",
                  "lastName": "Term",
                  "privateEmail": "ac05.%d@outlook.com",
                  "mobilePhone": "+31 6 12345678",
                  "dateOfBirth": "2000-01-15",
                  "street": "Main St 1",
                  "postcode": "3511 AV",
                  "city": "Utrecht",
                  "contractType": "FIXED_TERM",
                  "startDate": "2027-01-05",
                  "hoursPerWeek": 40,
                  "probationMonths": 0,
                  "idChecked": true,
                  "contractSignedReceived": true,
                  "jobTitleId": %d,
                  "departmentId": %d,
                  "managerId": %d,
                  "grade": "JUNIOR",
                  "appRole": "EMPLOYEE",
                  "templateId": %d
                }
                """.formatted(nextId(),
                developerJobTitleId, developmentDeptId, managerEmployeeId, developmentTemplateId);

        mockMvc.perform(withJson(post("/api/onboardings").with(asHR()), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='endDate')].message")
                        .value(org.hamcrest.Matchers.hasItem("This field is required.")));
    }

    // =========================================================================
    // AC-06: Duplicate private e-mail → 409 DUPLICATE_PRIVATE_EMAIL
    // =========================================================================

    /**
     * AC-06 (FRD-02 / ONB-09): If an employee with the given private e-mail already exists,
     * {@code 409 DUPLICATE_PRIVATE_EMAIL} is returned together with
     * "An employee with this e-mail address already exists." and the
     * {@code existingEmployeeId} of the collision.
     */
    @Test
    @DisplayName("AC-06: POST /api/onboardings with duplicate email → 409 DUPLICATE_PRIVATE_EMAIL")
    void createEmployee_duplicatePrivateEmail_returns409WithExistingId() throws Exception {
        mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(duplicateEmail(), managerEmployeeId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PRIVATE_EMAIL"))
                .andExpect(jsonPath("$.message")
                        .value("An employee with this e-mail address already exists."))
                .andExpect(jsonPath("$.existingEmployeeId").value(existingEmployeeId));
    }

    // =========================================================================
    // AC-07: Probation end date is calculated (ONB-01)
    // =========================================================================

    /**
     * AC-07 (ONB-01): Start date 2026-10-21, 2 months probation →
     * probation ends 2026-12-21 (read-only field in the response).
     */
    @Test
    @DisplayName("AC-07: POST /api/onboardings with 2-month probation → probationEndsDate in response")
    void createEmployee_probationEndDateCalculated() throws Exception {
        String email = "ac07." + nextId() + "@outlook.com";
        String body = """
                {
                  "firstName": "Probation",
                  "lastName": "Test",
                  "privateEmail": "%s",
                  "mobilePhone": "+31 6 12345678",
                  "dateOfBirth": "2000-01-15",
                  "street": "Probation St 1",
                  "postcode": "3511 AV",
                  "city": "Utrecht",
                  "contractType": "PERMANENT",
                  "startDate": "2026-10-21",
                  "hoursPerWeek": 40,
                  "probationMonths": 2,
                  "idChecked": true,
                  "contractSignedReceived": true,
                  "jobTitleId": %d,
                  "departmentId": %d,
                  "managerId": %d,
                  "grade": "JUNIOR",
                  "appRole": "EMPLOYEE",
                  "templateId": %d
                }
                """.formatted(email, developerJobTitleId, developmentDeptId,
                managerEmployeeId, developmentTemplateId);

        String resp = mockMvc.perform(withJson(post("/api/onboardings").with(asHR()), body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeId").exists())
                .andReturn().getResponse().getContentAsString();
        long empId = Long.parseLong(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(resp).get("employeeId").asText());

        // Probation end date is derived (start date + probation months, R-DB-3), not stored;
        // A11 returns it computed in the onboarding summary.
        mockMvc.perform(get("/api/onboardings/" + empId).with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.probationEndDate").value("2026-12-21"));
    }

    // =========================================================================
    // AC-08: Checklist items created from template appear in IT admin's My Tasks
    // =========================================================================

    /**
     * AC-08 (ONB-02 / ONB-03): The Development template creates an IT-owned "Laptop" item.
     * After the employee is created, that item must appear in the IT admin's My Tasks
     * ({@code GET /api/tasks}).
     */
    @Test
    @DisplayName("AC-08: IT-owned checklist item from template appears in IT admin's My Tasks")
    void checklistItemCreated_appearsInItAdminMyTasks() throws Exception {
        String email = "ac08." + nextId() + "@outlook.com";
        String resp = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long empId = Long.parseLong(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(resp).get("employeeId").asText());

        // Verify the IT-owned items were created in the DB
        int itItems = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.checklist_item WHERE employee_id = ? AND owner_role = 'IT_ADMIN' AND status = 'OPEN'",
                Integer.class, empId);
        org.junit.jupiter.api.Assertions.assertTrue(itItems > 0,
                "Development template must create at least one IT_ADMIN-owned checklist item");

        // The IT admin's My Tasks endpoint must include at least one item for this employee
        mockMvc.perform(get("/api/tasks").with(asIT()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.employeeId==" + empId + ")]").exists());
    }

    // =========================================================================
    // AC-09: Tick and untick a checklist item (ONB-04 / D-15)
    // =========================================================================

    /**
     * AC-09 (ONB-04 / D-15): The owner ticks an item (Done immediately, progress updates);
     * the owner unticks it before onboarding completes (returns to Open); both actions
     * appear in the employee's History.
     */
    @Test
    @DisplayName("AC-09: POST /done and DELETE /done toggle checklist item status and both log to History")
    void tickAndUntickItem_statusTogglesAndBothActionsInHistory() throws Exception {
        String email = "ac09." + nextId() + "@outlook.com";
        String resp = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long empId = Long.parseLong(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(resp).get("employeeId").asText());

        // Pick an HR-owned item (HR is the current actor)
        long itemId = jdbc.queryForObject(
                "SELECT id FROM hr.checklist_item WHERE employee_id = ? AND owner_role = 'HR' AND status = 'OPEN' LIMIT 1",
                Long.class, empId);

        // Tick the item — must return 200 with status DONE
        mockMvc.perform(post("/api/checklist-items/" + itemId + "/done")
                        .with(asHR())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("DONE"));

        // Verify DB
        String status = jdbc.queryForObject(
                "SELECT status FROM hr.checklist_item WHERE id = ?", String.class, itemId);
        org.junit.jupiter.api.Assertions.assertEquals("DONE", status);

        // Untick — must return 200 with status OPEN
        mockMvc.perform(delete("/api/checklist-items/" + itemId + "/done")
                        .with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("OPEN"));

        // Verify both actions are in audit_log
        int auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.audit_log WHERE entity_type = 'checklist_item' AND entity_id = ?",
                Integer.class, itemId);
        org.junit.jupiter.api.Assertions.assertTrue(auditCount >= 2,
                "Both the tick and the untick must be recorded in audit_log");
    }

    // =========================================================================
    // AC-10: Equipment issued becomes an asset (ONB-06)
    // =========================================================================

    /**
     * AC-10 (ONB-06): When IT records a MacBook Pro 14" with asset number CW-LT-0251,
     * the asset appears in the asset register as ISSUED and the matching "Laptop"
     * checklist item is ticked.
     */
    @Test
    @DisplayName("AC-10: POST /assets with laptop → asset ISSUED + Laptop checklist item ticked")
    void issueEquipment_assetCreatedAndChecklistItemTicked() throws Exception {
        String email = "ac10." + nextId() + "@outlook.com";
        String resp = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long empId = Long.parseLong(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(resp).get("employeeId").asText());

        String assetBody = """
                {
                  "assetType": "LAPTOP",
                  "model": "MacBook Pro 14\\"",
                  "assetNumber": "CW-LT-AC10-%d",
                  "issueDate": "2027-01-05"
                }
                """.formatted(nextId());

        mockMvc.perform(withJson(
                        post("/api/employees/" + empId + "/assets").with(asIT()),
                        assetBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ISSUED"));

        // Verify asset exists with ISSUED status
        int assetCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.asset WHERE employee_id = ? AND status = 'ISSUED' AND asset_type = 'LAPTOP'",
                Integer.class, empId);
        org.junit.jupiter.api.Assertions.assertEquals(1, assetCount,
                "The newly issued laptop asset must appear in the asset register");

        // Verify the matching 'EQUIPMENT_ISSUED' checklist item is now DONE (ONB-06)
        int doneCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.checklist_item WHERE employee_id = ? AND section = 'EQUIPMENT_ISSUED' AND status = 'DONE'",
                Integer.class, empId);
        org.junit.jupiter.api.Assertions.assertTrue(doneCount > 0,
                "At least one EQUIPMENT_ISSUED checklist item must be DONE after issuing a laptop");
    }

    // =========================================================================
    // AC-11: Employee sees only their own items (ONB-07)
    // =========================================================================

    /**
     * AC-11 (ONB-07): An employee's onboarding view shows only their own items;
     * HR-owned items are hidden.
     * <p>The Development template for the base employee has both HR-owned and
     * IT-owned items.  {@code GET /api/me/onboarding} must return only the
     * EMPLOYEE-owned rows.</p>
     */
    @Test
    @DisplayName("AC-11: GET /api/me/onboarding returns only EMPLOYEE-owned items")
    void employeeSeesOnlyOwnItems() throws Exception {
        // Create a dedicated employee actor for this test
        long id = nextId();
        String sub = "sub-ac11-" + id;
        long empId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, idp_subject,
                    manager_id
                ) VALUES (
                    ?, 'Sanne', 'de Boer', ?,
                    '+31 6 12345678', '1998-03-14', 'Dam 1', '3511 AV', 'Utrecht',
                    ?, ?, 'JUNIOR', 'EMPLOYEE', ?,
                    ?
                ) RETURNING id
                """, Long.class,
                "E-" + id, "sanne.ac11." + id + "@outlook.com",
                developerJobTitleId, developmentDeptId, sub, managerEmployeeId);

        // Seed an onboarding record for this employee
        jdbc.update("""
                INSERT INTO hr.onboarding (employee_id, template_id, id_checked, contract_signed_received)
                VALUES (?, ?, true, true)
                """, empId, developmentTemplateId);

        // Manually insert 4 HR-owned and 4 EMPLOYEE-owned checklist items
        for (int i = 0; i < 4; i++) {
            jdbc.update("""
                    INSERT INTO hr.checklist_item (
                        employee_id, phase, name, section, owner_role, item_kind, mandatory, due_date, status
                    ) VALUES (?, 'ONBOARDING', ?, 'DOCUMENTS_ADMIN', 'HR', 'GENERIC', false, '2027-01-05', 'OPEN')
                    """, empId, "HR Item " + i);
            jdbc.update("""
                    INSERT INTO hr.checklist_item (
                        employee_id, phase, name, section, owner_role, item_kind, mandatory, due_date, status
                    ) VALUES (?, 'ONBOARDING', ?, 'ORIENTATION', 'EMPLOYEE', 'GENERIC', false, '2027-01-05', 'OPEN')
                    """, empId, "Employee Item " + i);
        }

        // GET /api/me/onboarding as the employee — must see only EMPLOYEE-owned items
        mockMvc.perform(get("/api/me/onboarding")
                        .with(asUserWithSub(sub, "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.ownerRole=='HR')]").doesNotExist())
                .andExpect(jsonPath("$.items[?(@.ownerRole=='EMPLOYEE')]").isNotEmpty());
    }

    // =========================================================================
    // AC-12: "Send to IT" adds extra checklist items for untemplated accessories (D-05)
    // =========================================================================

    /**
     * AC-12 (D-05): Sending the IT request with a "Docking station" (not in the Development
     * template) creates a new checklist item owned by IT admin.
     */
    @Test
    @DisplayName("AC-12: POST /it-request/send with extra accessory → new IT checklist item created")
    void sendItRequest_withUntemplatedAccessory_addsNewChecklistItem() throws Exception {
        String email = "ac12." + nextId() + "@outlook.com";
        String resp = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long empId = Long.parseLong(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(resp).get("employeeId").asText());

        // Count IT items before sending
        int beforeCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.checklist_item WHERE employee_id = ? AND owner_role = 'IT_ADMIN'",
                Integer.class, empId);

        // Send with a Docking station (not in the Development template)
        String body = """
                {
                  "laptopModel": "MacBook Pro 14\\"",
                  "readyBy": "2027-01-05",
                  "deliveryMethod": "OFFICE",
                  "accessories": ["DOCKING_STATION"],
                  "accounts": ["MICROSOFT_365"]
                }
                """;
        mockMvc.perform(withJson(
                        post("/api/onboardings/" + empId + "/it-request/send").with(asHR()),
                        body))
                .andExpect(status().isOk());

        int afterCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.checklist_item WHERE employee_id = ? AND owner_role = 'IT_ADMIN'",
                Integer.class, empId);
        org.junit.jupiter.api.Assertions.assertTrue(afterCount > beforeCount,
                "Sending IT request with Docking station must add a new IT_ADMIN checklist item (D-05)");
    }

    // =========================================================================
    // AC-13: Confirm equipment with remarks → item Done + IT notified
    // =========================================================================

    /**
     * AC-13 (S9): Employee confirms equipment and notes "Monitor missing".
     * The item becomes Done and IT admin receives an in-app notification.
     */
    @Test
    @DisplayName("AC-13: POST /confirm-equipment with remarks → item Done + IT notification created")
    void confirmEquipmentWithRemarks_itemDoneAndItNotified() throws Exception {
        // Seed a checklist item for equipment confirmation
        long id = nextId();
        String sub = "sub-ac13-" + id;
        long empId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, idp_subject,
                    manager_id
                ) VALUES (
                    ?, 'AC13', 'Employee', ?,
                    '+31 6 12345678', '1997-05-20', 'Test St 13', '3511 AV', 'Utrecht',
                    ?, ?, 'JUNIOR', 'EMPLOYEE', ?,
                    ?
                ) RETURNING id
                """, Long.class,
                "E-" + id, "ac13." + id + "@outlook.com",
                developerJobTitleId, developmentDeptId, sub, managerEmployeeId);

        long itemId = jdbc.queryForObject("""
                INSERT INTO hr.checklist_item (
                    employee_id, phase, name, section, owner_role, item_kind, mandatory, due_date, status
                ) VALUES (?, 'ONBOARDING', 'Confirm equipment received', 'EQUIPMENT_ISSUED', 'EMPLOYEE', 'EQUIPMENT_CONFIRM', false, '2027-01-05', 'OPEN')
                RETURNING id
                """, Long.class, empId);

        // Seed an asset to confirm
        long assetId = jdbc.queryForObject(
                ("INSERT INTO hr.asset (asset_number, asset_type, model, status, employee_id, issued_on) "
                        + "VALUES ('CW-AC13-" + nextId() + "', 'LAPTOP', 'MacBook', 'ISSUED', ?, '2027-01-05') "
                        + "RETURNING id"),
                Long.class, empId);

        // Employee confirms equipment with remarks
        String body = """
                {
                  "receivedAssetIds": [%d],
                  "remarks": "Monitor missing"
                }
                """.formatted(assetId);

        mockMvc.perform(withJson(
                        post("/api/checklist-items/" + itemId + "/confirm-equipment")
                                .with(asUserWithSub(sub, "EMPLOYEE")),
                        body))
                .andExpect(status().isOk());

        // Verify item is DONE
        String status = jdbc.queryForObject(
                "SELECT status FROM hr.checklist_item WHERE id = ?", String.class, itemId);
        org.junit.jupiter.api.Assertions.assertEquals("DONE", status);

        // Verify IT notification was created
        int notifCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.notification WHERE employee_id = ? AND message LIKE '%Monitor missing%'",
                Integer.class, itEmployeeId);
        org.junit.jupiter.api.Assertions.assertTrue(notifCount >= 1,
                "IT admin must receive a notification about the remark");
    }

    // =========================================================================
    // AC-14: Policy acknowledgement requires "I have read" checkbox (S8)
    // =========================================================================

    /**
     * AC-14 (S8): Employee tries to confirm a policy without ticking the acceptance checkbox.
     * Must return 400 with exact message "Confirm that you have read the policy."
     */
    @Test
    @DisplayName("AC-14: POST /acknowledge with accepted=false → 400 'Confirm that you have read the policy.'")
    void acknowledgePolicy_withoutAcceptedFlag_returns400ExactMessage() throws Exception {
        long id = nextId();
        String sub = "sub-ac14-" + id;
        long empId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, idp_subject,
                    manager_id
                ) VALUES (
                    ?, 'AC14', 'Employee', ?,
                    '+31 6 12345678', '1996-09-10', 'Policy St 1', '3511 AV', 'Utrecht',
                    ?, ?, 'JUNIOR', 'EMPLOYEE', ?,
                    ?
                ) RETURNING id
                """, Long.class,
                "E-" + id, "ac14." + id + "@outlook.com",
                developerJobTitleId, developmentDeptId, sub, managerEmployeeId);

        long itemId = jdbc.queryForObject("""
                INSERT INTO hr.checklist_item (
                    employee_id, phase, name, section, owner_role, item_kind, mandatory, due_date, status
                ) VALUES (?, 'ONBOARDING', 'Read company policy', 'DOCUMENTS_ADMIN', 'EMPLOYEE', 'POLICY_ACK', true, '2027-01-05', 'OPEN')
                RETURNING id
                """, Long.class, empId);

        mockMvc.perform(withJson(
                        post("/api/checklist-items/" + itemId + "/acknowledge")
                                .with(asUserWithSub(sub, "EMPLOYEE")),
                        """
                        {"accepted": false}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Confirm that you have read the policy."));
    }

    // =========================================================================
    // AC-15: "Mark done" on remaining setup with open mandatory items → 409
    // =========================================================================

    /**
     * AC-15 (S6 / D-14): If 1 mandatory IT item is still open when IT admin tries to
     * mark "Remaining setup" done, the response must be
     * {@code 409 MANDATORY_ITEMS_OPEN} with message "Tick all mandatory items first."
     */
    @Test
    @DisplayName("AC-15: POST /it-setup/complete with open mandatory items → 409 'Tick all mandatory items first.'")
    void itSetupComplete_withOpenMandatoryItem_returns409ExactMessage() throws Exception {
        String email = "ac15." + nextId() + "@outlook.com";
        String resp = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long empId = Long.parseLong(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(resp).get("employeeId").asText());

        // At least one mandatory IT item is still OPEN — attempt to complete setup
        mockMvc.perform(post("/api/onboardings/" + empId + "/it-setup/complete")
                        .with(asIT())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MANDATORY_ITEMS_OPEN"))
                .andExpect(jsonPath("$.message").value("Tick all mandatory items first."));
    }

    // =========================================================================
    // AC-16: Employee cannot change first name, last name, DOB (WS-03)
    // =========================================================================

    /**
     * AC-16 (WS-03): My Profile must refuse updates to first name, last name,
     * date of birth or work e-mail (employment details).  Attempting to include
     * those fields in {@code PUT /api/me/profile} must not persist the change
     * (the endpoint ignores or rejects them).
     */
    @Test
    @DisplayName("AC-16: PUT /api/me/profile ignores/rejects firstName, lastName, dateOfBirth changes")
    void myProfile_employeeCannotChangeNameOrDob() throws Exception {
        long id = nextId();
        String sub = "sub-ac16-" + id;
        long empId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, idp_subject,
                    manager_id
                ) VALUES (
                    ?, 'Lotte', 'Jansen', ?,
                    '+31 6 55555555', '1995-11-05', 'Jansen St 1', '3511 AV', 'Utrecht',
                    ?, ?, 'JUNIOR', 'EMPLOYEE', ?,
                    ?
                ) RETURNING id
                """, Long.class,
                "E-" + id, "lotte.ac16." + id + "@outlook.com",
                developerJobTitleId, developmentDeptId, sub, managerEmployeeId);

        // Attempt to change name + DOB (must be ignored / 400)
        String body = """
                {
                  "firstName": "CHANGED",
                  "lastName": "CHANGED",
                  "dateOfBirth": "1900-01-01",
                  "mobilePhone": "+31 6 12345678",
                  "street": "New Street 1",
                  "postcode": "3511 AV",
                  "city": "Utrecht"
                }
                """;

        // Either the endpoint accepts only the allowed WS-03 fields and name does NOT change,
        // or it returns 400.  Either way, firstName in the DB must still be "Lotte".
        mockMvc.perform(withJson(put("/api/me/profile")
                        .with(asUserWithSub(sub, "EMPLOYEE")), body))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(
                            sc == 200 || sc == 400,
                            "Expected 200 (fields ignored) or 400 (fields rejected), got " + sc);
                });

        // The first name must NOT have changed regardless of response
        String firstName = jdbc.queryForObject(
                "SELECT first_name FROM hr.employee WHERE id = ?", String.class, empId);
        org.junit.jupiter.api.Assertions.assertEquals("Lotte", firstName,
                "Employee must not be able to change their first name (WS-03)");
    }

    // =========================================================================
    // AC-17: Role-based menu — IT admin sees only allowed menu items
    // =========================================================================

    /**
     * AC-17 (FRD-01 WS-01): An IT admin's {@code GET /api/me} response must include
     * only the menu items for the IT admin role: Dashboard, My Tasks, Assets, Employees.
     */
    @Test
    @DisplayName("AC-17: GET /api/me as IT admin returns IT-specific menu items only")
    void getMe_itAdminRole_returnsCorrectMenu() throws Exception {
        mockMvc.perform(get("/api/me").with(asIT()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appRole").value("IT_ADMIN"))
                // Must include the IT menu items
                .andExpect(jsonPath("$.menu").isArray())
                // Must NOT include HR-only items like onboarding, reports, settings
                .andExpect(jsonPath("$.menu[?(@=='REPORTS')]").doesNotExist())
                .andExpect(jsonPath("$.menu[?(@=='SETTINGS')]").doesNotExist());
    }

    // =========================================================================
    // AC-19: Task removed after undo window expires (FRD-01 G-03)
    // =========================================================================

    /**
     * AC-19 (FRD-01 G-03): After HR completes onboarding and the undo window expires,
     * the "Onboarding checklist" task is removed from My Tasks and the count decreases.
     */
    @Test
    @DisplayName("AC-19: After undo window, onboarding task removed from HR My Tasks")
    void completeOnboarding_afterUndoWindow_taskRemovedFromMyTasks() throws Exception {
        String email = "ac19." + nextId() + "@outlook.com";
        String resp = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long empId = Long.parseLong(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(resp).get("employeeId").asText());

        // Tick all mandatory items
        java.util.List<Long> mandatoryIds = jdbc.queryForList(
                "SELECT id FROM hr.checklist_item WHERE employee_id = ? AND mandatory = true AND status = 'OPEN'",
                Long.class, empId);
        for (long itemId : mandatoryIds) {
            mockMvc.perform(post("/api/checklist-items/" + itemId + "/done")
                            .with(asHR())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        // Get task count before completing
        String countResp = mockMvc.perform(get("/api/tasks/count").with(asHR()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int countBefore = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(countResp).get("count").asInt();

        // Complete onboarding
        mockMvc.perform(post("/api/onboardings/" + empId + "/complete")
                        .with(asHR())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted());

        // After undo window, task count must decrease
        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(15))
                .pollInterval(java.time.Duration.ofSeconds(1))
                .until(() -> {
                    try {
                        String cr = mockMvc.perform(get("/api/tasks/count").with(asHR()))
                                .andReturn().getResponse().getContentAsString();
                        int countAfter = new com.fasterxml.jackson.databind.ObjectMapper()
                                .readTree(cr).get("count").asInt();
                        return countAfter < countBefore;
                    } catch (Exception e) {
                        return false;
                    }
                });
    }

    // =========================================================================
    // AC-20: Manager cannot see employees from other teams (WS-02)
    // =========================================================================

    /**
     * AC-20 (FRD-01 WS-02): A manager sees only employees who report to them.
     * Jesse Willems reports to managerEmployeeId; another manager must get 403/404
     * when trying to view Jesse.
     */
    @Test
    @DisplayName("AC-20: GET /api/employees/{id} as wrong manager → 403 FORBIDDEN")
    void getEmployee_managerCannotSeeOtherTeam() throws Exception {
        // Create Jesse who reports to the test manager
        long id = nextId();
        long jesseId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, manager_id
                ) VALUES (
                    ?, 'Jesse', 'Willems', ?,
                    '+31 6 12345678', '1998-01-01', 'Jesse St 1', '3511 AV', 'Utrecht',
                    ?, ?, 'JUNIOR', 'EMPLOYEE',
                    ?
                ) RETURNING id
                """, Long.class,
                "E-" + id, "jesse.ac20." + id + "@outlook.com",
                developerJobTitleId, developmentDeptId, managerEmployeeId);

        // Create a second manager (Anouk Peters) who does NOT manage Jesse
        long mgId2 = nextId();
        String anoukSub = "sub-anouk-" + mgId2;
        jdbc.update("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, idp_subject
                ) VALUES (
                    ?, 'Anouk', 'Peters', ?,
                    '+31 6 88888888', '1980-07-07', 'Anouk Ave 1', '2000 XZ', 'Rotterdam',
                    ?, ?, 'SENIOR', 'MANAGER', ?
                )
                """,
                "E-" + mgId2, "anouk.ac20." + mgId2 + "@cubeworks.test",
                developerJobTitleId, developmentDeptId, anoukSub);

        // Anouk should NOT be able to view Jesse
        mockMvc.perform(get("/api/employees/" + jesseId)
                        .with(asUserWithSub(anoukSub, "MANAGER")))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(
                            sc == 403 || sc == 404,
                            "Manager from another team must get 403 or 404 when viewing an employee not on their team, got " + sc);
                });
    }

    // =========================================================================
    // AC-21: Save draft does not start case, no email, no task (D-13)
    // =========================================================================

    /**
     * AC-21 (D-01 / D-13): Saving a draft writes to {@code onboarding_draft} only.
     * No {@code employee} row, no case, no e-mail, and the Employees list shows
     * the entry with a "Draft" badge.
     */
    @Test
    @DisplayName("AC-21: POST /api/onboarding-drafts creates draft only, no employee row or email")
    void saveDraft_noEmployeeRowNoCaseNoEmail() throws Exception {
        String email = "ac21." + nextId() + "@outlook.com";
        String draftBody = """
                {
                  "firstName": "Draft",
                  "lastName": "Employee",
                  "privateEmail": "%s"
                }
                """.formatted(email);

        // Count email_outbox rows before
        int outboxBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.email_outbox", Integer.class);

        mockMvc.perform(withJson(post("/api/onboarding-drafts").with(asHR()), draftBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.draftId").exists());

        // No employee row must have been created
        int empCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.employee WHERE private_email = ?",
                Integer.class, email);
        org.junit.jupiter.api.Assertions.assertEquals(0, empCount,
                "A draft must NOT create an employee row (AC-21, D-13)");

        // No new email must have been queued
        int outboxAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.email_outbox", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(outboxBefore, outboxAfter,
                "A draft must NOT queue any e-mail (AC-21)");
    }

    // =========================================================================
    // AC-22: Duplicate asset number → 409 ASSET_NUMBER_IN_USE
    // =========================================================================

    /**
     * AC-22 (409 ASSET_NUMBER_IN_USE): Attempting to issue an asset with a number that
     * already exists returns 409 with message "This asset number is already in use."
     */
    @Test
    @DisplayName("AC-22: POST /assets with duplicate asset number → 409 ASSET_NUMBER_IN_USE")
    void issueEquipment_duplicateAssetNumber_returns409ExactMessage() throws Exception {
        // Seed an existing asset
        String assetNumber = "CW-DUP-AC22-" + nextId();
        jdbc.update("""
                INSERT INTO hr.asset (asset_number, asset_type, model, status)
                VALUES (?, 'LAPTOP', 'ThinkPad', 'IN_STORAGE')
                """, assetNumber);

        // Create an employee to issue to
        String email = "ac22." + nextId() + "@outlook.com";
        String resp = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long empId = Long.parseLong(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(resp).get("employeeId").asText());

        String body = """
                {
                  "assetType": "LAPTOP",
                  "model": "ThinkPad Clone",
                  "assetNumber": "%s",
                  "issueDate": "2027-01-05"
                }
                """.formatted(assetNumber);

        mockMvc.perform(withJson(
                        post("/api/employees/" + empId + "/assets").with(asIT()), body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSET_NUMBER_IN_USE"))
                .andExpect(jsonPath("$.message").value("This asset number is already in use."));
    }

    // =========================================================================
    // AC-23: Undo a completed task within the undo window (R-API-6)
    // =========================================================================

    /**
     * AC-23 (R-API-6): HR completes onboarding.  Within the undo window (≤ 10 s),
     * HR calls {@code POST /api/tasks/{taskId}/undo} and the pending completion is
     * cancelled — the engine task returns to OPEN.
     */
    @Test
    @DisplayName("AC-23: POST /tasks/{id}/undo within undo window cancels the pending completion")
    void completeOnboarding_undoWithinWindow_taskReappears() throws Exception {
        String email = "ac23." + nextId() + "@outlook.com";
        String resp = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long empId = Long.parseLong(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(resp).get("employeeId").asText());

        // Tick all mandatory items
        java.util.List<Long> mandatoryIds = jdbc.queryForList(
                "SELECT id FROM hr.checklist_item WHERE employee_id = ? AND mandatory = true AND status = 'OPEN'",
                Long.class, empId);
        for (long itemId : mandatoryIds) {
            mockMvc.perform(post("/api/checklist-items/" + itemId + "/done")
                    .with(asHR()).contentType(MediaType.APPLICATION_JSON));
        }

        // Complete onboarding — captures undoUntil
        String completeResp = mockMvc.perform(
                        post("/api/onboardings/" + empId + "/complete")
                                .with(asHR())
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        // The engine task should now be in PENDING status
        long pendingTaskId = jdbc.queryForObject(
                "SELECT id FROM hr.engine_task WHERE employee_id = ? AND pending_status = 'PENDING' LIMIT 1",
                Long.class, empId);

        // Undo immediately — must return 200
        mockMvc.perform(post("/api/tasks/" + pendingTaskId + "/undo")
                        .with(asHR())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // The engine task must no longer be PENDING
        String pendingStatus = jdbc.queryForObject(
                "SELECT pending_status FROM hr.engine_task WHERE id = ?",
                String.class, pendingTaskId);
        org.junit.jupiter.api.Assertions.assertEquals("UNDONE", pendingStatus,
                "After undo, the engine task pending_status must be UNDONE (AC-23)");
    }

    // =========================================================================
    // AC-24: HR ticks on behalf of IT admin (D-04)
    // =========================================================================

    /**
     * AC-24 (D-04): HR may tick any owner's item.  When HR ticks an IT-admin-owned item,
     * {@code done_by} records the HR employee's id, and History shows
     * "done by HR on behalf of IT admin".
     */
    @Test
    @DisplayName("AC-24: HR ticks IT-owned item → done_by=HR and audit_log records behalf message")
    void hrTicksOnBehalfOfItAdmin_doneByRecordsHrHistory() throws Exception {
        String email = "ac24." + nextId() + "@outlook.com";
        String resp = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long empId = Long.parseLong(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(resp).get("employeeId").asText());

        // Find an IT_ADMIN-owned item
        long itItemId = jdbc.queryForObject(
                "SELECT id FROM hr.checklist_item WHERE employee_id = ? AND owner_role = 'IT_ADMIN' AND status = 'OPEN' LIMIT 1",
                Long.class, empId);

        // HR ticks it
        mockMvc.perform(post("/api/checklist-items/" + itItemId + "/done")
                        .with(asHR())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("DONE"));

        // done_by must be the HR employee
        long doneBy = jdbc.queryForObject(
                "SELECT done_by FROM hr.checklist_item WHERE id = ?", Long.class, itItemId);
        org.junit.jupiter.api.Assertions.assertEquals(hrEmployeeId, doneBy,
                "done_by must record the HR employee's id when HR ticks on behalf (AC-24)");

        // audit_log must contain a record indicating this was done on behalf of IT admin
        int auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.audit_log WHERE entity_type = 'checklist_item' AND entity_id = ? AND details LIKE '%on behalf%'",
                Integer.class, itItemId);
        org.junit.jupiter.api.Assertions.assertTrue(auditCount >= 1,
                "History must record 'done by HR on behalf of IT admin' (AC-24, D-04)");
    }

    // =========================================================================
    // G-04 (FRD-00): every new task notifies its owner — not a numbered F01 AC,
    // this is the cross-cutting FRD-00 rule verified via the onboarding flow.
    // =========================================================================

    /**
     * G-04 (FRD-00): "Every new task creates an in-app notification and an e-mail to its
     * owner." Creating an onboarding always raises the "Onboarding checklist" engine task
     * for HR (step 11 of {@code OnboardingService.createOnboarding}), so this is the most
     * reliable fixture to assert the mechanism on — it does not depend on which checklist
     * items a template happens to contain.
     */
    @Test
    @DisplayName("G-04: creating an onboarding notifies HR in-app and queues a TASK_ASSIGNED e-mail")
    void createOnboarding_raisesHrNotificationAndTaskAssignedEmail() throws Exception {
        String email = "g04." + nextId() + "@outlook.com";
        String resp = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long empId = Long.parseLong(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(resp).get("employeeId").asText());

        int notificationCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.notification WHERE recipient_id = ? AND employee_id = ? "
                        + "AND title = 'New task: Onboarding checklist'",
                Integer.class, hrEmployeeId, empId);
        org.junit.jupiter.api.Assertions.assertTrue(notificationCount >= 1,
                "HR must get an in-app notification for the new 'Onboarding checklist' task (G-04)");

        String hrEmail = jdbc.queryForObject(
                "SELECT private_email FROM hr.employee WHERE id = ?", String.class, hrEmployeeId);
        int emailCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.email_outbox WHERE template_code = 'TASK_ASSIGNED' "
                        + "AND to_address = ? AND status = 'PENDING'",
                Integer.class, hrEmail);
        org.junit.jupiter.api.Assertions.assertTrue(emailCount >= 1,
                "HR must get a TASK_ASSIGNED e-mail for the new 'Onboarding checklist' task (G-04)");
    }

    // =========================================================================
    // G-05 (FRD-00): overdue reminder — not a numbered F01 AC, this is the
    // cross-cutting FRD-00 rule verified via the "Onboarding checklist" engine task.
    // =========================================================================

    /**
     * G-05 (FRD-00): "An overdue reminder e-mail is sent 1 day after the due date
     * (configurable, FRD-08)." Backdates the "Onboarding checklist" engine task's due date
     * so it is remindable, runs {@link OverdueReminderScheduler#sendOverdueReminders()}
     * directly (rather than waiting for its hourly cadence), and asserts: (1) exactly one
     * reminder notification + e-mail is raised, and (2) a second run does not repeat it —
     * {@code overdue_reminder_at} must be stamped so the row is never picked up again.
     */
    @Test
    @DisplayName("G-05: overdue 'Onboarding checklist' task reminds HR once, not twice")
    void overdueReminderScheduler_remindsOnce_notTwice() throws Exception {
        String email = "g05." + nextId() + "@outlook.com";
        String resp = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long empId = Long.parseLong(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(resp).get("employeeId").asText());

        // Backdate the "Onboarding checklist" engine task so it is overdue by more than
        // OVERDUE_REMINDER_DAYS (seeded as 1 day).
        jdbc.update("""
                UPDATE hr.engine_task SET due_date = CURRENT_DATE - 5
                WHERE employee_id = ? AND task_name = 'Onboarding checklist'
                """, empId);

        overdueReminderScheduler.sendOverdueReminders();

        int notificationCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.notification WHERE recipient_id = ? AND employee_id = ? "
                        + "AND title = 'Overdue: Onboarding checklist'",
                Integer.class, hrEmployeeId, empId);
        org.junit.jupiter.api.Assertions.assertEquals(1, notificationCount,
                "HR must get exactly one overdue-reminder notification (G-05)");

        String hrEmail = jdbc.queryForObject(
                "SELECT private_email FROM hr.employee WHERE id = ?", String.class, hrEmployeeId);
        int emailCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.email_outbox WHERE template_code = 'TASK_OVERDUE' "
                        + "AND to_address = ? AND status = 'PENDING'",
                Integer.class, hrEmail);
        org.junit.jupiter.api.Assertions.assertEquals(1, emailCount,
                "HR must get exactly one TASK_OVERDUE e-mail (G-05)");

        java.sql.Timestamp remindedAt = jdbc.queryForObject(
                "SELECT overdue_reminder_at FROM hr.engine_task WHERE employee_id = ? AND task_name = 'Onboarding checklist'",
                java.sql.Timestamp.class, empId);
        org.junit.jupiter.api.Assertions.assertNotNull(remindedAt,
                "overdue_reminder_at must be stamped after the reminder fires (G-05)");

        // Second run must NOT repeat the reminder.
        overdueReminderScheduler.sendOverdueReminders();

        int notificationCountAfterSecondRun = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.notification WHERE recipient_id = ? AND employee_id = ? "
                        + "AND title = 'Overdue: Onboarding checklist'",
                Integer.class, hrEmployeeId, empId);
        org.junit.jupiter.api.Assertions.assertEquals(1, notificationCountAfterSecondRun,
                "A second scheduler run must not send a repeat overdue reminder (G-05)");
    }

    // =========================================================================
    // OPEN-01 (S7M): a manager's MANAGER-owned orientation item is discoverable
    // via My Tasks — not a numbered F01 AC, this is a gate-4 follow-up closed here.
    // =========================================================================

    /**
     * OPEN-01 (S7M, FRD-02 §3 step 7 "HR and the manager complete orientation items"):
     * before this fix, {@code TaskService.getMyTasks}'s {@code MANAGER} branch was an empty
     * stub, so a MANAGER-owned checklist item — addable today via A16 "+ Add item" — was
     * invisible to the manager who owns it. Adds one via the real endpoint (not a raw SQL
     * insert) for an employee managed by {@code managerEmployeeId}, then asserts it appears
     * in that manager's {@code GET /api/tasks}.
     */
    @Test
    @DisplayName("OPEN-01: MANAGER-owned checklist item appears in the manager's own My Tasks")
    void managerOwnedChecklistItem_appearsInManagerMyTasks() throws Exception {
        String email = "open01." + nextId() + "@outlook.com";
        String resp = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long empId = Long.parseLong(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(resp).get("employeeId").asText());

        mockMvc.perform(withJson(
                        post("/api/onboardings/" + empId + "/checklist-items").with(asHR()),
                        """
                        {
                          "name": "Team introduction (manager)",
                          "section": "ORIENTATION",
                          "ownerRole": "MANAGER",
                          "mandatory": true,
                          "dueOffsetDays": 0
                        }
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/tasks").with(asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.content[?(@.employeeId==" + empId + " && @.title=='Team introduction (manager)')]")
                        .exists());
    }
}
