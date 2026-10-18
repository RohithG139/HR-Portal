package com.cubeworks.hr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code GET /api/dashboard} (W01, FRD-01 Wave 3).
 *
 * <h2>Coverage map</h2>
 * <pre>
 * W01/HR    getDashboard_asHr_returnsHrShape
 * W01/MGR   getDashboard_asManager_returnsManagerShape
 * W01/EMP   getDashboard_asEmployee_returnsEmployeeShape
 * AC-W07    getDashboard_asItAdmin_returnsItAdminFields
 * </pre>
 *
 * <p>Each test verifies the response <em>shape</em> (fields present with the right type), not
 * the exact count values — those depend on how many employees other test classes have seeded in
 * the shared DB, so they are not predictable. What matters is that the endpoint dispatches to
 * the correct DTO for each role and serialises all expected fields.</p>
 */
@DisplayName("W01 — GET /api/dashboard role-dispatched shape")
class FRD01DashboardTests extends AbstractApiTest {

    // ── W01/HR — HR gets activeEmployees, onboardingCount, tasks ────────────

    /**
     * W01/HR: an HR caller receives the HR-shaped dashboard.
     * Verifies that {@code activeEmployees}, {@code activeEmployeesDelta},
     * {@code onboardingCount}, {@code onboardingNames} and {@code tasks} are all present.
     */
    @Test
    @DisplayName("W01/HR: GET /api/dashboard as HR → HR shape with expected fields")
    void getDashboard_asHr_returnsHrShape() throws Exception {
        mockMvc.perform(get("/api/dashboard").with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeEmployees").isNumber())
                .andExpect(jsonPath("$.activeEmployeesDelta").isNumber())
                .andExpect(jsonPath("$.onboardingCount").isNumber())
                .andExpect(jsonPath("$.onboardingNames").isArray())
                .andExpect(jsonPath("$.tasks").isArray());
    }

    // ── W01/MGR — Manager gets teamMembers, tasks, myTeam ───────────────────

    /**
     * W01/MGR: a Manager caller receives the Manager-shaped dashboard.
     * Verifies {@code teamMembers}, {@code tasks} and {@code myTeam} are all present.
     * The seeded Employee actor reports to the Manager actor, so {@code teamMembers} >= 1.
     */
    @Test
    @DisplayName("W01/MGR: GET /api/dashboard as Manager → Manager shape with myTeam")
    void getDashboard_asManager_returnsManagerShape() throws Exception {
        mockMvc.perform(get("/api/dashboard").with(asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamMembers").isNumber())
                .andExpect(jsonPath("$.tasks").isArray())
                .andExpect(jsonPath("$.myTeam").isArray());
    }

    // ── W01/EMP — Employee gets equipment and tasks ──────────────────────────

    /**
     * W01/EMP: an Employee caller receives the Employee-shaped dashboard.
     * Verifies {@code equipment} and {@code tasks} arrays are present.
     */
    @Test
    @DisplayName("W01/EMP: GET /api/dashboard as Employee → Employee shape")
    void getDashboard_asEmployee_returnsEmployeeShape() throws Exception {
        mockMvc.perform(get("/api/dashboard").with(asEmployee()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipment").isArray())
                .andExpect(jsonPath("$.tasks").isArray());
    }

    // ── AC-W07 — IT admin gets equipmentRequestsCount and openSetupItemsCount

    /**
     * AC-W07: an IT admin caller receives the IT-admin-shaped dashboard with the two
     * equipment-related count fields. Both counts must be non-negative integers.
     */
    @Test
    @DisplayName("AC-W07: GET /api/dashboard as IT admin → equipmentRequestsCount and openSetupItemsCount present")
    void getDashboard_asItAdmin_returnsItAdminFields_AC_W07() throws Exception {
        mockMvc.perform(get("/api/dashboard").with(asIT()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentRequestsCount").isNumber())
                .andExpect(jsonPath("$.openSetupItemsCount").isNumber())
                .andExpect(jsonPath("$.tasks").isArray());
    }
}
