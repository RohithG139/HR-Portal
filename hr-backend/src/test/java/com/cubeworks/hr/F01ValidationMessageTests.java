package com.cubeworks.hr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests asserting the <em>exact</em> validation message text defined in
 * F01 spec §5 (R-SRC-3: messages are copied exactly from the FRD or spec).
 *
 * <p>Each test sends a deliberately invalid request and verifies that the response body
 * contains the exact string.  A test fails if the message is missing, misspelled or
 * has wrong punctuation.</p>
 *
 * <h2>Coverage map (spec §5 → test)</h2>
 * <pre>
 * S2 personal details
 *   "This field is required."                          → msg_required_firstName
 *   "Enter a valid e-mail address."                    → msg_invalidEmail
 *   "Enter a valid phone number, e.g. +31 6 12345678." → msg_invalidPhone
 *   "Enter a valid postcode, e.g. 3511 AV."            → msg_invalidPostcode
 *   "The employee must be at least 16 years old."      → msg_ageBelow16
 *   "An employee with this e-mail address already exists." → msg_duplicateEmail (409)
 *
 * S2 contract
 *   "This field is required."  (endDate when FIXED_TERM) → msg_endDateRequired
 *   "The end date must be after the start date."          → msg_endDateBeforeStart
 *   "Hours must be between 1 and 40."                     → msg_invalidHours
 *
 * S2 verification
 *   "Confirm that the ID was checked and the contract is signed." → msg_verificationBothUnchecked
 *
 * S3 IT request
 *   "Complete the equipment request before sending it to IT." → msg_incompleteItRequest (400)
 *   "The ready-by date must be on or before the start date."  → msg_readyByAfterStart
 *
 * S4 checklist add-item modal
 *   "This field is required."  (item name blank)       → msg_checklistItemNameRequired
 *
 * S4 complete onboarding
 *   409 MANDATORY_ITEMS_OPEN + message "…"             → covered in F01OnboardingTests AC-15
 *
 * S5 equipment (IT admin add-asset modal)
 *   "This field is required."  (model blank)           → msg_assetModelRequired
 *   "This asset number is already in use."             → covered in F01OnboardingTests AC-22
 *
 * S8 policy pop-up
 *   "Confirm that you have read the policy."           → covered in F01OnboardingTests AC-14
 *
 * S10 My Profile
 *   "Enter a valid phone number, e.g. +31 6 12345678." → msg_profile_invalidPhone
 *   "Enter a valid postcode, e.g. 3511 AV."            → msg_profile_invalidPostcode
 *   "Add an emergency contact name and phone number."  → msg_profile_emergencyIncomplete
 * </pre>
 */
class F01ValidationMessageTests extends AbstractApiTest {

    // ── S2: Personal details ─────────────────────────────────────────────────

    /**
     * "This field is required." is returned for {@code firstName} when it is blank.
     */
    @Test
    @DisplayName("§5 S2: blank firstName → 400 'This field is required.'")
    void msg_required_firstName() throws Exception {
        String body = buildOnboardingBodyWithOverrides(
                "\"firstName\": \"\"", "\"lastName\": \"Willems\"");

        mockMvc.perform(withJson(post("/api/onboardings").with(asHR()), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='firstName')].message")
                        .value(org.hamcrest.Matchers.hasItem("This field is required.")));
    }

    /**
     * "Enter a valid e-mail address." when {@code privateEmail} is malformed.
     */
    @Test
    @DisplayName("§5 S2: malformed privateEmail → 400 'Enter a valid e-mail address.'")
    void msg_invalidEmail() throws Exception {
        String body = buildOnboardingBodyWithOverrides(
                "\"privateEmail\": \"not-an-email\"");

        mockMvc.perform(withJson(post("/api/onboardings").with(asHR()), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='privateEmail')].message")
                        .value(org.hamcrest.Matchers.hasItem("Enter a valid e-mail address.")));
    }

    /**
     * "Enter a valid phone number, e.g. +31 6 12345678." when {@code mobilePhone}
     * does not match the expected pattern.
     */
    @Test
    @DisplayName("§5 S2: invalid mobilePhone → 400 'Enter a valid phone number, e.g. +31 6 12345678.'")
    void msg_invalidPhone() throws Exception {
        String body = buildOnboardingBodyWithOverrides(
                "\"mobilePhone\": \"not-a-phone\"");

        mockMvc.perform(withJson(post("/api/onboardings").with(asHR()), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='mobilePhone')].message")
                        .value(org.hamcrest.Matchers.hasItem(
                                "Enter a valid phone number, e.g. +31 6 12345678.")));
    }

    /**
     * "Enter a valid postcode, e.g. 3511 AV." when {@code postcode} has a wrong format.
     */
    @Test
    @DisplayName("§5 S2: invalid postcode → 400 'Enter a valid postcode, e.g. 3511 AV.'")
    void msg_invalidPostcode() throws Exception {
        String body = buildOnboardingBodyWithOverrides(
                "\"postcode\": \"INVALID\"");

        mockMvc.perform(withJson(post("/api/onboardings").with(asHR()), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='postcode')].message")
                        .value(org.hamcrest.Matchers.hasItem(
                                "Enter a valid postcode, e.g. 3511 AV.")));
    }

    /**
     * "The employee must be at least 16 years old." when {@code dateOfBirth} is too recent.
     */
    @Test
    @DisplayName("§5 S2: DOB yields age < 16 → 400 'The employee must be at least 16 years old.'")
    void msg_ageBelow16() throws Exception {
        // Use a date of birth only 10 years ago (well under 16)
        String body = buildOnboardingBodyWithOverrides(
                "\"dateOfBirth\": \"2016-01-01\"");

        mockMvc.perform(withJson(post("/api/onboardings").with(asHR()), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='dateOfBirth')].message")
                        .value(org.hamcrest.Matchers.hasItem(
                                "The employee must be at least 16 years old.")));
    }

    /**
     * "An employee with this e-mail address already exists." returned in a 409
     * when the private e-mail duplicates an existing employee.
     */
    @Test
    @DisplayName("§5 S2: duplicate privateEmail → 409 'An employee with this e-mail address already exists.'")
    void msg_duplicateEmail() throws Exception {
        // Seed an existing employee
        long id = nextId();
        String email = "dup.v." + id + "@outlook.com";
        jdbc.update("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role
                ) VALUES (
                    ?, 'Dup', 'Validation', ?,
                    '+31 6 22222222', '1990-01-01', 'Dup St 1', '3511 AV', 'Utrecht',
                    ?, ?, 'JUNIOR', 'EMPLOYEE'
                )
                """,
                "E-" + id, email, developerJobTitleId, developmentDeptId);

        String body = buildOnboardingBodyWithOverrides(
                "\"privateEmail\": \"" + email + "\"");

        mockMvc.perform(withJson(post("/api/onboardings").with(asHR()), body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PRIVATE_EMAIL"))
                .andExpect(jsonPath("$.message")
                        .value("An employee with this e-mail address already exists."));
    }

    // ── S2: Contract ──────────────────────────────────────────────────────────

    /**
     * "This field is required." for {@code endDate} when contract type is FIXED_TERM
     * and no end date is provided.
     */
    @Test
    @DisplayName("§5 S2 contract: FIXED_TERM without endDate → 400 'This field is required.'")
    void msg_endDateRequired() throws Exception {
        String body = buildFixedTermBodyWithoutEndDate();

        mockMvc.perform(withJson(post("/api/onboardings").with(asHR()), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='endDate')].message")
                        .value(org.hamcrest.Matchers.hasItem("This field is required.")));
    }

    /**
     * "The end date must be after the start date." when {@code endDate} is before
     * (or equal to) {@code startDate} for a FIXED_TERM contract.
     */
    @Test
    @DisplayName("§5 S2 contract: endDate before startDate → 400 'The end date must be after the start date.'")
    void msg_endDateBeforeStart() throws Exception {
        String body = buildOnboardingBodyWithOverrides(
                "\"contractType\": \"FIXED_TERM\"",
                "\"startDate\": \"2027-06-01\"",
                "\"endDate\": \"2027-05-01\"");

        mockMvc.perform(withJson(post("/api/onboardings").with(asHR()), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='endDate')].message")
                        .value(org.hamcrest.Matchers.hasItem(
                                "The end date must be after the start date.")));
    }

    /**
     * "Hours must be between 1 and 40." when {@code hoursPerWeek} is 0.
     */
    @Test
    @DisplayName("§5 S2 contract: hoursPerWeek=0 → 400 'Hours must be between 1 and 40.'")
    void msg_invalidHours() throws Exception {
        String body = buildOnboardingBodyWithOverrides(
                "\"hoursPerWeek\": 0");

        mockMvc.perform(withJson(post("/api/onboardings").with(asHR()), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='hoursPerWeek')].message")
                        .value(org.hamcrest.Matchers.hasItem("Hours must be between 1 and 40.")));
    }

    /**
     * "Hours must be between 1 and 40." when {@code hoursPerWeek} is 41.
     */
    @Test
    @DisplayName("§5 S2 contract: hoursPerWeek=41 → 400 'Hours must be between 1 and 40.'")
    void msg_invalidHours_tooMany() throws Exception {
        String body = buildOnboardingBodyWithOverrides(
                "\"hoursPerWeek\": 41");

        mockMvc.perform(withJson(post("/api/onboardings").with(asHR()), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='hoursPerWeek')].message")
                        .value(org.hamcrest.Matchers.hasItem("Hours must be between 1 and 40.")));
    }

    // ── S2: Verification ──────────────────────────────────────────────────────

    /**
     * "Confirm that the ID was checked and the contract is signed."
     * when both {@code idChecked} and {@code contractSignedReceived} are false on Save &amp; continue.
     */
    @Test
    @DisplayName("§5 S2 verification: both boxes unchecked → 400 'Confirm that the ID was checked and the contract is signed.'")
    void msg_verificationBothUnchecked() throws Exception {
        String body = buildOnboardingBodyWithOverrides(
                "\"idChecked\": false",
                "\"contractSignedReceived\": false");

        mockMvc.perform(withJson(post("/api/onboardings").with(asHR()), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='verification')].message")
                        .value(org.hamcrest.Matchers.hasItem(
                                "Confirm that the ID was checked and the contract is signed.")));
    }

    // ── S3: IT request ────────────────────────────────────────────────────────

    /**
     * "Complete the equipment request before sending it to IT."
     * when the IT request is sent without selecting a laptop, ready-by date, or delivery method.
     */
    @Test
    @DisplayName("§5 S3: send IT request without laptop/readyBy/delivery → 400 'Complete the equipment request before sending it to IT.'")
    void msg_incompleteItRequest() throws Exception {
        // First create an employee to have an onboarding
        String email = "itreq." + nextId() + "@outlook.com";
        String resp = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long empId = Long.parseLong(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(resp).get("employeeId").asText());

        // Send without the required fields
        mockMvc.perform(withJson(
                        post("/api/onboardings/" + empId + "/it-request/send")
                                .with(asHR()),
                        "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Complete the equipment request before sending it to IT."));
    }

    /**
     * "The ready-by date must be on or before the start date."
     * when {@code readyBy} is after the employee's start date.
     */
    @Test
    @DisplayName("§5 S3: readyBy after startDate → 400 'The ready-by date must be on or before the start date.'")
    void msg_readyByAfterStart() throws Exception {
        String email = "rdby." + nextId() + "@outlook.com";

        // Create employee with startDate 2027-01-05
        String resp = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long empId = Long.parseLong(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(resp).get("employeeId").asText());

        // readyBy is 2027-01-10, which is AFTER the start date 2027-01-05
        String body = """
                {
                  "laptopModel": "MacBook Pro 14\\"",
                  "readyBy": "2027-01-10",
                  "deliveryMethod": "OFFICE",
                  "accessories": [],
                  "accounts": []
                }
                """;

        mockMvc.perform(withJson(
                        post("/api/onboardings/" + empId + "/it-request/send").with(asHR()),
                        body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='readyBy')].message")
                        .value(org.hamcrest.Matchers.hasItem(
                                "The ready-by date must be on or before the start date.")));
    }

    // ── S4: Checklist add-item modal ──────────────────────────────────────────

    /**
     * "This field is required." when the checklist item {@code name} is blank.
     */
    @Test
    @DisplayName("§5 S4: blank checklist item name → 400 'This field is required.'")
    void msg_checklistItemNameRequired() throws Exception {
        String email = "ci." + nextId() + "@outlook.com";
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
                  "name": "",
                  "section": "DOCUMENTS_ADMIN",
                  "ownerRole": "HR",
                  "dueOffsetDays": 0,
                  "mandatory": true
                }
                """;

        mockMvc.perform(withJson(
                        post("/api/onboardings/" + empId + "/checklist-items").with(asHR()),
                        body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='name')].message")
                        .value(org.hamcrest.Matchers.hasItem("This field is required.")));
    }

    // ── S5: Equipment (IT admin add-asset modal) ──────────────────────────────

    /**
     * "This field is required." when the asset {@code model} is blank.
     */
    @Test
    @DisplayName("§5 S5: blank asset model → 400 'This field is required.'")
    void msg_assetModelRequired() throws Exception {
        String email = "am." + nextId() + "@outlook.com";
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
                  "model": "",
                  "assetNumber": "CW-BLANK-%d",
                  "issueDate": "2027-01-05"
                }
                """.formatted(nextId());

        mockMvc.perform(withJson(
                        post("/api/employees/" + empId + "/assets").with(asIT()),
                        body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='model')].message")
                        .value(org.hamcrest.Matchers.hasItem("This field is required.")));
    }

    // ── S10: My Profile ───────────────────────────────────────────────────────

    /**
     * "Enter a valid phone number, e.g. +31 6 12345678." for an invalid mobile phone
     * in {@code PUT /api/me/profile}.
     */
    @Test
    @DisplayName("§5 S10: invalid mobilePhone in PUT /api/me/profile → 400 exact message")
    void msg_profile_invalidPhone() throws Exception {
        long id = nextId();
        String sub = "sub-pf-" + id;
        jdbc.update("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, idp_subject,
                    manager_id
                ) VALUES (
                    ?, 'Profile', 'Test', ?,
                    '+31 6 12345678', '1995-01-01', 'Test St 1', '3511 AV', 'Utrecht',
                    ?, ?, 'JUNIOR', 'EMPLOYEE', ?,
                    ?
                )
                """,
                "E-" + id, "pf." + id + "@outlook.com",
                developerJobTitleId, developmentDeptId, sub, managerEmployeeId);

        String body = """
                {
                  "mobilePhone": "bad-phone",
                  "street": "New St 1",
                  "postcode": "3511 AV",
                  "city": "Utrecht"
                }
                """;

        mockMvc.perform(withJson(put("/api/me/profile").with(asUserWithSub(sub, "EMPLOYEE")), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='mobilePhone')].message")
                        .value(org.hamcrest.Matchers.hasItem(
                                "Enter a valid phone number, e.g. +31 6 12345678.")));
    }

    /**
     * "Enter a valid postcode, e.g. 3511 AV." for an invalid postcode in
     * {@code PUT /api/me/profile}.
     */
    @Test
    @DisplayName("§5 S10: invalid postcode in PUT /api/me/profile → 400 exact message")
    void msg_profile_invalidPostcode() throws Exception {
        long id = nextId();
        String sub = "sub-pc-" + id;
        jdbc.update("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, idp_subject,
                    manager_id
                ) VALUES (
                    ?, 'PostcodeTest', 'User', ?,
                    '+31 6 12345678', '1995-02-02', 'Test St 2', '3511 AV', 'Utrecht',
                    ?, ?, 'JUNIOR', 'EMPLOYEE', ?,
                    ?
                )
                """,
                "E-" + id, "pc." + id + "@outlook.com",
                developerJobTitleId, developmentDeptId, sub, managerEmployeeId);

        String body = """
                {
                  "mobilePhone": "+31 6 12345678",
                  "street": "Test St 2",
                  "postcode": "INVALID",
                  "city": "Utrecht"
                }
                """;

        mockMvc.perform(withJson(put("/api/me/profile").with(asUserWithSub(sub, "EMPLOYEE")), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='postcode')].message")
                        .value(org.hamcrest.Matchers.hasItem(
                                "Enter a valid postcode, e.g. 3511 AV.")));
    }

    /**
     * "Add an emergency contact name and phone number." when the emergency name
     * is provided but the phone is blank (or vice versa).
     */
    @Test
    @DisplayName("§5 S10: emergencyName without emergencyPhone → 400 'Add an emergency contact name and phone number.'")
    void msg_profile_emergencyIncomplete() throws Exception {
        long id = nextId();
        String sub = "sub-ec-" + id;
        jdbc.update("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, idp_subject,
                    manager_id
                ) VALUES (
                    ?, 'Emergency', 'Test', ?,
                    '+31 6 12345678', '1994-03-03', 'Emg St 3', '3511 AV', 'Utrecht',
                    ?, ?, 'JUNIOR', 'EMPLOYEE', ?,
                    ?
                )
                """,
                "E-" + id, "ec." + id + "@outlook.com",
                developerJobTitleId, developmentDeptId, sub, managerEmployeeId);

        // Provide emergency name but no phone number
        String body = """
                {
                  "mobilePhone": "+31 6 12345678",
                  "street": "Emg St 3",
                  "postcode": "3511 AV",
                  "city": "Utrecht",
                  "emergencyName": "John Emergency",
                  "emergencyPhone": ""
                }
                """;

        mockMvc.perform(withJson(put("/api/me/profile").with(asUserWithSub(sub, "EMPLOYEE")), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='emergencyPhone' || @.field=='emergencyContact')].message")
                        .value(org.hamcrest.Matchers.hasItem(
                                "Add an emergency contact name and phone number.")));
    }

    // ── Optimistic locking ────────────────────────────────────────────────────

    /**
     * A stale {@code version} on {@code PUT /api/onboardings/{id}/details} returns
     * {@code 409 STALE_VERSION} (R-DB-5).
     */
    @Test
    @DisplayName("§R-DB-5: stale version on PUT /api/onboardings/{id}/details → 409 STALE_VERSION")
    void optimisticLock_staleVersion_onDetails_returns409() throws Exception {
        String email = "ol1." + nextId() + "@outlook.com";
        String resp = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long empId = Long.parseLong(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(resp).get("employeeId").asText());

        // Use a deliberately stale version (0 is always stale if the record has been updated)
        String staleBody = """
                {
                  "firstName": "Updated",
                  "lastName": "Willems",
                  "version": -1
                }
                """;

        mockMvc.perform(withJson(
                        put("/api/onboardings/" + empId + "/details").with(asHR()),
                        staleBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    /**
     * A stale {@code version} on {@code PUT /api/me/profile} returns
     * {@code 409 STALE_VERSION} (R-DB-5).
     */
    @Test
    @DisplayName("§R-DB-5: stale version on PUT /api/me/profile → 409 STALE_VERSION")
    void optimisticLock_staleVersion_onProfile_returns409() throws Exception {
        long id = nextId();
        String sub = "sub-ol2-" + id;
        jdbc.update("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, idp_subject,
                    manager_id
                ) VALUES (
                    ?, 'Stale', 'User', ?,
                    '+31 6 12345678', '1995-04-04', 'Stale St 4', '3511 AV', 'Utrecht',
                    ?, ?, 'JUNIOR', 'EMPLOYEE', ?,
                    ?
                )
                """,
                "E-" + id, "ol2." + id + "@outlook.com",
                developerJobTitleId, developmentDeptId, sub, managerEmployeeId);

        String body = """
                {
                  "mobilePhone": "+31 6 12345678",
                  "street": "New St 1",
                  "postcode": "3511 AV",
                  "city": "Utrecht",
                  "version": -1
                }
                """;

        mockMvc.perform(withJson(
                        put("/api/me/profile").with(asUserWithSub(sub, "EMPLOYEE")), body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    /**
     * {@code POST /api/tasks/{taskId}/undo} after the undo window has expired
     * returns {@code 409 UNDO_EXPIRED}.
     */
    @Test
    @DisplayName("§R-API-6: POST /api/tasks/{id}/undo after window expiry → 409 UNDO_EXPIRED")
    void undoAfterWindow_returns409UndoExpired() throws Exception {
        // Seed an engine task whose undo window has already passed (pending_execute_at in the past)
        String email = "undo." + nextId() + "@outlook.com";
        String resp = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long empId = Long.parseLong(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(resp).get("employeeId").asText());

        // Insert a PENDING engine task whose execute_at is in the past
        long taskDbId = jdbc.queryForObject("""
                INSERT INTO hr.engine_task (
                    engine_task_id, employee_id, task_name, due_date, created_at,
                    pending_status, pending_output, pending_by, pending_execute_at
                ) VALUES (
                    'engine-undo-test-' || ?, ?, 'Onboarding checklist',
                    CURRENT_DATE, NOW(),
                    'PENDING', '{}', ?, NOW() - INTERVAL '1 minute'
                ) RETURNING id
                """, Long.class, empId, empId, hrEmployeeId);

        // Try to undo after the window
        mockMvc.perform(post("/api/tasks/" + taskDbId + "/undo")
                        .with(asHR())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UNDO_EXPIRED"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a valid {@code POST /api/onboardings} body and then replaces specific
     * JSON key-value pairs with the overrides provided.
     *
     * <p>Each override is a complete JSON key-value fragment such as
     * {@code "\"hoursPerWeek\": 0"}.  The replacement is a simple string replace:
     * the override key must already exist in the base body so it replaces the original
     * value.</p>
     */
    private String buildOnboardingBodyWithOverrides(final String... overrides) {
        // Base valid body — unique email per call
        String base = validOnboardingBody("valid." + nextId() + "@outlook.com", managerEmployeeId);
        for (String override : overrides) {
            String key = override.substring(0, override.indexOf(':'));
            // Replace the existing value for this key in the JSON
            String trimmedKey = key.trim(); // e.g. "\"firstName\""
            base = base.replaceAll(
                    trimmedKey + "\\s*:\\s*[^,}\\]]+",
                    override);
        }
        return base;
    }

    /** Builds a FIXED_TERM body with no endDate field at all. */
    private String buildFixedTermBodyWithoutEndDate() {
        return """
                {
                  "firstName": "Fixed",
                  "lastName": "Term",
                  "privateEmail": "ft.%d@outlook.com",
                  "mobilePhone": "+31 6 12345678",
                  "dateOfBirth": "2000-01-15",
                  "street": "FT St 1",
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
                """.formatted(nextId(), developerJobTitleId, developmentDeptId,
                managerEmployeeId, developmentTemplateId);
    }
}
