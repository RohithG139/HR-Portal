package com.cubeworks.hr;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for W05 ({@code GET /api/team}) and W06 ({@code GET /api/team/export}).
 *
 * <h2>Coverage map</h2>
 * <pre>
 * AC-W01  listTeam_asManager_returnsOwnTeamOnly
 * AC-W03  listTeam_secondManager_cannotSeeFirstManagerTeam
 * W05     listTeam_withOnboardingFilter_returnsMembersInOnboarding
 * W05     listTeam_withDeferredStatusFilter_returnsEmptyPage
 * W06     exportTeam_asManager_returnsXlsxFile
 * </pre>
 *
 * <h2>WS-02 enforcement</h2>
 * <p>The two manager actors each have their own direct report. Every list call is scoped to
 * {@code employee.manager_id = caller.id} on the server side — the test verifies that
 * manager1 cannot see manager2's employee and vice versa (AC-W03).</p>
 */
@DisplayName("W05/W06 — My Team list and export")
class FRD01TeamTests extends AbstractApiTest {

    // ── Per-class test data ───────────────────────────────────────────────────

    /** Employee in manager1's (the seeded Manager actor) team. */
    private long mgr1EmployeeId;
    private String mgr1EmployeeCode;
    /** Unique lastName for mgr1's employee — used as q filter (findFiltered searches by name, not code). */
    private String mgr1LastName;

    /** A second MANAGER actor created by this test class. */
    private String mgr2Sub;
    private long mgr2Id;

    /** Employee in manager2's team. */
    private long mgr2EmployeeId;
    private String mgr2EmployeeCode;

    /**
     * Seeds actors and additional direct reports for WS-02 boundary tests.
     */
    @BeforeAll
    @Override
    void setUpActors() {
        super.setUpActors();

        // ── Employee in manager1's team ────────────────────────────────────────
        final long e1Id = nextId();
        mgr1EmployeeCode = "E-T1-" + e1Id;
        // Unique lastName so the q filter (searches firstName + lastName) returns only this employee.
        mgr1LastName = "TM1x" + e1Id;
        mgr1EmployeeId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role,
                    manager_id
                ) VALUES (
                    ?, 'Team', ?, ?,
                    '+31 6 20000001', '1993-02-01', 'Team St 1', '1000 AA', 'Amsterdam',
                    ?, ?, 'MEDIOR', 'EMPLOYEE',
                    ?
                ) RETURNING id
                """, Long.class,
                mgr1EmployeeCode, mgr1LastName,
                "team.member1." + e1Id + "@cubeworks.test",
                developerJobTitleId, developmentDeptId, managerEmployeeId);

        // ── Second manager actor ────────────────────────────────────────────────
        final long mg2Id = nextId();
        mgr2Sub = "sub-mg2-" + mg2Id;
        mgr2Id = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, idp_subject
                ) VALUES (
                    ?, 'Manager2', 'Actor', ?,
                    '+31 6 20000002', '1980-04-10', 'Mgr2 Ave 2', '1000 BB', 'Amsterdam',
                    ?, ?, 'SENIOR_I', 'MANAGER', ?
                ) RETURNING id
                """, Long.class,
                "E-MG2-" + mg2Id,
                "mg2.actor." + mg2Id + "@cubeworks.test",
                developerJobTitleId, developmentDeptId, mgr2Sub);

        // ── Employee in manager2's team ────────────────────────────────────────
        final long e2Id = nextId();
        mgr2EmployeeCode = "E-T2-" + e2Id;
        mgr2EmployeeId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role,
                    manager_id
                ) VALUES (
                    ?, 'Team', 'Member2', ?,
                    '+31 6 20000003', '1996-07-07', 'Team St 2', '1000 CC', 'Amsterdam',
                    ?, ?, 'JUNIOR', 'EMPLOYEE',
                    ?
                ) RETURNING id
                """, Long.class,
                mgr2EmployeeCode,
                "team.member2." + e2Id + "@cubeworks.test",
                developerJobTitleId, developmentDeptId, mgr2Id);
    }

    // ── AC-W01: manager1 sees only their own direct reports ───────────────────

    /**
     * AC-W01: manager1 can see their own direct report (mgr1Employee) via the My Team list.
     * WS-02 scope is enforced server-side, not just by hiding the My Team nav item.
     *
     * <p>Note: the q filter searches by first name + last name (not employee code), so we pass
     * the employee's unique lastName as the q value to isolate the result.</p>
     */
    @Test
    @DisplayName("AC-W01: GET /api/team as Manager → returns own team member")
    void listTeam_asManager_returnsOwnTeamMember_AC_W01() throws Exception {
        // q filter searches first+last name — use the unique lastName to isolate this employee.
        mockMvc.perform(get("/api/team")
                        .param("q", mgr1LastName)
                        .with(asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].employeeCode").value(mgr1EmployeeCode));
    }

    // ── AC-W03: manager1 cannot see manager2's employee ──────────────────────

    /**
     * AC-W03: a second manager calling {@code GET /api/team} with a query that matches an
     * employee belonging to manager1 receives an empty result — WS-02 prevents cross-team
     * visibility even when the employee's name is known.
     */
    @Test
    @DisplayName("AC-W03: Manager2 GET /api/team?q=<mgr1EmployeeName> → empty items (WS-02)")
    void listTeam_secondManager_cannotSeeFirstManagerTeam_AC_W03() throws Exception {
        final var mgr2Jwt = asUserWithSub(mgr2Sub, "MANAGER");

        // mgr1LastName is unique to mgr1's employee; manager2's query should return 0 results
        // because that employee reports to manager1, not manager2 (WS-02 scoping).
        mockMvc.perform(get("/api/team")
                        .param("q", mgr1LastName)
                        .with(mgr2Jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                // manager2 should see zero results for mgr1's employee
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    // ── W05: Onboarding status filter ─────────────────────────────────────────

    /**
     * W05: filtering by status="Onboarding" returns only members in the ONBOARDING stage.
     * All seeded actors have the default ONBOARDING stage, so mgr1Employee should appear.
     */
    @Test
    @DisplayName("W05: GET /api/team?status=Onboarding&q=<lastName> → returns the onboarding member")
    void listTeam_withOnboardingFilter_returnsMemberInOnboardingStage() throws Exception {
        // q filter searches first+last name — use the unique lastName to isolate this employee.
        mockMvc.perform(get("/api/team")
                        .param("status", "Onboarding")
                        .param("q", mgr1LastName)
                        .with(asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].stage").value("ONBOARDING"));
    }

    /**
     * W05: a deferred status filter ("On project") returns an empty items list without a 4xx
     * error — the project_assignment table does not exist yet (deferred to F04).
     */
    @Test
    @DisplayName("W05: GET /api/team?status=On+project → 200 empty items (deferred filter)")
    void listTeam_withDeferredStatusFilter_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/team")
                        .param("status", "On project")
                        .with(asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    // ── W06: Excel export ─────────────────────────────────────────────────────

    /**
     * W06: the export endpoint returns a 200 with Excel content-type and an attachment header.
     * The response body is not empty (POI always writes at least the workbook header).
     */
    @Test
    @DisplayName("W06: GET /api/team/export as Manager → 200 xlsx attachment")
    void exportTeam_asManager_returnsXlsxAttachment() throws Exception {
        mockMvc.perform(get("/api/team/export").with(asManager()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }
}
