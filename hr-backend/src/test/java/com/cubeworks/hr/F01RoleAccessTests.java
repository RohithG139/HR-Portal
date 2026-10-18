package com.cubeworks.hr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Role-based access tests for every endpoint listed in F01 spec §6.
 *
 * <p>For each endpoint the spec's "Role" column defines which roles are allowed.
 * Each test class in this file covers exactly two scenarios per endpoint:</p>
 * <ol>
 *   <li>The <em>allowed</em> role succeeds (2xx or the expected non-403 status).</li>
 *   <li>A <em>forbidden</em> role receives {@code 403 {"code":"FORBIDDEN"}}.</li>
 * </ol>
 *
 * <p>Additionally, G-13 and WS-02/03 ownership tests are included where the spec
 * implies them (section marked with "ownership" in the comment).</p>
 *
 * <h2>Endpoint coverage</h2>
 * <pre>
 * A01  GET  /api/me                         all roles allowed
 * A02  GET  /api/lookups/onboarding         HR only
 * A03  GET  /api/lookups/it                 HR, IT
 * A04  GET  /api/employees                  HR only
 * A08  POST /api/onboarding-drafts          HR only
 * A10  POST /api/onboardings                HR only
 * A11  GET  /api/onboardings/{id}           HR only
 * A12  PUT  /api/onboardings/{id}/details   HR only
 * A13  PUT  /api/onboardings/{id}/it-request  HR only
 * A14  POST /api/onboardings/{id}/it-request/send  HR only
 * A15  GET  /api/onboardings/{id}/checklist         HR and IT (own items)
 * A16  POST /api/onboardings/{id}/checklist-items   HR only
 * A17  POST /api/checklist-items/{id}/done           owner or HR
 * A18  DEL  /api/checklist-items/{id}/done           owner or HR
 * A19  POST /api/onboardings/{id}/complete            HR only
 * A20  GET  /api/it/equipment-requests/{id}          IT, HR
 * A21  POST /api/employees/{id}/assets               IT only
 * A22  POST /api/assets/{id}/unissue                 IT only
 * A23  POST /api/onboardings/{id}/it-request/complete  IT only
 * A24  POST /api/onboardings/{id}/it-setup/complete    IT only
 * A25  GET  /api/me/onboarding                       EMPLOYEE (own only)
 * A29  GET  /api/me/profile                          EMPLOYEE (own only)
 * A30  PUT  /api/me/profile                          EMPLOYEE (WS-03 ownership)
 * A31  GET  /api/employees/{id}                      HR; Manager (own team, WS-02)
 * A32  GET  /api/employees/{id}/profile              HR, Manager
 * A33  PUT  /api/employees/{id}/profile              HR only
 * A34  POST /api/employees/{id}/account/resend-invite  HR only
 * A35  GET  /api/employees/{id}/history              HR only
 * A36  GET  /api/tasks                               all roles
 * A37  POST /api/tasks/{id}/undo                     task owner only
 * A38  GET  /api/notifications                       all roles
 * </pre>
 */
class F01RoleAccessTests extends AbstractApiTest {

    // ─────────────────────────────────────────────────────────────────────────
    // A01: GET /api/me — all roles
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A01: GET /api/me as HR → 200")
    void getMe_asHR_succeeds() throws Exception {
        mockMvc.perform(get("/api/me").with(asHR()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A01: GET /api/me as EMPLOYEE → 200 (all roles allowed)")
    void getMe_asEmployee_succeeds() throws Exception {
        mockMvc.perform(get("/api/me").with(asEmployee()))
                .andExpect(status().isOk());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A02: GET /api/lookups/onboarding — HR only
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A02: GET /api/lookups/onboarding as HR → 200")
    void getOnboardingLookups_asHR_succeeds() throws Exception {
        mockMvc.perform(get("/api/lookups/onboarding").with(asHR()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A02: GET /api/lookups/onboarding as EMPLOYEE → 403")
    void getOnboardingLookups_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/lookups/onboarding").with(asEmployee()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("A02: GET /api/lookups/onboarding as MANAGER → 403")
    void getOnboardingLookups_asManager_returns403() throws Exception {
        mockMvc.perform(get("/api/lookups/onboarding").with(asManager()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A03: GET /api/lookups/it — HR and IT
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A03: GET /api/lookups/it as HR → 200")
    void getItLookups_asHR_succeeds() throws Exception {
        mockMvc.perform(get("/api/lookups/it").with(asHR()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A03: GET /api/lookups/it as IT admin → 200")
    void getItLookups_asIT_succeeds() throws Exception {
        mockMvc.perform(get("/api/lookups/it").with(asIT()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A03: GET /api/lookups/it as EMPLOYEE → 403")
    void getItLookups_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/lookups/it").with(asEmployee()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A04: GET /api/employees — HR only
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A04: GET /api/employees as HR → 200")
    void listEmployees_asHR_succeeds() throws Exception {
        mockMvc.perform(get("/api/employees").with(asHR()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A04: GET /api/employees as MANAGER → 403")
    void listEmployees_asManager_returns403() throws Exception {
        mockMvc.perform(get("/api/employees").with(asManager()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("A04: GET /api/employees as IT admin → 200 (D-W9: IT admin has reduced-field read access)")
    void listEmployees_asIT_returns200_withReducedFields() throws Exception {
        // D-W9 (FRD-01 Wave 3): IT admin is now allowed to call GET /api/employees with a
        // reduced field set (employeeCode, firstName, lastName, jobTitle, stage, assetCount).
        // The original A04 gate-2 spec restricted this endpoint to HR only; D-W9 extends it.
        mockMvc.perform(get("/api/employees").with(asIT()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A08: POST /api/onboarding-drafts — HR only
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A08: POST /api/onboarding-drafts as HR → 201")
    void createDraft_asHR_succeeds() throws Exception {
        mockMvc.perform(withJson(post("/api/onboarding-drafts").with(asHR()),
                        """
                        {"firstName": "Draft", "lastName": "Test", "privateEmail": "drft.%d@t.com"}
                        """.formatted(nextId())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("A08: POST /api/onboarding-drafts as EMPLOYEE → 403")
    void createDraft_asEmployee_returns403() throws Exception {
        mockMvc.perform(withJson(post("/api/onboarding-drafts").with(asEmployee()),
                        """
                        {"firstName": "Draft", "lastName": "Forbidden"}
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A10: POST /api/onboardings — HR only
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A10: POST /api/onboardings as HR → 201")
    void createOnboarding_asHR_succeeds() throws Exception {
        mockMvc.perform(withJson(post("/api/onboardings").with(asHR()),
                        validOnboardingBody("role.hr." + nextId() + "@t.com", managerEmployeeId)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("A10: POST /api/onboardings as EMPLOYEE → 403")
    void createOnboarding_asEmployee_returns403() throws Exception {
        mockMvc.perform(withJson(post("/api/onboardings").with(asEmployee()),
                        validOnboardingBody("role.emp." + nextId() + "@t.com", managerEmployeeId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("A10: POST /api/onboardings as MANAGER → 403")
    void createOnboarding_asManager_returns403() throws Exception {
        mockMvc.perform(withJson(post("/api/onboardings").with(asManager()),
                        validOnboardingBody("role.mg." + nextId() + "@t.com", managerEmployeeId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("A10: POST /api/onboardings as IT admin → 403")
    void createOnboarding_asIT_returns403() throws Exception {
        mockMvc.perform(withJson(post("/api/onboardings").with(asIT()),
                        validOnboardingBody("role.it." + nextId() + "@t.com", managerEmployeeId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A14: POST /api/onboardings/{id}/it-request/send — HR only
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A14: POST /it-request/send as EMPLOYEE → 403")
    void sendItRequest_asEmployee_returns403() throws Exception {
        long empId = createMinimalOnboarding("a14.emp." + nextId() + "@t.com");

        mockMvc.perform(withJson(
                        post("/api/onboardings/" + empId + "/it-request/send")
                                .with(asEmployee()),
                        "{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("A14: POST /it-request/send as IT admin → 403")
    void sendItRequest_asIT_returns403() throws Exception {
        long empId = createMinimalOnboarding("a14.it." + nextId() + "@t.com");

        mockMvc.perform(withJson(
                        post("/api/onboardings/" + empId + "/it-request/send")
                                .with(asIT()),
                        "{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A15: GET /api/onboardings/{id}/checklist — HR and IT (IT sees own items)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A15: GET /checklist as HR → 200")
    void getChecklist_asHR_succeeds() throws Exception {
        long empId = createMinimalOnboarding("a15.hr." + nextId() + "@t.com");

        mockMvc.perform(get("/api/onboardings/" + empId + "/checklist").with(asHR()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A15: GET /checklist as IT admin → 200")
    void getChecklist_asIT_succeeds() throws Exception {
        long empId = createMinimalOnboarding("a15.it." + nextId() + "@t.com");

        mockMvc.perform(get("/api/onboardings/" + empId + "/checklist").with(asIT()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A15: GET /checklist as EMPLOYEE → 403")
    void getChecklist_asEmployee_returns403() throws Exception {
        long empId = createMinimalOnboarding("a15.emp." + nextId() + "@t.com");

        mockMvc.perform(get("/api/onboardings/" + empId + "/checklist").with(asEmployee()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A17: POST /api/checklist-items/{id}/done — owner or HR
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A17: POST /done as HR (on any item) → 200")
    void tickItem_asHR_succeeds() throws Exception {
        long empId = createMinimalOnboarding("a17.hr." + nextId() + "@t.com");
        long itemId = getFirstOpenItem(empId, "HR");

        mockMvc.perform(post("/api/checklist-items/" + itemId + "/done")
                        .with(asHR())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A17: POST /done as MANAGER on HR-owned item → 403")
    void tickItem_asManager_onHRItem_returns403() throws Exception {
        long empId = createMinimalOnboarding("a17.mg." + nextId() + "@t.com");
        long itemId = getFirstOpenItem(empId, "HR");

        mockMvc.perform(post("/api/checklist-items/" + itemId + "/done")
                        .with(asManager())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A19: POST /api/onboardings/{id}/complete — HR only
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A19: POST /complete as EMPLOYEE → 403")
    void completeOnboarding_asEmployee_returns403() throws Exception {
        long empId = createMinimalOnboarding("a19.emp." + nextId() + "@t.com");

        mockMvc.perform(post("/api/onboardings/" + empId + "/complete")
                        .with(asEmployee())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("A19: POST /complete as MANAGER → 403")
    void completeOnboarding_asManager_returns403() throws Exception {
        long empId = createMinimalOnboarding("a19.mg." + nextId() + "@t.com");

        mockMvc.perform(post("/api/onboardings/" + empId + "/complete")
                        .with(asManager())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("A19: POST /complete as IT admin → 403")
    void completeOnboarding_asIT_returns403() throws Exception {
        long empId = createMinimalOnboarding("a19.it." + nextId() + "@t.com");

        mockMvc.perform(post("/api/onboardings/" + empId + "/complete")
                        .with(asIT())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A21: POST /api/employees/{id}/assets — IT only
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A21: POST /assets as IT admin → 201")
    void issueAsset_asIT_succeeds() throws Exception {
        long empId = createMinimalOnboarding("a21.it." + nextId() + "@t.com");
        String body = """
                {
                  "assetType": "LAPTOP",
                  "model": "ThinkPad",
                  "assetNumber": "CW-A21-%d",
                  "issueDate": "2027-01-05"
                }
                """.formatted(nextId());

        mockMvc.perform(withJson(post("/api/employees/" + empId + "/assets").with(asIT()), body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("A21: POST /assets as HR → 403")
    void issueAsset_asHR_returns403() throws Exception {
        long empId = createMinimalOnboarding("a21.hr." + nextId() + "@t.com");
        String body = """
                {
                  "assetType": "LAPTOP",
                  "model": "ThinkPad",
                  "assetNumber": "CW-A21HR-%d",
                  "issueDate": "2027-01-05"
                }
                """.formatted(nextId());

        mockMvc.perform(withJson(post("/api/employees/" + empId + "/assets").with(asHR()), body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("A21: POST /assets as EMPLOYEE → 403")
    void issueAsset_asEmployee_returns403() throws Exception {
        long empId = createMinimalOnboarding("a21.emp." + nextId() + "@t.com");
        String body = """
                {
                  "assetType": "LAPTOP",
                  "model": "ThinkPad",
                  "assetNumber": "CW-A21EM-%d",
                  "issueDate": "2027-01-05"
                }
                """.formatted(nextId());

        mockMvc.perform(withJson(post("/api/employees/" + empId + "/assets").with(asEmployee()), body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A23: POST /api/onboardings/{id}/it-request/complete — IT only
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A23: POST /it-request/complete as HR → 403")
    void completeItRequest_asHR_returns403() throws Exception {
        long empId = createMinimalOnboarding("a23.hr." + nextId() + "@t.com");

        mockMvc.perform(post("/api/onboardings/" + empId + "/it-request/complete")
                        .with(asHR())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("A23: POST /it-request/complete as EMPLOYEE → 403")
    void completeItRequest_asEmployee_returns403() throws Exception {
        long empId = createMinimalOnboarding("a23.emp." + nextId() + "@t.com");

        mockMvc.perform(post("/api/onboardings/" + empId + "/it-request/complete")
                        .with(asEmployee())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A25: GET /api/me/onboarding — EMPLOYEE (own only)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A25: GET /api/me/onboarding as EMPLOYEE → 200")
    void getMyOnboarding_asEmployee_succeeds() throws Exception {
        mockMvc.perform(get("/api/me/onboarding").with(asEmployee()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A25: GET /api/me/onboarding as HR → 403 (endpoint is employee-only)")
    void getMyOnboarding_asHR_returns403() throws Exception {
        mockMvc.perform(get("/api/me/onboarding").with(asHR()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A29/A30: GET + PUT /api/me/profile — every role, own record only (G-13)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A29: GET /api/me/profile as EMPLOYEE → 200")
    void getMyProfile_asEmployee_succeeds() throws Exception {
        mockMvc.perform(get("/api/me/profile").with(asEmployee()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A29 (G-13): GET /api/me/profile as HR → 200, returns HR's own record")
    void getMyProfile_asHR_succeeds() throws Exception {
        mockMvc.perform(get("/api/me/profile").with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(hrEmployeeId));
    }

    @Test
    @DisplayName("A29 (G-13): GET /api/me/profile as IT admin → 200, returns IT's own record")
    void getMyProfile_asIT_succeeds() throws Exception {
        mockMvc.perform(get("/api/me/profile").with(asIT()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itEmployeeId));
    }

    @Test
    @DisplayName("A29 (G-13): GET /api/me/profile as Manager → 200, returns Manager's own record")
    void getMyProfile_asManager_succeeds() throws Exception {
        mockMvc.perform(get("/api/me/profile").with(asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(managerEmployeeId));
    }

    @Test
    @DisplayName("A30 (G-13): PUT /api/me/profile as IT admin → 200, updates IT's own contact details")
    void updateMyProfile_asIT_succeeds() throws Exception {
        mockMvc.perform(withJson(put("/api/me/profile").with(asIT()),
                        """
                        {
                          "mobilePhone": "+31 6 99999999",
                          "street": "IT St 1",
                          "postcode": "3511 AV",
                          "city": "Utrecht"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itEmployeeId))
                .andExpect(jsonPath("$.mobilePhone").value("+31 6 99999999"));
    }

    @Test
    @DisplayName("A30 (WS-03): PUT /api/me/profile as HR ignores employment fields, even for HR's own record")
    void updateMyProfile_asHR_ignoresEmploymentFields() throws Exception {
        // UpdateMyProfileRequest has no firstName/appRole fields at all (WS-03), so sending them
        // in the raw JSON body must have no effect regardless of the caller's app role.
        mockMvc.perform(withJson(put("/api/me/profile").with(asHR()),
                        """
                        {
                          "firstName": "ShouldBeIgnored",
                          "appRole": "EMPLOYEE",
                          "mobilePhone": "+31 6 88888888",
                          "street": "HR St 1",
                          "postcode": "3511 AV",
                          "city": "Utrecht"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("HR"))
                .andExpect(jsonPath("$.appRole").value("HR"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A31: GET /api/employees/{id} — HR; Manager (own team only — WS-02)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A31: GET /api/employees/{id} as HR → 200")
    void getEmployee_asHR_succeeds() throws Exception {
        mockMvc.perform(get("/api/employees/" + baseEmployeeId).with(asHR()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A31: GET /api/employees/{id} as Manager (own team member) → 200")
    void getEmployee_asManager_ownTeam_succeeds() throws Exception {
        // baseEmployeeId has managerEmployeeId as their manager
        mockMvc.perform(get("/api/employees/" + baseEmployeeId).with(asManager()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A31: GET /api/employees/{id} as EMPLOYEE → 403 (not their own record in this endpoint)")
    void getEmployee_asEmployee_returns403() throws Exception {
        // The /api/employees/{id} endpoint is for HR and Manager only
        // Employees use /api/me/profile for their own data
        mockMvc.perform(get("/api/employees/" + hrEmployeeId).with(asEmployee()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A33: PUT /api/employees/{id}/profile — HR only
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A33: PUT /api/employees/{id}/profile as MANAGER → 403 (read-only for manager)")
    void updateEmployeeProfile_asManager_returns403() throws Exception {
        mockMvc.perform(withJson(
                        put("/api/employees/" + baseEmployeeId + "/profile").with(asManager()),
                        """
                        {
                          "firstName": "New",
                          "lastName": "Name",
                          "version": 0
                        }
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("A33: PUT /api/employees/{id}/profile as IT admin → 403")
    void updateEmployeeProfile_asIT_returns403() throws Exception {
        mockMvc.perform(withJson(
                        put("/api/employees/" + baseEmployeeId + "/profile").with(asIT()),
                        """
                        {
                          "firstName": "New",
                          "lastName": "Name",
                          "version": 0
                        }
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A34: POST /api/employees/{id}/account/resend-invite — HR only
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A34: POST /account/resend-invite as EMPLOYEE → 403")
    void resendInvite_asEmployee_returns403() throws Exception {
        mockMvc.perform(post("/api/employees/" + baseEmployeeId + "/account/resend-invite")
                        .with(asEmployee())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("A34: POST /account/resend-invite as MANAGER → 403")
    void resendInvite_asManager_returns403() throws Exception {
        mockMvc.perform(post("/api/employees/" + baseEmployeeId + "/account/resend-invite")
                        .with(asManager())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A36: GET /api/tasks — all roles
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A36: GET /api/tasks as EMPLOYEE → 200 (all roles allowed)")
    void getTasks_asEmployee_succeeds() throws Exception {
        mockMvc.perform(get("/api/tasks").with(asEmployee()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A36: GET /api/tasks as IT admin → 200 (all roles allowed)")
    void getTasks_asIT_succeeds() throws Exception {
        mockMvc.perform(get("/api/tasks").with(asIT()))
                .andExpect(status().isOk());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A38: GET /api/notifications — all roles
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A38: GET /api/notifications as EMPLOYEE → 200")
    void getNotifications_asEmployee_succeeds() throws Exception {
        mockMvc.perform(get("/api/notifications").with(asEmployee()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A38: GET /api/notifications as HR → 200")
    void getNotifications_asHR_succeeds() throws Exception {
        mockMvc.perform(get("/api/notifications").with(asHR()))
                .andExpect(status().isOk());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A37: POST /api/tasks/{id}/undo — task owner only (G-13 ownership)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A37: POST /tasks/{id}/undo by a different user → 403 (task ownership)")
    void undoTask_byNonOwner_returns403() throws Exception {
        // Create a pending engine task owned by the HR actor
        long empId = createMinimalOnboarding("a37.hr." + nextId() + "@t.com");
        long taskId = jdbc.queryForObject("""
                INSERT INTO hr.engine_task (
                    engine_task_id, employee_id, task_name, due_date, created_at,
                    pending_status, pending_output, pending_by, pending_execute_at
                ) VALUES (
                    'engine-a37-' || ?, ?, 'Onboarding checklist',
                    CURRENT_DATE, NOW(),
                    'PENDING', '{}', ?, NOW() + INTERVAL '30 seconds'
                ) RETURNING id
                """, Long.class, empId, empId, hrEmployeeId);

        // The EMPLOYEE actor tries to undo a task owned by HR — must be 403
        mockMvc.perform(post("/api/tasks/" + taskId + "/undo")
                        .with(asEmployee())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Unauthenticated access — no JWT at all
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Any protected endpoint without JWT → 401")
    void anyEndpoint_withoutJwt_returns401() throws Exception {
        // Spring Security returns 401 when no bearer token is present
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates an employee + onboarding via the API (as HR) and returns the employee id.
     * Used in role tests that need an existing onboarding to act on.
     */
    private long createMinimalOnboarding(final String email) throws Exception {
        String resp = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(resp).get("employeeId").asText());
    }

    /**
     * Returns the id of the first OPEN checklist item with the given owner role for the employee.
     */
    private long getFirstOpenItem(final long employeeId, final String ownerRole) {
        return jdbc.queryForObject(
                "SELECT id FROM hr.checklist_item WHERE employee_id = ? AND owner_role = ? AND status = 'OPEN' LIMIT 1",
                Long.class, employeeId, ownerRole);
    }
}
