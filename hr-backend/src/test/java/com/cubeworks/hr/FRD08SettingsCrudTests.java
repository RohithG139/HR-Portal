package com.cubeworks.hr;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for FRD-08 Settings CRUD — happy paths and key business rules.
 *
 * <h2>Coverage</h2>
 * <pre>
 * AC-01  New job title appears in lookups after creation
 * AC-02  Remove section from appraisal form; form has one fewer section
 * AC-04  Timer update: PIP_CHECKIN value changes; reading back confirms new value
 * AC-07  Department in use returns 409 ITEM_IN_USE (ADM-03)
 * AC-08  Archiving a template does not affect existing checklist_item rows
 * AC-12  Test-send: writes to email_outbox and returns 202
 * AC-16  Settings change is written to audit_log (ADM-05)
 * D-02   Deleting a template item that is in use returns 409 ITEM_IN_USE
 * </pre>
 */
class FRD08SettingsCrudTests extends AbstractApiTest {

    @BeforeAll
    void setUp() {
        super.setUpActors();
    }

    /**
     * Reads a setting's current {@code version} directly from the database (D-10: timer
     * updates are now optimistic-locked). Queried fresh before every PUT rather than assumed,
     * since multiple test classes share the same Testcontainers database and may have already
     * updated the same seeded row.
     */
    private int currentTimerVersion(final String code) {
        return jdbc.queryForObject("SELECT version FROM hr.setting WHERE code = ?", Integer.class, code);
    }

    // ── AC-01: New job title appears everywhere ───────────────────────────────

    /**
     * AC-01: Given HR adds the job title "Account Manager",
     * then GET /api/settings/job-titles includes it.
     */
    @Test
    @DisplayName("AC-01: createJobTitle_givenValidName_appearsInList")
    void createJobTitle_givenValidName_appearsInList() throws Exception {
        final long suffix = nextId();
        final String name = "Account Manager " + suffix;

        // Create
        perform(withJson(post("/api/settings/job-titles"), """
                {"name":"%s"}
                """.formatted(name)).with(asHR()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name));

        // Verify it appears in the list
        perform(get("/api/settings/job-titles").with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '%s')]".formatted(name)).exists());
    }

    /**
     * AC-01 (extended): New department also appears in lookups after creation.
     */
    @Test
    @DisplayName("AC-01: createDepartment_givenValidName_appearsInList")
    void createDepartment_givenValidName_appearsInList() throws Exception {
        final long suffix = nextId();
        final String name = "Finance " + suffix;

        perform(withJson(post("/api/settings/departments"), """
                {"name":"%s"}
                """.formatted(name)).with(asHR()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.employeeCount").value(0));

        perform(get("/api/settings/departments").with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '%s')]".formatted(name)).exists());
    }

    // ── AC-02: Remove a section from a form ──────────────────────────────────

    /**
     * AC-02: Given the Standard appraisal form has N sections,
     * when HR removes a section, the form has N-1 sections.
     */
    @Test
    @DisplayName("AC-02: deleteSection_givenExistingSection_formHasFewerSections")
    void deleteSection_givenExistingSection_formHasFewerSections() throws Exception {
        // Create a form
        final long suffix = nextId();
        final String createFormBody = """
                {"name":"Test Form %d","usedFor":"STANDARD"}
                """.formatted(suffix);

        final String formResponse = perform(withJson(post("/api/settings/appraisal-forms"), createFormBody)
                .with(asHR()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        final long formId = Long.parseLong(
                formResponse.replaceAll(".*\"id\":(\\d+).*", "$1"));

        // Add two sections
        final String sectionBody = """
                {"name":"Results","bySelf":true,"byPeer":false,"byManager":true,"byHr":false}
                """;
        final String s1Response = perform(withJson(post("/api/settings/appraisal-forms/" + formId + "/sections"),
                sectionBody).with(asHR()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        final long s1Id = Long.parseLong(
                s1Response.replaceAll(".*\"id\":(\\d+).*", "$1"));

        perform(withJson(post("/api/settings/appraisal-forms/" + formId + "/sections"),
                """
                {"name":"Core","bySelf":false,"byPeer":false,"byManager":true,"byHr":false}
                """).with(asHR()))
                .andExpect(status().isCreated());

        // Delete the first section
        perform(delete("/api/settings/appraisal-forms/" + formId + "/sections/" + s1Id)
                .with(asHR()))
                .andExpect(status().isNoContent());

        // Verify only 1 section remains
        perform(get("/api/settings/appraisal-forms/" + formId).with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections", hasSize(1)))
                .andExpect(jsonPath("$.sections[0].name").value("Core"));
    }

    // ── AC-04: Timer change ───────────────────────────────────────────────────

    /**
     * AC-04: Given HR changes PIP_CHECKIN from 14 to 10 days,
     * then GET /api/settings/timers returns 10 for PIP_CHECKIN.
     * (Running PIPs are not affected — ADM-02, enforced by design, not tested here.)
     */
    @Test
    @DisplayName("AC-04: updateTimer_givenNewValue_valueIsPersistedAndReturned")
    void updateTimer_givenNewValue_valueIsPersistedAndReturned() throws Exception {
        perform(withJson(put("/api/settings/timers/PIP_CHECKIN"),
                """
                {"valueInt":10,"version":%d}
                """.formatted(currentTimerVersion("PIP_CHECKIN"))).with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PIP_CHECKIN"))
                .andExpect(jsonPath("$.valueInt").value(10));

        // Reading back confirms the change
        perform(get("/api/settings/timers").with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'PIP_CHECKIN')].valueInt")
                        .value(hasItem(10)));

        // Restore original value so other tests are unaffected
        perform(withJson(put("/api/settings/timers/PIP_CHECKIN"),
                """
                {"valueInt":14,"version":%d}
                """.formatted(currentTimerVersion("PIP_CHECKIN"))).with(asHR()))
                .andExpect(status().isOk());
    }

    // ── AC-06: KPI cycle change ───────────────────────────────────────────────

    /**
     * AC-06: Given HR changes the KPI cycle to Half-yearly,
     * then GET /api/settings/timers returns HALF_YEARLY for KPI_CYCLE.
     */
    @Test
    @DisplayName("AC-06: updateKpiCycle_givenHalfYearly_valueIsPersistedAndReturned")
    void updateKpiCycle_givenHalfYearly_valueIsPersistedAndReturned() throws Exception {
        perform(withJson(put("/api/settings/timers/KPI_CYCLE"),
                """
                {"valueText":"HALF_YEARLY","version":%d}
                """.formatted(currentTimerVersion("KPI_CYCLE"))).with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valueText").value("HALF_YEARLY"));

        // Restore original
        perform(withJson(put("/api/settings/timers/KPI_CYCLE"),
                """
                {"valueText":"QUARTERLY","version":%d}
                """.formatted(currentTimerVersion("KPI_CYCLE"))).with(asHR()))
                .andExpect(status().isOk());
    }

    // ── AC-07: Department in use cannot be removed ────────────────────────────

    /**
     * AC-07: Given the Development department has employees assigned (setUpActors creates them),
     * when HR tries to remove it, 409 ITEM_IN_USE is returned (ADM-03).
     */
    @Test
    @DisplayName("AC-07: deleteDepartment_givenDeptWithEmployees_returns409ItemInUse")
    void deleteDepartment_givenDeptWithEmployees_returns409ItemInUse() throws Exception {
        perform(delete("/api/settings/departments/" + developmentDeptId).with(asHR()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_IN_USE"))
                .andExpect(jsonPath("$.message").value("This item is in use and cannot be removed."));
    }

    // ── AC-08: Archiving a template does not affect existing checklist items ──

    /**
     * AC-08: When HR archives the Development template, the template no longer appears
     * in the active list (includeArchived=false), but its id is still accessible via detail.
     */
    @Test
    @DisplayName("AC-08: archiveTemplate_givenActiveTemplate_disappearsFromActiveList")
    void archiveTemplate_givenActiveTemplate_disappearsFromActiveList() throws Exception {
        // Create a fresh template to archive (avoid affecting seeded data)
        final long suffix = nextId();
        final String body = """
                {"name":"Temp Archive Test %d","kind":"ONBOARDING"}
                """.formatted(suffix);

        final String created = perform(withJson(post("/api/settings/checklist-templates"), body)
                .with(asHR()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        final long templateId = Long.parseLong(
                created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        // Archive it
        perform(post("/api/settings/checklist-templates/" + templateId + "/archive")
                .with(asHR()))
                .andExpect(status().isOk());

        // Must not appear in active list
        perform(get("/api/settings/checklist-templates?includeArchived=false")
                .with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + templateId + ")]").doesNotExist());

        // Must appear in full list
        perform(get("/api/settings/checklist-templates?includeArchived=true")
                .with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + templateId + ")]").exists());

        // Detail is still accessible
        perform(get("/api/settings/checklist-templates/" + templateId)
                .with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());
    }

    /**
     * AC-08 (strengthened — Gate 5 spec-coverage finding): archiving a template that already
     * has at least one instantiated {@code checklist_item} leaves that row completely
     * unchanged (ADM-04). The earlier {@code archiveTemplate_...} test above only proves
     * archiving works against a template with no items; this one proves the actual "stays on
     * existing cases" guarantee by inserting a real checklist_item first. Uses a fresh,
     * test-owned template (not the shared seeded "Development" one) so this test cannot
     * interfere with any other test's use of seed data.
     */
    @Test
    @DisplayName("AC-08: archiveTemplate_givenInstantiatedItem_leavesChecklistItemUnchanged")
    void archiveTemplate_givenInstantiatedItem_leavesChecklistItemUnchanged() throws Exception {
        final long suffix = nextId();
        final long templateId = jdbc.queryForObject("""
                INSERT INTO hr.checklist_template (name, kind, created_at, updated_at, version)
                VALUES (?, 'ONBOARDING', now(), now(), 0)
                RETURNING id
                """, Long.class, "AC-08 Archive Test " + suffix);

        final long itemId = jdbc.queryForObject("""
                INSERT INTO hr.checklist_template_item
                    (template_id, name, section, owner_role, mandatory, due_offset_days,
                     sort_order, created_at, updated_at, version)
                VALUES (?, 'Original item name', 'DOCUMENTS_ADMIN', 'HR', true, 0, 0, now(), now(), 0)
                RETURNING id
                """, Long.class, templateId);

        final long employeeId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, idp_subject
                ) VALUES (?, 'Test', 'Emp', ?, '+31 6 99000002', '1990-01-01',
                    'Test Street 2', '1000 AA', 'Amsterdam', ?, ?, 'JUNIOR', 'EMPLOYEE', ?)
                RETURNING id
                """, Long.class,
                "E-TST-" + suffix,
                "test.emp2." + suffix + "@test.com",
                developerJobTitleId, developmentDeptId, "sub-tst2-" + suffix);

        jdbc.update("""
                INSERT INTO hr.checklist_item (
                    employee_id, phase, template_item_id, name, section, owner_role,
                    item_kind, mandatory, due_date, created_at, updated_at, version)
                VALUES (?, 'ONBOARDING', ?, 'Original item name', 'DOCUMENTS_ADMIN', 'HR',
                    'GENERIC', true, '2027-01-10', now(), now(), 0)
                """, employeeId, itemId);

        final java.util.Map<String, Object> before = jdbc.queryForMap(
                "SELECT name, section, owner_role, mandatory FROM hr.checklist_item WHERE employee_id = ?",
                employeeId);

        // Archive the template itself — this must never touch checklist_item.
        perform(post("/api/settings/checklist-templates/" + templateId + "/archive")
                .with(asHR()))
                .andExpect(status().isOk());

        final java.util.Map<String, Object> after = jdbc.queryForMap(
                "SELECT name, section, owner_role, mandatory FROM hr.checklist_item WHERE employee_id = ?",
                employeeId);

        org.assertj.core.api.Assertions.assertThat(after).isEqualTo(before);
    }

    // ── AC-12: Test-send ──────────────────────────────────────────────────────

    /**
     * AC-12: When HR sends a test e-mail for the WELCOME_NEW_EMPLOYEE template,
     * 202 is returned and a row is written to email_outbox.
     */
    @Test
    @DisplayName("AC-12: testSend_givenValidTemplate_returns202AndWritesOutbox")
    void testSend_givenValidTemplate_returns202AndWritesOutbox() throws Exception {
        final long outboxCountBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.email_outbox", Long.class);

        perform(post("/api/settings/email-templates/WELCOME_NEW_EMPLOYEE/test")
                .with(asHR()))
                .andExpect(status().isAccepted());

        final long outboxCountAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.email_outbox", Long.class);

        org.assertj.core.api.Assertions.assertThat(outboxCountAfter)
                .isGreaterThan(outboxCountBefore);
    }

    // ── AC-16: Settings change is audited ─────────────────────────────────────

    /**
     * AC-16: When HR changes ONBOARDING_OVERDUE, an audit_log entry records the change
     * with entity_type=SETTING and the correct actor (ADM-05).
     */
    @Test
    @DisplayName("AC-16: updateTimer_givenValidChange_writesAuditLog")
    void updateTimer_givenValidChange_writesAuditLog() throws Exception {
        final long auditCountBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.audit_log WHERE entity_type='SETTING'", Long.class);

        perform(withJson(put("/api/settings/timers/ONBOARDING_OVERDUE"),
                """
                {"valueInt":4,"version":%d}
                """.formatted(currentTimerVersion("ONBOARDING_OVERDUE"))).with(asHR()))
                .andExpect(status().isOk());

        final long auditCountAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.audit_log WHERE entity_type='SETTING'", Long.class);

        org.assertj.core.api.Assertions.assertThat(auditCountAfter)
                .isGreaterThan(auditCountBefore);

        // Restore
        perform(withJson(put("/api/settings/timers/ONBOARDING_OVERDUE"),
                """
                {"valueInt":3,"version":%d}
                """.formatted(currentTimerVersion("ONBOARDING_OVERDUE"))).with(asHR()))
                .andExpect(status().isOk());
    }

    // ── D-02: Template item in use cannot be deleted ──────────────────────────

    /**
     * D-02: A template item that has been instantiated into a checklist_item row
     * cannot be deleted (FK RESTRICT) — returns 409 ITEM_IN_USE.
     *
     * <p>We simulate this by creating a checklist_item row that references an existing
     * template item from the seeded Development template, then trying to delete that item.</p>
     */
    @Test
    @DisplayName("D-02: deleteTemplateItem_givenItemInstantiated_returns409ItemInUse")
    void deleteTemplateItem_givenItemInstantiated_returns409ItemInUse() throws Exception {
        // Find the first item in the Development template
        final Long itemId = jdbc.queryForObject(
                "SELECT id FROM hr.checklist_template_item WHERE template_id = ? LIMIT 1",
                Long.class, developmentTemplateId);

        if (itemId == null) {
            // No items in the Development template — skip by creating one and not referencing it
            return;
        }

        // Insert a checklist_item row referencing this template_item_id
        // We need an onboarding to reference; create minimal onboarding data
        final long empSuffix = nextId();
        final long employeeId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, idp_subject
                ) VALUES (?, 'Test', 'Emp', ?, '+31 6 99000001', '1990-01-01',
                    'Test Street 1', '1000 AA', 'Amsterdam', ?, ?, 'JUNIOR', 'EMPLOYEE', ?)
                RETURNING id
                """, Long.class,
                "E-TST-" + empSuffix,
                "test.emp." + empSuffix + "@test.com",
                developerJobTitleId, developmentDeptId, "sub-tst-" + empSuffix);

        // Insert a checklist_item referencing the template item (actual checklist_item schema)
        jdbc.update("""
                INSERT INTO hr.checklist_item (
                    employee_id, phase, template_item_id, name, section, owner_role,
                    item_kind, mandatory, due_date, created_at, updated_at, version
                ) VALUES (?, 'ONBOARDING', ?, 'Test item', 'DOCUMENTS_ADMIN', 'HR',
                    'GENERIC', true, '2027-01-10', now(), now(), 0)
                """, employeeId, itemId);

        // Now trying to delete the template item must return 409 ITEM_IN_USE
        perform(delete("/api/settings/checklist-templates/" + developmentTemplateId + "/items/" + itemId)
                .with(asHR()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_IN_USE"))
                .andExpect(jsonPath("$.message").value("This item is in use and cannot be removed."));
    }

    // ── Duplicate name checks ─────────────────────────────────────────────────

    /**
     * Duplicate department name returns 409 DUPLICATE_NAME.
     */
    @Test
    @DisplayName("createDepartment_givenDuplicateName_returns409DuplicateName")
    void createDepartment_givenDuplicateName_returns409DuplicateName() throws Exception {
        perform(withJson(post("/api/settings/departments"),
                """
                {"name":"Development"}
                """).with(asHR()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"))
                .andExpect(jsonPath("$.message").value("This name already exists."));
    }

    /**
     * Duplicate job title name returns 409 DUPLICATE_NAME.
     */
    @Test
    @DisplayName("createJobTitle_givenDuplicateName_returns409DuplicateName")
    void createJobTitle_givenDuplicateName_returns409DuplicateName() throws Exception {
        perform(withJson(post("/api/settings/job-titles"),
                """
                {"name":"Developer"}
                """).with(asHR()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"))
                .andExpect(jsonPath("$.message").value("This name already exists."));
    }

    /**
     * Duplicate appraisal form name returns 409 DUPLICATE_NAME.
     */
    @Test
    @DisplayName("createAppraisalForm_givenDuplicateName_returns409DuplicateName")
    void createAppraisalForm_givenDuplicateName_returns409DuplicateName() throws Exception {
        perform(withJson(post("/api/settings/appraisal-forms"),
                """
                {"name":"Standard","usedFor":"STANDARD"}
                """).with(asHR()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"))
                .andExpect(jsonPath("$.message").value("This name already exists."));
    }

    // ── GET happy paths ───────────────────────────────────────────────────────

    @Test
    @DisplayName("listDepartments_returnsSeededDevelopmentDepartment")
    void listDepartments_returnsSeededDevelopmentDepartment() throws Exception {
        perform(get("/api/settings/departments").with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Development')]").exists());
    }

    @Test
    @DisplayName("listJobTitles_returnsSeededDeveloper")
    void listJobTitles_returnsSeededDeveloper() throws Exception {
        perform(get("/api/settings/job-titles").with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Developer')]").exists());
    }

    @Test
    @DisplayName("listChecklistTemplates_returnsSeededTemplates")
    void listChecklistTemplates_returnsSeededTemplates() throws Exception {
        perform(get("/api/settings/checklist-templates").with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("getChecklistTemplateDetail_givenSeededTemplate_returnsItems")
    void getChecklistTemplateDetail_givenSeededTemplate_returnsItems() throws Exception {
        perform(get("/api/settings/checklist-templates/" + developmentTemplateId).with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(developmentTemplateId))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    @DisplayName("listAppraisalForms_returnsSeededForms")
    void listAppraisalForms_returnsSeededForms() throws Exception {
        perform(get("/api/settings/appraisal-forms").with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.name == 'Standard')]").exists());
    }

    @Test
    @DisplayName("listEmailTemplates_returnsAllTemplates")
    void listEmailTemplates_returnsAllTemplates() throws Exception {
        perform(get("/api/settings/email-templates").with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(6))));
    }

    @Test
    @DisplayName("getEmailTemplate_givenWelcomeTemplate_returnsDetail")
    void getEmailTemplate_givenWelcomeTemplate_returnsDetail() throws Exception {
        perform(get("/api/settings/email-templates/WELCOME_NEW_EMPLOYEE").with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WELCOME_NEW_EMPLOYEE"))
                .andExpect(jsonPath("$.version").isNumber());
    }

    @Test
    @DisplayName("listTimers_returnsSeededTimerSettings")
    void listTimers_returnsSeededTimerSettings() throws Exception {
        perform(get("/api/settings/timers").with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.code == 'BENCH_CHECK')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'KPI_CYCLE')]").exists());
    }

    /**
     * AC-03 (Gate 5 spec-coverage finding — partial): the FRD's own acceptance scenario is
     * "the e-mail is sent to Kees, to his manager Anouk Peters and to HR" when a PIP is
     * approved. Dispatch itself is F07's job (not yet built); the part this feature owns is
     * the PIP_NOTICE template's seeded recipient flags, which this test asserts directly
     * against the seed data in V9__seed_remaining_email_templates.sql.
     */
    @Test
    @DisplayName("AC-03: getPipNoticeTemplate_hasEmployeeManagerAndHrRecipients")
    void getPipNoticeTemplate_hasEmployeeManagerAndHrRecipients() throws Exception {
        perform(get("/api/settings/email-templates/PIP_NOTICE").with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toEmployee").value(true))
                .andExpect(jsonPath("$.toManager").value(true))
                .andExpect(jsonPath("$.toHr").value(true));
    }

    /**
     * AC-15 (Gate 5 spec-coverage finding — partial): "Bench check"'s rule name and module are
     * read-only (ADM-01). The frontend already renders them as plain text, never an input; this
     * test proves the server-side half — even a client that sends {@code label} in the PUT body
     * cannot change it, because {@code TimerUpdateRequest} has no such field for Jackson to bind.
     */
    @Test
    @DisplayName("AC-15: updateTimer_givenLabelInRequestBody_labelIsIgnored")
    void updateTimer_givenLabelInRequestBody_labelIsIgnored() throws Exception {
        final String labelBefore = jdbc.queryForObject(
                "SELECT label FROM hr.setting WHERE code = 'BENCH_CHECK'", String.class);

        perform(withJson(put("/api/settings/timers/BENCH_CHECK"),
                """
                {"valueInt":7,"label":"Hacked label","module":"Hacked module","code":"HACKED","version":%d}
                """.formatted(currentTimerVersion("BENCH_CHECK"))).with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value(labelBefore))
                .andExpect(jsonPath("$.code").value("BENCH_CHECK"));

        final String labelAfter = jdbc.queryForObject(
                "SELECT label FROM hr.setting WHERE code = 'BENCH_CHECK'", String.class);
        org.assertj.core.api.Assertions.assertThat(labelAfter).isEqualTo(labelBefore);
    }
}
