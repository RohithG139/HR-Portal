package com.cubeworks.hr;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for FRD-08 Settings role access control — AC-17.
 *
 * <h2>AC-17 coverage</h2>
 * <p>Every settings endpoint requires the {@code HR} role. All other roles
 * (MANAGER, EMPLOYEE, IT_ADMIN) must receive {@code 403 FORBIDDEN}.</p>
 *
 * <p>We test a representative sample of endpoints: one GET and one mutating call per
 * resource group.  Because all controllers share the same class-level
 * {@code @PreAuthorize("hasRole('HR')")}, a failed check on one endpoint implies all
 * others in the same controller are equally protected.</p>
 */
class FRD08SettingsRoleAccessTests extends AbstractApiTest {

    @BeforeAll
    void setUp() {
        super.setUpActors();
    }

    // ── Departments — C01 / C02 / C03 ─────────────────────────────────────────

    @Test
    @DisplayName("AC-17: listDepartments_asManager_returns403")
    void listDepartments_asManager_returns403() throws Exception {
        perform(get("/api/settings/departments").with(asManager()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: listDepartments_asEmployee_returns403")
    void listDepartments_asEmployee_returns403() throws Exception {
        perform(get("/api/settings/departments").with(asEmployee()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: listDepartments_asIT_returns403")
    void listDepartments_asIT_returns403() throws Exception {
        perform(get("/api/settings/departments").with(asIT()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: createDepartment_asManager_returns403")
    void createDepartment_asManager_returns403() throws Exception {
        perform(withJson(post("/api/settings/departments"),
                """
                {"name":"Denied Dept"}
                """).with(asManager()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: createDepartment_asEmployee_returns403")
    void createDepartment_asEmployee_returns403() throws Exception {
        perform(withJson(post("/api/settings/departments"),
                """
                {"name":"Denied Dept"}
                """).with(asEmployee()))
                .andExpect(status().isForbidden());
    }

    // ── Job Titles — C04 / C05 / C06 ─────────────────────────────────────────

    @Test
    @DisplayName("AC-17: listJobTitles_asManager_returns403")
    void listJobTitles_asManager_returns403() throws Exception {
        perform(get("/api/settings/job-titles").with(asManager()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: createJobTitle_asEmployee_returns403")
    void createJobTitle_asEmployee_returns403() throws Exception {
        perform(withJson(post("/api/settings/job-titles"),
                """
                {"name":"Denied Title","grade":"MEDIOR","departmentId":1}
                """).with(asEmployee()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: createJobTitle_asIT_returns403")
    void createJobTitle_asIT_returns403() throws Exception {
        perform(withJson(post("/api/settings/job-titles"),
                """
                {"name":"Denied Title","grade":"MEDIOR","departmentId":1}
                """).with(asIT()))
                .andExpect(status().isForbidden());
    }

    // ── Checklist Templates — C07 / C09 ───────────────────────────────────────

    @Test
    @DisplayName("AC-17: listChecklistTemplates_asManager_returns403")
    void listChecklistTemplates_asManager_returns403() throws Exception {
        perform(get("/api/settings/checklist-templates").with(asManager()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: createChecklistTemplate_asEmployee_returns403")
    void createChecklistTemplate_asEmployee_returns403() throws Exception {
        perform(withJson(post("/api/settings/checklist-templates"),
                """
                {"name":"Denied Template","kind":"ONBOARDING"}
                """).with(asEmployee()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: archiveChecklistTemplate_asIT_returns403")
    void archiveChecklistTemplate_asIT_returns403() throws Exception {
        perform(post("/api/settings/checklist-templates/" + developmentTemplateId + "/archive")
                .with(asIT()))
                .andExpect(status().isForbidden());
    }

    // ── Appraisal Forms — C14 / C15 ───────────────────────────────────────────

    @Test
    @DisplayName("AC-17: listAppraisalForms_asManager_returns403")
    void listAppraisalForms_asManager_returns403() throws Exception {
        perform(get("/api/settings/appraisal-forms").with(asManager()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: createAppraisalForm_asEmployee_returns403")
    void createAppraisalForm_asEmployee_returns403() throws Exception {
        perform(withJson(post("/api/settings/appraisal-forms"),
                """
                {"name":"Denied Form","usedFor":"STANDARD"}
                """).with(asEmployee()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: createAppraisalForm_asIT_returns403")
    void createAppraisalForm_asIT_returns403() throws Exception {
        perform(withJson(post("/api/settings/appraisal-forms"),
                """
                {"name":"Denied Form","usedFor":"STANDARD"}
                """).with(asIT()))
                .andExpect(status().isForbidden());
    }

    // ── Email Templates — C25 / C27 ───────────────────────────────────────────

    @Test
    @DisplayName("AC-17: listEmailTemplates_asManager_returns403")
    void listEmailTemplates_asManager_returns403() throws Exception {
        perform(get("/api/settings/email-templates").with(asManager()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: getEmailTemplate_asEmployee_returns403")
    void getEmailTemplate_asEmployee_returns403() throws Exception {
        perform(get("/api/settings/email-templates/WELCOME_NEW_EMPLOYEE").with(asEmployee()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: testSendEmailTemplate_asIT_returns403")
    void testSendEmailTemplate_asIT_returns403() throws Exception {
        perform(post("/api/settings/email-templates/WELCOME_NEW_EMPLOYEE/test")
                .with(asIT()))
                .andExpect(status().isForbidden());
    }

    // ── Timers — C29 / C30 ────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-17: listTimers_asManager_returns403")
    void listTimers_asManager_returns403() throws Exception {
        perform(get("/api/settings/timers").with(asManager()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: updateTimer_asEmployee_returns403")
    void updateTimer_asEmployee_returns403() throws Exception {
        perform(withJson(put("/api/settings/timers/BENCH_CHECK"),
                """
                {"valueInt":5}
                """).with(asEmployee()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: updateTimer_asIT_returns403")
    void updateTimer_asIT_returns403() throws Exception {
        perform(withJson(put("/api/settings/timers/BENCH_CHECK"),
                """
                {"valueInt":5}
                """).with(asIT()))
                .andExpect(status().isForbidden());
    }
}
