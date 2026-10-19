package com.cubeworks.hr;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for FRD-08 Settings validation messages.
 *
 * <h2>Coverage</h2>
 * <p>Validates that the exact error messages from spec §5 and §12 are returned by the API.
 * Each test triggers a single validation rule and asserts the exact wording.</p>
 *
 * <pre>
 * AC-09  Exact validation messages on missing/blank fields
 * AC-10  "This name already exists." for duplicate department/job-title/form names
 * AC-11  "Add at least one section with a question." when activating empty form
 * AC-13  "Choose at least one recipient." when all recipient flags are false
 * AC-14  "Enter valid e-mail addresses, separated by commas." for bad CC
 * AC-15  Timer validation: "Enter a whole number greater than 0."
 *         PASSWORD_MIN_LENGTH: "Enter a number between 8 and 64."
 *         KPI_CYCLE: "Choose a KPI cycle."
 * </pre>
 *
 * <h2>JSONPath pattern used here</h2>
 * <p>MockMvc's JsonPath evaluates {@code $.errors[?(@.field == 'x')].message} as a
 * <em>list</em> of matched message strings.  We therefore use {@code hasItem()} rather
 * than a direct string {@code value()} comparison (same pattern as
 * {@link F01ValidationMessageTests}).</p>
 */
class FRD08SettingsValidationTests extends AbstractApiTest {

    @BeforeAll
    void setUp() {
        super.setUpActors();
    }

    /**
     * Reads a setting's current {@code version} directly from the database (D-10: timer
     * updates are now optimistic-locked, so every PUT needs a correct version or it fails
     * with 409 STALE_VERSION before this file's own validation checks ever run).
     */
    private int currentTimerVersion(final String code) {
        return jdbc.queryForObject("SELECT version FROM hr.setting WHERE code = ?", Integer.class, code);
    }

    // ── AC-09: Required field messages ────────────────────────────────────────

    @Test
    @DisplayName("AC-09: createDepartment_givenBlankName_returns400WithExactMessage")
    void createDepartment_givenBlankName_returns400WithExactMessage() throws Exception {
        perform(withJson(post("/api/settings/departments"),
                """
                {"name":""}
                """).with(asHR()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message")
                        .value(hasItem("This field is required.")));
    }

    @Test
    @DisplayName("AC-09: createDepartment_givenMissingName_returns400")
    void createDepartment_givenMissingName_returns400() throws Exception {
        perform(withJson(post("/api/settings/departments"),
                """
                {}
                """).with(asHR()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message")
                        .value(hasItem("This field is required.")));
    }

    @Test
    @DisplayName("AC-09: createJobTitle_givenBlankName_returns400WithExactMessage")
    void createJobTitle_givenBlankName_returns400WithExactMessage() throws Exception {
        perform(withJson(post("/api/settings/job-titles"),
                """
                {"name":"  ","grade":"MEDIOR","departmentId":1}
                """).with(asHR()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message")
                        .value(hasItem("This field is required.")));
    }

    @Test
    @DisplayName("AC-09: createAppraisalForm_givenMissingUsedFor_returns400")
    void createAppraisalForm_givenMissingUsedFor_returns400() throws Exception {
        perform(withJson(post("/api/settings/appraisal-forms"),
                """
                {"name":"Missing UsedFor Form"}
                """).with(asHR()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'usedFor')].message")
                        .value(hasItem("This field is required.")));
    }

    @Test
    @DisplayName("AC-09: createChecklistTemplate_givenMissingKind_returns400")
    void createChecklistTemplate_givenMissingKind_returns400() throws Exception {
        perform(withJson(post("/api/settings/checklist-templates"),
                """
                {"name":"No Kind Template"}
                """).with(asHR()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'kind')].message")
                        .value(hasItem("This field is required.")));
    }

    // ── AC-10: Duplicate name messages ────────────────────────────────────────

    @Test
    @DisplayName("AC-10: createDepartment_givenExistingName_returns409DuplicateName")
    void createDepartment_givenExistingName_returns409DuplicateName() throws Exception {
        perform(withJson(post("/api/settings/departments"),
                """
                {"name":"Development"}
                """).with(asHR()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"))
                .andExpect(jsonPath("$.message").value("This name already exists."));
    }

    @Test
    @DisplayName("AC-09: createJobTitle_givenNoGradeOrDepartment_returns400WithExactMessages")
    void createJobTitle_givenNoGradeOrDepartment_returns400WithExactMessages() throws Exception {
        perform(withJson(post("/api/settings/job-titles"),
                """
                {"name":"Gradeless Title"}
                """).with(asHR()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'grade')].message")
                        .value(hasItem("This field is required.")))
                .andExpect(jsonPath("$.errors[?(@.field == 'departmentId')].message")
                        .value(hasItem("This field is required.")));
    }

    @Test
    @DisplayName("createJobTitle_givenUnknownDepartment_returns404")
    void createJobTitle_givenUnknownDepartment_returns404() throws Exception {
        perform(withJson(post("/api/settings/job-titles"),
                """
                {"name":"Orphan Title","grade":"JUNIOR","departmentId":999999}
                """).with(asHR()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("AC-10: createJobTitle_givenExistingName_returns409DuplicateName")
    void createJobTitle_givenExistingName_returns409DuplicateName() throws Exception {
        perform(withJson(post("/api/settings/job-titles"),
                """
                {"name":"Developer","grade":"MEDIOR","departmentId":1}
                """).with(asHR()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"))
                .andExpect(jsonPath("$.message").value("This name already exists."));
    }

    @Test
    @DisplayName("AC-10: createAppraisalForm_givenExistingName_returns409DuplicateName")
    void createAppraisalForm_givenExistingName_returns409DuplicateName() throws Exception {
        perform(withJson(post("/api/settings/appraisal-forms"),
                """
                {"name":"Standard","usedFor":"STANDARD"}
                """).with(asHR()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"))
                .andExpect(jsonPath("$.message").value("This name already exists."));
    }

    // ── AC-11: Activate form without sections / questions ─────────────────────

    /**
     * AC-11: Setting status=ACTIVE on a form with no sections returns a field error
     * on "status" with the exact message "Add at least one section with a question."
     */
    @Test
    @DisplayName("AC-11: activateForm_givenNoSections_returns400WithExactMessage")
    void activateForm_givenNoSections_returns400WithExactMessage() throws Exception {
        // Create a fresh DRAFT form with no sections
        final long suffix = nextId();
        final String createBody = """
                {"name":"Empty Form %d","usedFor":"STANDARD"}
                """.formatted(suffix);

        final String created = perform(withJson(post("/api/settings/appraisal-forms"), createBody)
                .with(asHR()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        final long formId = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        // Fetch the form to get its current version
        final String detail = perform(get("/api/settings/appraisal-forms/" + formId)
                .with(asHR()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        final int version = Integer.parseInt(detail.replaceAll(".*\"version\":(\\d+).*", "$1"));

        // Attempt to activate — must be rejected
        perform(withJson(put("/api/settings/appraisal-forms/" + formId),
                """
                {
                  "name":"Empty Form %d",
                  "usedFor":"STANDARD",
                  "status":"ACTIVE",
                  "overallScoreMethod":"WEIGHTED_AVERAGE",
                  "label1":"1","label2":"2","label3":"3","label4":"4","label5":"5",
                  "version":%d
                }
                """.formatted(suffix, version)).with(asHR()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'status')].message")
                        .value(hasItem("Add at least one section with a question.")));
    }

    /**
     * AC-11 (extended): Activating a form where a section exists but has no questions
     * also returns "Add at least one section with a question."
     */
    @Test
    @DisplayName("AC-11: activateForm_givenSectionWithNoQuestions_returns400WithExactMessage")
    void activateForm_givenSectionWithNoQuestions_returns400WithExactMessage() throws Exception {
        final long suffix = nextId();

        // Create form
        final String created = perform(withJson(post("/api/settings/appraisal-forms"),
                """
                {"name":"Section No Q %d","usedFor":"STANDARD"}
                """.formatted(suffix)).with(asHR()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        final long formId = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        // Add a section (no questions)
        perform(withJson(post("/api/settings/appraisal-forms/" + formId + "/sections"),
                """
                {"name":"Empty Section","bySelf":true,"byPeer":false,"byManager":true,"byHr":false}
                """).with(asHR()))
                .andExpect(status().isCreated());

        // Fetch version
        final String detail = perform(get("/api/settings/appraisal-forms/" + formId)
                .with(asHR()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        final int version = Integer.parseInt(detail.replaceAll(".*\"version\":(\\d+).*", "$1"));

        // Attempt to activate
        perform(withJson(put("/api/settings/appraisal-forms/" + formId),
                """
                {
                  "name":"Section No Q %d",
                  "usedFor":"STANDARD",
                  "status":"ACTIVE",
                  "overallScoreMethod":"WEIGHTED_AVERAGE",
                  "label1":"1","label2":"2","label3":"3","label4":"4","label5":"5",
                  "version":%d
                }
                """.formatted(suffix, version)).with(asHR()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'status')].message")
                        .value(hasItem("Add at least one section with a question.")));
    }

    // ── AC-13: No recipient selected ─────────────────────────────────────────

    /**
     * AC-13: Updating an e-mail template with all recipient flags false returns
     * "Choose at least one recipient."
     */
    @Test
    @DisplayName("AC-13: updateEmailTemplate_givenNoRecipients_returns400WithExactMessage")
    void updateEmailTemplate_givenNoRecipients_returns400WithExactMessage() throws Exception {
        // First fetch the current template to get the version
        final String detail = perform(get("/api/settings/email-templates/WELCOME_NEW_EMPLOYEE")
                .with(asHR()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        final int version = Integer.parseInt(detail.replaceAll(".*\"version\":(\\d+).*", "$1"));

        perform(withJson(put("/api/settings/email-templates/WELCOME_NEW_EMPLOYEE"),
                """
                {
                  "name":"Welcome",
                  "triggerDescription":"When a new employee is onboarded",
                  "subject":"Welcome!",
                  "body":"Hello {{firstName}}!",
                  "cc":"",
                  "toEmployee":false,
                  "toManager":false,
                  "toHr":false,
                  "toItAdmin":false,
                  "toTaskOwner":false,
                  "toPeer":false,
                  "active":true,
                  "version":%d
                }
                """.formatted(version)).with(asHR()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'toEmployee')].message")
                        .value(hasItem("Choose at least one recipient.")));
    }

    // ── AC-14: Invalid CC e-mail addresses ───────────────────────────────────

    /**
     * AC-14: Updating an e-mail template with malformed CC addresses returns
     * "Enter valid e-mail addresses, separated by commas."
     */
    @Test
    @DisplayName("AC-14: updateEmailTemplate_givenBadCc_returns400WithExactMessage")
    void updateEmailTemplate_givenBadCc_returns400WithExactMessage() throws Exception {
        final String detail = perform(get("/api/settings/email-templates/WELCOME_NEW_EMPLOYEE")
                .with(asHR()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        final int version = Integer.parseInt(detail.replaceAll(".*\"version\":(\\d+).*", "$1"));

        perform(withJson(put("/api/settings/email-templates/WELCOME_NEW_EMPLOYEE"),
                """
                {
                  "name":"Welcome",
                  "triggerDescription":"When a new employee is onboarded",
                  "subject":"Welcome!",
                  "body":"Hello!",
                  "cc":"not-an-email, also bad@",
                  "toEmployee":true,
                  "toManager":false,
                  "toHr":false,
                  "toItAdmin":false,
                  "toTaskOwner":false,
                  "toPeer":false,
                  "active":true,
                  "version":%d
                }
                """.formatted(version)).with(asHR()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'cc')].message")
                        .value(hasItem("Enter valid e-mail addresses, separated by commas.")));
    }

    // ── AC-15: Timer validation messages ─────────────────────────────────────

    /**
     * AC-15a: Updating a regular timer with valueInt = 0 returns
     * "Enter a whole number greater than 0."
     */
    @Test
    @DisplayName("AC-15: updateTimer_givenZeroValue_returns400WithExactMessage")
    void updateTimer_givenZeroValue_returns400WithExactMessage() throws Exception {
        perform(withJson(put("/api/settings/timers/BENCH_CHECK"),
                """
                {"valueInt":0,"version":%d}
                """.formatted(currentTimerVersion("BENCH_CHECK"))).with(asHR()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'valueInt')].message")
                        .value(hasItem("Enter a whole number greater than 0.")));
    }

    /**
     * AC-15a (negative): Updating a regular timer with valueInt = -1 also returns
     * "Enter a whole number greater than 0."
     */
    @Test
    @DisplayName("AC-15: updateTimer_givenNegativeValue_returns400WithExactMessage")
    void updateTimer_givenNegativeValue_returns400WithExactMessage() throws Exception {
        perform(withJson(put("/api/settings/timers/BENCH_CHECK"),
                """
                {"valueInt":-1,"version":%d}
                """.formatted(currentTimerVersion("BENCH_CHECK"))).with(asHR()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'valueInt')].message")
                        .value(hasItem("Enter a whole number greater than 0.")));
    }

    /**
     * AC-15b: Updating PASSWORD_MIN_LENGTH with a value below 8 returns
     * "Enter a number between 8 and 64."
     */
    @Test
    @DisplayName("AC-15: updatePasswordMinLength_givenValueBelow8_returns400WithExactMessage")
    void updatePasswordMinLength_givenValueBelow8_returns400WithExactMessage() throws Exception {
        perform(withJson(put("/api/settings/timers/PASSWORD_MIN_LENGTH"),
                """
                {"valueInt":4,"version":%d}
                """.formatted(currentTimerVersion("PASSWORD_MIN_LENGTH"))).with(asHR()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'valueInt')].message")
                        .value(hasItem("Enter a number between 8 and 64.")));
    }

    /**
     * AC-15b: Updating PASSWORD_MIN_LENGTH with a value above 64 returns
     * "Enter a number between 8 and 64."
     */
    @Test
    @DisplayName("AC-15: updatePasswordMinLength_givenValueAbove64_returns400WithExactMessage")
    void updatePasswordMinLength_givenValueAbove64_returns400WithExactMessage() throws Exception {
        perform(withJson(put("/api/settings/timers/PASSWORD_MIN_LENGTH"),
                """
                {"valueInt":100,"version":%d}
                """.formatted(currentTimerVersion("PASSWORD_MIN_LENGTH"))).with(asHR()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'valueInt')].message")
                        .value(hasItem("Enter a number between 8 and 64.")));
    }

    /**
     * AC-15c: Updating KPI_CYCLE with an invalid enum string returns
     * "Choose a KPI cycle."
     */
    @Test
    @DisplayName("AC-15: updateKpiCycle_givenInvalidValue_returns400WithExactMessage")
    void updateKpiCycle_givenInvalidValue_returns400WithExactMessage() throws Exception {
        perform(withJson(put("/api/settings/timers/KPI_CYCLE"),
                """
                {"valueText":"MONTHLY","version":%d}
                """.formatted(currentTimerVersion("KPI_CYCLE"))).with(asHR()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'valueText')].message")
                        .value(hasItem("Choose a KPI cycle.")));
    }

    // ── Section completedBy validation ────────────────────────────────────────

    /**
     * Adding a section with all reviewer flags false returns "Choose at least one reviewer."
     * (spec §5 — section completedBy validation).
     */
    @Test
    @DisplayName("addSection_givenNoReviewers_returns400WithExactMessage")
    void addSection_givenNoReviewers_returns400WithExactMessage() throws Exception {
        // Create a form to add the section to
        final long suffix = nextId();
        final String created = perform(withJson(post("/api/settings/appraisal-forms"),
                """
                {"name":"No Reviewer Form %d","usedFor":"STANDARD"}
                """.formatted(suffix)).with(asHR()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        final long formId = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));

        perform(withJson(post("/api/settings/appraisal-forms/" + formId + "/sections"),
                """
                {"name":"No Reviewer","bySelf":false,"byPeer":false,"byManager":false,"byHr":false}
                """).with(asHR()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'completedBy')].message")
                        .value(hasItem("Choose at least one reviewer.")));
    }

    // ── Not found checks ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getChecklistTemplate_givenUnknownId_returns404")
    void getChecklistTemplate_givenUnknownId_returns404() throws Exception {
        perform(get("/api/settings/checklist-templates/999999").with(asHR()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("getAppraisalForm_givenUnknownId_returns404")
    void getAppraisalForm_givenUnknownId_returns404() throws Exception {
        perform(get("/api/settings/appraisal-forms/999999").with(asHR()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("getEmailTemplate_givenUnknownCode_returns404")
    void getEmailTemplate_givenUnknownCode_returns404() throws Exception {
        perform(get("/api/settings/email-templates/NO_SUCH_CODE").with(asHR()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("updateTimer_givenUnknownCode_returns404")
    void updateTimer_givenUnknownCode_returns404() throws Exception {
        perform(withJson(put("/api/settings/timers/NO_SUCH_CODE"),
                """
                {"valueInt":5}
                """).with(asHR()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
