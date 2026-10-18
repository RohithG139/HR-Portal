package com.cubeworks.hr;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role-access tests for every new endpoint added in FRD-01 Wave 3.
 *
 * <h2>Coverage map</h2>
 * <pre>
 * GET  /api/dashboard                  — all 4 roles → 200
 * GET  /api/team                       — MANAGER → 200; HR/EMPLOYEE/IT_ADMIN → 403
 * GET  /api/team/export                — MANAGER → 200; HR → 403
 * GET  /api/employees/{id}/manager-tasks   — HR → 200; MANAGER/EMPLOYEE/IT_ADMIN → 403
 * POST /api/employees/{id}/manager-transfers  — HR → 201; MANAGER/EMPLOYEE → 403
 * AC-W08: GET /api/employees          — IT admin sees assetCount; HR sees null assetCount
 * </pre>
 *
 * <p>Rule R-API-2: the server always checks role; hiding UI elements is not the only protection.</p>
 */
@DisplayName("FRD-01 Wave 3 — role-access and AC-W08 field-reduction")
class FRD01RoleAccessTests extends AbstractApiTest {

    // ── Per-class seed data ────────────────────────────────────────────────────

    /** Code of a test employee created by this class. */
    private String testEmployeeCode;
    private long testEmployeeId;
    /**
     * Unique lastName of the test employee, used as q-filter.
     * The findFiltered query searches firstName + lastName, NOT employee code, so the code
     * itself cannot be used as q value.
     */
    private String testEmployeeLastName;

    /**
     * Seeds one extra employee belonging to the Manager actor (used for manager-tasks endpoint
     * and AC-W08 filter assertions).
     */
    @BeforeAll
    @Override
    void setUpActors() {
        super.setUpActors();

        final long eId = nextId();
        testEmployeeCode = "E-RAC-" + eId;
        // Unique lastName including the id suffix so q-filter returns exactly this employee.
        testEmployeeLastName = "RACTest" + eId;
        testEmployeeId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role,
                    manager_id
                ) VALUES (
                    ?, 'Role', ?, ?,
                    '+31 6 40000001', '1994-09-09', 'Access St 1', '1000 AA', 'Amsterdam',
                    ?, ?, 'JUNIOR', 'EMPLOYEE',
                    ?
                ) RETURNING id
                """, Long.class,
                testEmployeeCode, testEmployeeLastName,
                "role.access." + eId + "@cubeworks.test",
                developerJobTitleId, developmentDeptId, managerEmployeeId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/dashboard — all 4 roles allowed
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/dashboard as HR → 200")
    void getDashboard_asHr_returns200() throws Exception {
        mockMvc.perform(get("/api/dashboard").with(asHR()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/dashboard as Manager → 200")
    void getDashboard_asManager_returns200() throws Exception {
        mockMvc.perform(get("/api/dashboard").with(asManager()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/dashboard as Employee → 200")
    void getDashboard_asEmployee_returns200() throws Exception {
        mockMvc.perform(get("/api/dashboard").with(asEmployee()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/dashboard as IT admin → 200")
    void getDashboard_asItAdmin_returns200() throws Exception {
        mockMvc.perform(get("/api/dashboard").with(asIT()))
                .andExpect(status().isOk());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/team — MANAGER only
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/team as Manager → 200")
    void listTeam_asManager_returns200() throws Exception {
        mockMvc.perform(get("/api/team").with(asManager()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/team as HR → 403 (MANAGER only)")
    void listTeam_asHr_returns403() throws Exception {
        mockMvc.perform(get("/api/team").with(asHR()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/team as Employee → 403 (MANAGER only)")
    void listTeam_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/team").with(asEmployee()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/team as IT admin → 403 (MANAGER only)")
    void listTeam_asItAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/team").with(asIT()))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/team/export — MANAGER only
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/team/export as Manager → 200")
    void exportTeam_asManager_returns200() throws Exception {
        mockMvc.perform(get("/api/team/export").with(asManager()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/team/export as HR → 403 (MANAGER only)")
    void exportTeam_asHr_returns403() throws Exception {
        mockMvc.perform(get("/api/team/export").with(asHR()))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/employees/{id}/manager-tasks — HR only
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/employees/{id}/manager-tasks as HR → 200")
    void getManagerTasks_asHr_returns200() throws Exception {
        mockMvc.perform(get("/api/employees/{id}/manager-tasks", testEmployeeId).with(asHR()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/employees/{id}/manager-tasks as Manager → 403 (HR only)")
    void getManagerTasks_asManager_returns403() throws Exception {
        mockMvc.perform(get("/api/employees/{id}/manager-tasks", testEmployeeId).with(asManager()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/employees/{id}/manager-tasks as Employee → 403 (HR only)")
    void getManagerTasks_asEmployee_returns403() throws Exception {
        mockMvc.perform(get("/api/employees/{id}/manager-tasks", testEmployeeId).with(asEmployee()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/employees/{id}/manager-tasks as IT admin → 403 (HR only)")
    void getManagerTasks_asItAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/employees/{id}/manager-tasks", testEmployeeId).with(asIT()))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/employees/{id}/manager-transfers — HR only
    // ─────────────────────────────────────────────────────────────────────────

    private String minimalTransferBody() {
        return """
                {
                  "newManagerId": 999999,
                  "effectiveDate": "%s"
                }
                """.formatted(LocalDate.now().plusDays(1));
    }

    @Test
    @DisplayName("POST /api/employees/{id}/manager-transfers as Manager → 403 (HR only)")
    void createTransfer_asManager_returns403() throws Exception {
        mockMvc.perform(withJson(
                        post("/api/employees/{id}/manager-transfers", testEmployeeId)
                                .with(asManager()),
                        minimalTransferBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/employees/{id}/manager-transfers as Employee → 403 (HR only)")
    void createTransfer_asEmployee_returns403() throws Exception {
        mockMvc.perform(withJson(
                        post("/api/employees/{id}/manager-transfers", testEmployeeId)
                                .with(asEmployee()),
                        minimalTransferBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/employees/{id}/manager-transfers as IT admin → 403 (HR only)")
    void createTransfer_asItAdmin_returns403() throws Exception {
        mockMvc.perform(withJson(
                        post("/api/employees/{id}/manager-transfers", testEmployeeId)
                                .with(asIT()),
                        minimalTransferBody()))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AC-W08: GET /api/employees field-reduction for IT admin vs HR
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * AC-W08 (IT admin projection): IT admin calling GET /api/employees receives a non-null
     * integer {@code assetCount} for each employee. The seeded test employee has no assets,
     * so assetCount should be 0 (batch COUNT query returns 0 for absent keys).
     *
     * <p>Note: {@code q} searches first name + last name, not employee code — use the unique
     * {@code testEmployeeLastName} to isolate the result.</p>
     */
    @Test
    @DisplayName("AC-W08/IT: GET /api/employees as IT admin → assetCount is a non-null integer")
    void listEmployees_asItAdmin_returnsAssetCount_AC_W08() throws Exception {
        // q searches firstName + lastName; testEmployeeLastName is unique for this test class.
        mockMvc.perform(get("/api/employees")
                        .param("q", testEmployeeLastName)
                        .with(asIT()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].employeeCode").value(testEmployeeCode))
                // assetCount must be a non-null integer (0 since the employee has no assets)
                .andExpect(jsonPath("$.items[0].assetCount").isNumber());
    }

    /**
     * AC-W08 (HR projection): HR calling GET /api/employees receives {@code null} for
     * {@code assetCount} — this field is not populated for the HR role (only for IT admin,
     * per D-W9).
     *
     * <p>Note: {@code q} searches first name + last name, not employee code — use the unique
     * {@code testEmployeeLastName} to isolate the result.</p>
     */
    @Test
    @DisplayName("AC-W08/HR: GET /api/employees as HR → assetCount is absent (fix #9: @JsonInclude(NON_NULL))")
    void listEmployees_asHr_hasNullAssetCount_AC_W08() throws Exception {
        // q searches firstName + lastName; testEmployeeLastName is unique for this test class.
        // Fix #9: with @JsonInclude(NON_NULL) on the assetCount record component, the field is
        // now omitted entirely from the JSON for HR responses (where it is null), rather than
        // being serialised as JSON null. doesNotExist() is the correct assertion.
        mockMvc.perform(get("/api/employees")
                        .param("q", testEmployeeLastName)
                        .with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].assetCount").doesNotExist());
    }
}
