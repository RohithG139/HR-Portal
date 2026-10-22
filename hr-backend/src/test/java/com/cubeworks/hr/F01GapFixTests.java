package com.cubeworks.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests (Testcontainers PostgreSQL) for the F01 onboarding gap fixes recorded in
 * spec.md §12 D-23…D-30 (FRD-02 v1.2 audit, 2026-10-21). One test (or a small group) per fix;
 * every validation message is asserted with its exact text (R-SRC-3), every new/changed endpoint
 * has a forbidden-role 403 check (DoD §2.8.4).
 */
class F01GapFixTests extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final byte[] PDF_BYTES = "%PDF-1.4\n%test\n".getBytes(StandardCharsets.US_ASCII);

    // ── helpers ──────────────────────────────────────────────────────────────

    private long createOnboarding(final String email) throws Exception {
        return createOnboardingWithBody(validOnboardingBody(email, managerEmployeeId));
    }

    private long createOnboardingWithBody(final String body) throws Exception {
        final String resp = mockMvc.perform(withJson(post("/api/onboardings").with(asHR()), body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(resp).get("employeeId").asLong();
    }

    private String email(final String tag) {
        return "gap." + tag + "." + nextId() + "@outlook.com";
    }

    /** Inserts a plain employee with an IdP subject so the test can act as them. */
    private long insertEmployee(final String sub, final String appRole) {
        final long id = nextId();
        return jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, idp_subject, manager_id
                ) VALUES (?, 'Gap', 'Tester', ?, '+31 6 12345678', '1995-01-01', 'Street 1', '3511 AV',
                          'Utrecht', ?, ?, 'JUNIOR', ?, ?, ?)
                RETURNING id
                """, Long.class, "E-GAP-" + id, "gap.emp." + id + "@outlook.com",
                developerJobTitleId, developmentDeptId, appRole, sub, managerEmployeeId);
    }

    private long insertItem(final long employeeId, final String name, final String section,
                            final String owner, final String kind, final boolean mandatory) {
        return jdbc.queryForObject("""
                INSERT INTO hr.checklist_item (employee_id, phase, name, section, owner_role, item_kind,
                                               mandatory, due_date, status)
                VALUES (?, 'ONBOARDING', ?, ?, ?, ?, ?, '2027-01-05', 'OPEN') RETURNING id
                """, Long.class, employeeId, name, section, owner, kind, mandatory);
    }

    private long uploadDocument(final String docType, final String fileName, final byte[] bytes)
            throws Exception {
        final MockMultipartFile file = new MockMultipartFile("file", fileName, "application/pdf", bytes);
        final String resp = mockMvc.perform(multipart("/api/documents").file(file)
                        .param("docType", docType).with(asHR()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(resp).get("documentId").asLong();
    }

    private String subOf(final long employeeId) {
        return jdbc.queryForObject("SELECT idp_subject FROM hr.employee WHERE id = ?", String.class, employeeId);
    }

    // =========================================================================
    // 1. Item kinds — V18 seed, settings DTO/request (itemKind, policyDocumentId)
    // =========================================================================

    @Test
    @DisplayName("V18: seeded IT items are EQUIPMENT/ACCOUNT; HR-owned policy/profile items stay GENERIC")
    void v18_setsItemKindsOnSeededTemplateItems() {
        assertEquals("EQUIPMENT", jdbc.queryForObject(
                "SELECT item_kind FROM hr.checklist_template_item WHERE template_id = ? AND name = 'Monitor'",
                String.class, developmentTemplateId));
        assertEquals("ACCOUNT", jdbc.queryForObject(
                "SELECT item_kind FROM hr.checklist_template_item WHERE template_id = ? AND name = 'Slack'",
                String.class, developmentTemplateId));
        assertEquals("GENERIC", jdbc.queryForObject(
                "SELECT item_kind FROM hr.checklist_template_item WHERE template_id = ? AND name = 'Policies acknowledged'",
                String.class, developmentTemplateId));
    }

    @Test
    @DisplayName("Settings C11/C12: HR sets itemKind POLICY_ACK + policyDocumentId; bad kind → 400; IT → 403")
    void settingsItem_itemKindAndPolicyDocument() throws Exception {
        final String tplResp = mockMvc.perform(withJson(post("/api/settings/checklist-templates").with(asHR()),
                        "{\"name\":\"Gap template " + nextId() + "\",\"kind\":\"ONBOARDING\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        final long templateId = JSON.readTree(tplResp).get("id").asLong();
        final long policyId = uploadDocument("POLICY", "code-of-conduct.pdf", PDF_BYTES);

        final String itemResp = mockMvc.perform(withJson(
                        post("/api/settings/checklist-templates/" + templateId + "/items").with(asHR()),
                        """
                        {"name":"Read the code of conduct","section":"DOCUMENTS_ADMIN","ownerRole":"EMPLOYEE",
                         "mandatory":true,"dueOffsetDays":0,"itemKind":"POLICY_ACK","policyDocumentId":%d}
                        """.formatted(policyId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itemKind").value("POLICY_ACK"))
                .andExpect(jsonPath("$.policyDocumentId").value(policyId))
                .andReturn().getResponse().getContentAsString();
        final long itemId = JSON.readTree(itemResp).get("id").asLong();

        // Switching the kind away from POLICY_ACK drops the policy link (DB CHECK)
        mockMvc.perform(withJson(
                        put("/api/settings/checklist-templates/" + templateId + "/items/" + itemId).with(asHR()),
                        """
                        {"name":"Complete your profile","section":"DOCUMENTS_ADMIN","ownerRole":"EMPLOYEE",
                         "mandatory":true,"dueOffsetDays":0,"itemKind":"PROFILE","policyDocumentId":%d}
                        """.formatted(policyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemKind").value("PROFILE"))
                .andExpect(jsonPath("$.policyDocumentId").doesNotExist());

        mockMvc.perform(withJson(
                        post("/api/settings/checklist-templates/" + templateId + "/items").with(asHR()),
                        """
                        {"name":"X","section":"DOCUMENTS_ADMIN","ownerRole":"EMPLOYEE",
                         "mandatory":true,"dueOffsetDays":0,"itemKind":"NOT_A_KIND"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("itemKind"))
                .andExpect(jsonPath("$.errors[0].message").value("This field is required."));

        mockMvc.perform(withJson(
                        post("/api/settings/checklist-templates/" + templateId + "/items").with(asIT()),
                        "{\"name\":\"X\",\"section\":\"DOCUMENTS_ADMIN\",\"ownerRole\":\"EMPLOYEE\",\"mandatory\":true,\"dueOffsetDays\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // =========================================================================
    // 2. Policy acknowledgement, equipment confirmation, profile → PROFILE item
    // =========================================================================

    @Test
    @DisplayName("A27: POLICY_ACK item → DONE + document(POLICY_ACK); wrong kind → 409; other employee/HR → 403")
    void acknowledgePolicy_requiresPolicyAckAndWritesDocument() throws Exception {
        final String sub = "sub-gap-ack-" + nextId();
        final long empId = insertEmployee(sub, "EMPLOYEE");
        final long ackItem = insertItem(empId, "Read company policy", "DOCUMENTS_ADMIN", "EMPLOYEE", "POLICY_ACK", true);
        final long genericItem = insertItem(empId, "Bring your bank card", "DOCUMENTS_ADMIN", "EMPLOYEE", "GENERIC", true);

        mockMvc.perform(withJson(post("/api/checklist-items/" + genericItem + "/acknowledge")
                        .with(asUserWithSub(sub, "EMPLOYEE")), "{\"accepted\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mockMvc.perform(withJson(post("/api/checklist-items/" + ackItem + "/acknowledge")
                        .with(asEmployee()), "{\"accepted\":true}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(withJson(post("/api/checklist-items/" + ackItem + "/acknowledge")
                        .with(asHR()), "{\"accepted\":true}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(withJson(post("/api/checklist-items/" + ackItem + "/acknowledge")
                        .with(asUserWithSub(sub, "EMPLOYEE")), "{\"accepted\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Confirm that you have read the policy."));

        mockMvc.perform(withJson(post("/api/checklist-items/" + ackItem + "/acknowledge")
                        .with(asUserWithSub(sub, "EMPLOYEE")), "{\"accepted\":true}"))
                .andExpect(status().isOk());

        assertEquals("DONE", jdbc.queryForObject(
                "SELECT status FROM hr.checklist_item WHERE id = ?", String.class, ackItem));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.document WHERE employee_id = ? AND doc_type = 'POLICY_ACK'",
                Integer.class, empId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.audit_log WHERE employee_id = ? AND action = 'ACKNOWLEDGE_POLICY'",
                Integer.class, empId));
    }

    @Test
    @DisplayName("A28: only the caller's issued assets can be confirmed (403 otherwise); wrong kind → 409; audited")
    void confirmEquipment_validatesAssetsAndKind() throws Exception {
        final String sub = "sub-gap-eq-" + nextId();
        final long empId = insertEmployee(sub, "EMPLOYEE");
        final long confirmItem = insertItem(empId, "Confirm equipment received", "EQUIPMENT_ISSUED", "EMPLOYEE",
                "EQUIPMENT_CONFIRM", true);
        final long genericItem = insertItem(empId, "Something else", "ORIENTATION", "EMPLOYEE", "GENERIC", false);
        final long mine = jdbc.queryForObject("""
                INSERT INTO hr.asset (asset_number, asset_type, model, status, employee_id, issued_on)
                VALUES (?, 'LAPTOP', 'MacBook', 'ISSUED', ?, '2027-01-05') RETURNING id
                """, Long.class, "CW-GAP-" + nextId(), empId);
        final long someoneElses = jdbc.queryForObject("""
                INSERT INTO hr.asset (asset_number, asset_type, model, status, employee_id, issued_on)
                VALUES (?, 'MONITOR', 'Dell', 'ISSUED', ?, '2027-01-05') RETURNING id
                """, Long.class, "CW-GAP-" + nextId(), baseEmployeeId);

        mockMvc.perform(withJson(post("/api/checklist-items/" + confirmItem + "/confirm-equipment")
                        .with(asUserWithSub(sub, "EMPLOYEE")),
                        "{\"receivedAssetIds\":[%d,%d]}".formatted(mine, someoneElses)))
                .andExpect(status().isForbidden());
        assertEquals("OPEN", jdbc.queryForObject(
                "SELECT status FROM hr.checklist_item WHERE id = ?", String.class, confirmItem));

        mockMvc.perform(withJson(post("/api/checklist-items/" + genericItem + "/confirm-equipment")
                        .with(asUserWithSub(sub, "EMPLOYEE")), "{\"receivedAssetIds\":[]}"))
                .andExpect(status().isConflict());

        mockMvc.perform(withJson(post("/api/checklist-items/" + confirmItem + "/confirm-equipment")
                        .with(asUserWithSub(sub, "EMPLOYEE")), "{\"receivedAssetIds\":[%d]}".formatted(mine)))
                .andExpect(status().isOk());
        assertEquals("DONE", jdbc.queryForObject(
                "SELECT status FROM hr.checklist_item WHERE id = ?", String.class, confirmItem));
        final String after = jdbc.queryForObject("""
                SELECT after_value::text FROM hr.audit_log
                WHERE entity_id = ? AND action = 'CONFIRM_EQUIPMENT'
                """, String.class, confirmItem);
        assertTrue(after.contains("\"receivedAssetIds\": [" + mine + "]"), after);
    }

    @Test
    @DisplayName("A30: saving a complete own profile ticks the open PROFILE item; incomplete → stays OPEN")
    void myProfileSave_ticksProfileItem() throws Exception {
        final long empId = createOnboarding(email("profile"));
        final String sub = subOf(empId);
        final long profileItem = insertItem(empId, "Complete your profile", "DOCUMENTS_ADMIN", "EMPLOYEE", "PROFILE", true);

        mockMvc.perform(withJson(put("/api/me/profile").with(asUserWithSub(sub, "EMPLOYEE")),
                        "{\"city\":\"Utrecht\"}"))
                .andExpect(status().isOk());
        assertEquals("OPEN", jdbc.queryForObject(
                "SELECT status FROM hr.checklist_item WHERE id = ?", String.class, profileItem));

        mockMvc.perform(withJson(put("/api/me/profile").with(asUserWithSub(sub, "EMPLOYEE")),
                        "{\"emergencyName\":\"Anna Willems\",\"emergencyPhone\":\"+31 6 87654321\"}"))
                .andExpect(status().isOk());
        assertEquals("DONE", jdbc.queryForObject(
                "SELECT status FROM hr.checklist_item WHERE id = ?", String.class, profileItem));
        assertEquals(empId, jdbc.queryForObject(
                "SELECT done_by FROM hr.checklist_item WHERE id = ?", Long.class, profileItem));
    }

    // =========================================================================
    // 4. Undo: complete endpoints return taskId; undo + re-complete works
    // =========================================================================

    @Test
    @DisplayName("A19 → 202 {undoUntil, taskId}; A37 undo with that id → 200; completing again → 202")
    void completeOnboarding_returnsTaskId_undoThenRecomplete() throws Exception {
        final long empId = createOnboarding(email("undo"));
        jdbc.update("""
                UPDATE hr.checklist_item SET status = 'DONE', done_at = now(), done_by = ?
                WHERE employee_id = ? AND mandatory
                """, hrEmployeeId, empId);

        final String resp = mockMvc.perform(post("/api/onboardings/" + empId + "/complete").with(asHR()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.undoUntil").exists())
                .andExpect(jsonPath("$.taskId").isNumber())
                .andReturn().getResponse().getContentAsString();
        final long taskId = JSON.readTree(resp).get("taskId").asLong();
        assertEquals("Onboarding checklist", jdbc.queryForObject(
                "SELECT task_name FROM hr.engine_task WHERE id = ?", String.class, taskId));

        mockMvc.perform(post("/api/tasks/" + taskId + "/undo").with(asHR()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/onboardings/" + empId + "/complete").with(asHR()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").value(taskId));
    }

    // =========================================================================
    // 8. Drafts (A09a) and A12 validation / start-date shift / DB-constraint handler
    // =========================================================================

    @Test
    @DisplayName("A09a GET /api/onboarding-drafts/{id}: full form restored; 404 unknown; 403 for non-HR")
    void getDraft_returnsFullForm() throws Exception {
        final String resp = mockMvc.perform(withJson(post("/api/onboarding-drafts").with(asHR()), """
                        {"firstName":"Draft","lastName":"Person",
                         "form":{"privateEmail":"draft.person@outlook.com","hoursPerWeek":"32","idChecked":true}}
                        """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        final long draftId = JSON.readTree(resp).get("draftId").asLong();

        mockMvc.perform(withJson(put("/api/onboarding-drafts/" + draftId).with(asHR()), """
                        {"firstName":"Draft","lastName":"Persoon",
                         "form":{"privateEmail":"draft.person@outlook.com","hoursPerWeek":"24","city":"Utrecht"}}
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/onboarding-drafts/" + draftId).with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(draftId))
                .andExpect(jsonPath("$.firstName").value("Draft"))
                .andExpect(jsonPath("$.lastName").value("Persoon"))
                .andExpect(jsonPath("$.form.privateEmail").value("draft.person@outlook.com"))
                .andExpect(jsonPath("$.form.hoursPerWeek").value("24"))
                .andExpect(jsonPath("$.form.city").value("Utrecht"));

        mockMvc.perform(get("/api/onboarding-drafts/999999999").with(asHR()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/onboarding-drafts/" + draftId).with(asIT()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/onboarding-drafts/" + draftId).with(asManager()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A12: hours out of range → 400 'Hours must be between 1 and 40.'; 403 for IT")
    void updateDetails_hoursValidated() throws Exception {
        final long empId = createOnboarding(email("hours"));
        mockMvc.perform(withJson(put("/api/onboardings/" + empId + "/details").with(asHR()),
                        "{\"hoursPerWeek\":41}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("hoursPerWeek"))
                .andExpect(jsonPath("$.errors[0].message").value("Hours must be between 1 and 40."));
        mockMvc.perform(withJson(put("/api/onboardings/" + empId + "/details").with(asHR()),
                        "{\"firstName\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("This field is required."));
        mockMvc.perform(withJson(put("/api/onboardings/" + empId + "/details").with(asIT()),
                        "{\"hoursPerWeek\":32}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A12 fixed-term: end date before start → exact message; a PERMANENT end date is ignored")
    void updateDetails_fixedTermEndDateRules() throws Exception {
        final String body = validOnboardingBody(email("ft"), managerEmployeeId)
                .replace("\"contractType\": \"PERMANENT\"", "\"contractType\": \"FIXED_TERM\", \"endDate\": \"2027-12-31\"");
        final long ftId = createOnboardingWithBody(body);
        mockMvc.perform(withJson(put("/api/onboardings/" + ftId + "/details").with(asHR()),
                        "{\"endDate\":\"2026-12-31\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("endDate"))
                .andExpect(jsonPath("$.errors[0].message").value("The end date must be after the start date."));
        // Moving the start past the existing end date is caught the same way
        mockMvc.perform(withJson(put("/api/onboardings/" + ftId + "/details").with(asHR()),
                        "{\"startDate\":\"2028-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("The end date must be after the start date."));

        final long permId = createOnboarding(email("perm"));
        mockMvc.perform(withJson(put("/api/onboardings/" + permId + "/details").with(asHR()),
                        "{\"endDate\":\"2027-12-31\"}"))
                .andExpect(status().isOk());
        assertNull(jdbc.queryForObject(
                "SELECT end_date FROM hr.contract WHERE employee_id = ? AND is_current", java.sql.Date.class, permId));
    }

    @Test
    @DisplayName("A12 start date moved: open item due dates and the 'Onboarding checklist' task move with it")
    void updateDetails_startDateShiftsDueDates() throws Exception {
        final long empId = createOnboarding(email("shift"));
        final long doneItem = jdbc.queryForObject(
                "SELECT id FROM hr.checklist_item WHERE employee_id = ? ORDER BY id LIMIT 1", Long.class, empId);
        jdbc.update("UPDATE hr.checklist_item SET status = 'DONE', done_at = now(), done_by = ? WHERE id = ?",
                hrEmployeeId, doneItem);
        final List<Map<String, Object>> before = jdbc.queryForList(
                "SELECT id, due_date FROM hr.checklist_item WHERE employee_id = ? ORDER BY id", empId);

        mockMvc.perform(withJson(put("/api/onboardings/" + empId + "/details").with(asHR()),
                        "{\"startDate\":\"2027-01-15\"}"))   // +10 days
                .andExpect(status().isOk());

        for (final Map<String, Object> row : before) {
            final long id = ((Number) row.get("id")).longValue();
            final java.time.LocalDate was = ((java.sql.Date) row.get("due_date")).toLocalDate();
            final java.time.LocalDate now = jdbc.queryForObject(
                    "SELECT due_date FROM hr.checklist_item WHERE id = ?", java.sql.Date.class, id).toLocalDate();
            assertEquals(id == doneItem ? was : was.plusDays(10), now, "item " + id);
        }
        assertEquals(java.time.LocalDate.of(2027, 1, 15), jdbc.queryForObject("""
                SELECT due_date FROM hr.engine_task WHERE employee_id = ? AND task_name = 'Onboarding checklist'
                """, java.sql.Date.class, empId).toLocalDate());
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM hr.audit_log WHERE employee_id = ? AND action = 'UPDATE_DETAILS'
                  AND after_value ->> 'startDate' = '2027-01-15'
                """, Integer.class, empId));
    }

    @Test
    @DisplayName("DB constraint violation → 409 CONFLICT in R-API-3 format, not 500")
    void dataIntegrityViolation_returns409() throws Exception {
        final long empId = createOnboarding(email("dbc"));
        final String tooLong = "x".repeat(300); // employee.first_name is varchar(100)
        mockMvc.perform(withJson(put("/api/onboardings/" + empId + "/details").with(asHR()),
                        "{\"firstName\":\"" + tooLong + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("The change conflicts with existing data."));
    }

    // =========================================================================
    // 9. A15 filter parameter names
    // =========================================================================

    @Test
    @DisplayName("A15 ?ownerFilter=IT_ADMIN&statusFilter=OPEN returns only open IT items")
    void checklist_ownerAndStatusFilters() throws Exception {
        final long empId = createOnboarding(email("filter"));
        final MvcResult r = mockMvc.perform(get("/api/onboardings/" + empId + "/checklist")
                        .param("ownerFilter", "IT_ADMIN").param("statusFilter", "OPEN").with(asHR()))
                .andExpect(status().isOk()).andReturn();
        final JsonNode sections = JSON.readTree(r.getResponse().getContentAsString()).get("sections");
        int count = 0;
        for (final JsonNode s : sections) {
            for (final JsonNode i : s.get("items")) {
                assertEquals("IT_ADMIN", i.get("ownerRole").asText());
                assertEquals("OPEN", i.get("status").asText());
                count++;
            }
        }
        assertTrue(count > 0);
    }

    // =========================================================================
    // 11. Step 1: manager + probation required; contract upload linked
    // =========================================================================

    @Test
    @DisplayName("A10: missing manager / probation period → 400 'This field is required.'")
    void createOnboarding_managerAndProbationRequired() throws Exception {
        final String noManager = validOnboardingBody(email("nomgr"), managerEmployeeId)
                .replace("\"managerId\": " + managerEmployeeId + ",", "");
        mockMvc.perform(withJson(post("/api/onboardings").with(asHR()), noManager))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("managerId"))
                .andExpect(jsonPath("$.errors[0].message").value("This field is required."));

        final String noProbation = validOnboardingBody(email("noprob"), managerEmployeeId)
                .replace("\"probationMonths\": 2,", "");
        mockMvc.perform(withJson(post("/api/onboardings").with(asHR()), noProbation))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("probationMonths"))
                .andExpect(jsonPath("$.errors[0].message").value("This field is required."));
    }

    @Test
    @DisplayName("A07 + A10: uploaded CONTRACT is linked to the new employee via contractDocumentId")
    void contractUpload_linkedOnCreate() throws Exception {
        final long docId = uploadDocument("CONTRACT", "contract.pdf", PDF_BYTES);
        assertEquals("application/pdf", jdbc.queryForObject(
                "SELECT content_type FROM hr.document WHERE id = ?", String.class, docId));
        final String body = validOnboardingBody(email("contract"), managerEmployeeId)
                .replace("\"idChecked\": true,", "\"idChecked\": true, \"contractDocumentId\": " + docId + ",");
        final long empId = createOnboardingWithBody(body);
        assertEquals(empId, jdbc.queryForObject(
                "SELECT employee_id FROM hr.document WHERE id = ?", Long.class, docId));
    }

    // =========================================================================
    // 12. ONB-09 case-insensitive duplicate
    // =========================================================================

    @Test
    @DisplayName("ONB-09: same e-mail in different case → 409 DUPLICATE_PRIVATE_EMAIL with existingEmployeeId")
    void duplicateEmail_isCaseInsensitive() throws Exception {
        final String mail = email("dupcase");
        final long existing = createOnboarding(mail);
        mockMvc.perform(withJson(post("/api/onboardings").with(asHR()),
                        validOnboardingBody(mail.toUpperCase(java.util.Locale.ROOT), managerEmployeeId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PRIVATE_EMAIL"))
                .andExpect(jsonPath("$.message").value("An employee with this e-mail address already exists."))
                .andExpect(jsonPath("$.existingEmployeeId").value(existing));
    }

    // =========================================================================
    // 13. ONB-06: asset ↔ checklist item link, match by kind/name, reopen on removal
    // =========================================================================

    @Test
    @DisplayName("A21/A22: asset ticks the matching item and sets asset_id; OTHER ticks nothing; removal reopens")
    void issueAsset_linksMatchingItem_unissueReopens() throws Exception {
        final long empId = createOnboarding(email("asset"));
        final long monitorItem = jdbc.queryForObject("""
                SELECT id FROM hr.checklist_item WHERE employee_id = ? AND name = 'Monitor'
                """, Long.class, empId);
        final long laptopItem = jdbc.queryForObject("""
                SELECT id FROM hr.checklist_item WHERE employee_id = ? AND name LIKE 'Laptop%'
                """, Long.class, empId);

        // An "Other" asset must not tick the first open equipment item any more
        mockMvc.perform(withJson(post("/api/employees/" + empId + "/assets").with(asIT()),
                        "{\"assetType\":\"OTHER\",\"model\":\"Cable\",\"assetNumber\":\"CW-OT-" + nextId() + "\"}"))
                .andExpect(status().isCreated());
        assertEquals("OPEN", jdbc.queryForObject(
                "SELECT status FROM hr.checklist_item WHERE id = ?", String.class, laptopItem));

        final String resp = mockMvc.perform(withJson(post("/api/employees/" + empId + "/assets").with(asIT()),
                        "{\"assetType\":\"MONITOR\",\"model\":\"Dell U2723\",\"assetNumber\":\"CW-MO-" + nextId() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        final long assetId = JSON.readTree(resp).get("id").asLong();
        assertEquals("DONE", jdbc.queryForObject(
                "SELECT status FROM hr.checklist_item WHERE id = ?", String.class, monitorItem));
        assertEquals(assetId, jdbc.queryForObject(
                "SELECT asset_id FROM hr.checklist_item WHERE id = ?", Long.class, monitorItem));
        assertEquals("OPEN", jdbc.queryForObject(
                "SELECT status FROM hr.checklist_item WHERE id = ?", String.class, laptopItem));

        mockMvc.perform(post("/api/assets/" + assetId + "/unissue").with(asHR()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/assets/" + assetId + "/unissue").with(asIT()))
                .andExpect(status().isOk());
        assertEquals("OPEN", jdbc.queryForObject(
                "SELECT status FROM hr.checklist_item WHERE id = ?", String.class, monitorItem));
        assertNull(jdbc.queryForObject(
                "SELECT asset_id FROM hr.checklist_item WHERE id = ?", Long.class, monitorItem));
    }

    // =========================================================================
    // 15. IT_EQUIPMENT_REQUEST goes to every active IT admin
    // =========================================================================

    @Test
    @DisplayName("A14: IT_EQUIPMENT_REQUEST e-mail → one outbox row per active IT admin, never a hard-coded address")
    void sendItRequest_emailsEveryActiveItAdmin() throws Exception {
        final long empId = createOnboarding(email("itmail"));
        mockMvc.perform(withJson(post("/api/onboardings/" + empId + "/it-request/send").with(asHR()),
                        validItRequestBody("2027-01-04")))
                .andExpect(status().isOk());

        final List<String> to = jdbc.queryForList("""
                SELECT to_address FROM hr.email_outbox
                WHERE employee_id = ? AND template_code = 'IT_EQUIPMENT_REQUEST'
                """, String.class, empId);
        final List<String> expected = jdbc.queryForList("""
                SELECT COALESCE(work_email, private_email) FROM hr.employee
                WHERE app_role = 'IT_ADMIN' AND stage <> 'ARCHIVED' AND account_status <> 'DISABLED'
                """, String.class);
        assertEquals(new java.util.HashSet<>(expected), new java.util.HashSet<>(to));
        assertEquals(expected.size(), to.size());
        assertFalse(to.contains("it-admin@cubeworks.internal"));
        assertTrue(to.contains(jdbc.queryForObject(
                "SELECT private_email FROM hr.employee WHERE id = ?", String.class, itEmployeeId)));
    }

    // =========================================================================
    // 18. M3: A07 hardening and A26 Content-Disposition
    // =========================================================================

    @Test
    @DisplayName("A07: bad docType → 400; non-PDF bytes sent as application/pdf → 400; IT → 403")
    void uploadDocument_validatesTypeAndSignature() throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "c.pdf", "application/pdf", PDF_BYTES))
                        .param("docType", "BOGUS").with(asHR()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("docType"))
                .andExpect(jsonPath("$.errors[0].message").value("This field is required."));
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "c.pdf", "application/pdf", PDF_BYTES))
                        .param("docType", "POLICY_ACK").with(asHR()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "evil.pdf", "application/pdf",
                                "<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)))
                        .param("docType", "CONTRACT").with(asHR()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"))
                .andExpect(jsonPath("$.errors[0].message").value("This file type is not allowed."));
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "c.pdf", "application/pdf", PDF_BYTES))
                        .param("docType", "CONTRACT").with(asIT()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A26: Content-Disposition filename is escaped / RFC 5987-encoded")
    void policyDocument_contentDispositionEscaped() throws Exception {
        final long policyId = uploadDocument("POLICY", "pol\"icyé.pdf", PDF_BYTES);
        final long templateItemId = jdbc.queryForObject("""
                INSERT INTO hr.checklist_template_item (template_id, name, section, owner_role, item_kind,
                                                        mandatory, due_offset_days, policy_document_id)
                VALUES (?, 'Gap policy', 'DOCUMENTS_ADMIN', 'EMPLOYEE', 'POLICY_ACK', false, 0, ?) RETURNING id
                """, Long.class, developmentTemplateId, policyId);
        try {
            final String sub = "sub-gap-pol-" + nextId();
            final long empId = insertEmployee(sub, "EMPLOYEE");
            final long itemId = jdbc.queryForObject("""
                    INSERT INTO hr.checklist_item (employee_id, phase, template_item_id, name, section, owner_role,
                                                   item_kind, mandatory, due_date, status)
                    VALUES (?, 'ONBOARDING', ?, 'Gap policy', 'DOCUMENTS_ADMIN', 'EMPLOYEE', 'POLICY_ACK', false,
                            '2027-01-05', 'OPEN') RETURNING id
                    """, Long.class, empId, templateItemId);

            final String header = mockMvc.perform(get("/api/checklist-items/" + itemId + "/policy")
                            .with(asUserWithSub(sub, "EMPLOYEE")))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", containsString("inline")))
                    .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''")))
                    .andExpect(header().string("Content-Disposition", not(containsString("pol\"icy"))))
                    .andReturn().getResponse().getHeader("Content-Disposition");
            assertNotNull(header);
        } finally {
            // keep the shared Development template as seeded for other test classes
            jdbc.update("DELETE FROM hr.checklist_item WHERE template_item_id = ?", templateItemId);
            jdbc.update("DELETE FROM hr.checklist_template_item WHERE id = ?", templateItemId);
        }
    }
}
