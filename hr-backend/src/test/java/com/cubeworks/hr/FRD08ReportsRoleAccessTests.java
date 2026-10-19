package com.cubeworks.hr;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for FRD-08 Reports role access control — AC-17.
 *
 * <h2>AC-17 coverage</h2>
 * <p>All report endpoints (C31–C47) require the {@code HR} role.  The
 * {@code @PreAuthorize("hasRole('HR')")} annotation is on the controller class, so every
 * method inherits it.  We test a representative sample: one endpoint per resource group,
 * one non-HR role per test.</p>
 *
 * <p>Because the class-level annotation applies uniformly, a failed access check on one
 * endpoint implies all others in {@code ReportController} are equally protected.</p>
 */
class FRD08ReportsRoleAccessTests extends AbstractApiTest {

    @BeforeAll
    void setUp() {
        super.setUpActors();
    }

    // ── C31 Summary ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-17: getSummary_asManager_returns403")
    void getSummary_asManager_returns403() throws Exception {
        perform(get("/api/reports/summary").with(asManager()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: getSummary_asEmployee_returns403")
    void getSummary_asEmployee_returns403() throws Exception {
        perform(get("/api/reports/summary").with(asEmployee()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: getSummary_asIT_returns403")
    void getSummary_asIT_returns403() throws Exception {
        perform(get("/api/reports/summary").with(asIT()))
                .andExpect(status().isForbidden());
    }

    // ── C32 Headcount ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-17: getHeadcount_asManager_returns403")
    void getHeadcount_asManager_returns403() throws Exception {
        perform(get("/api/reports/headcount").with(asManager()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: getHeadcount_asEmployee_returns403")
    void getHeadcount_asEmployee_returns403() throws Exception {
        perform(get("/api/reports/headcount").with(asEmployee()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: exportHeadcount_asIT_returns403")
    void exportHeadcount_asIT_returns403() throws Exception {
        perform(get("/api/reports/headcount/export").with(asIT()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: exportAll_asManager_returns403")
    void exportAll_asManager_returns403() throws Exception {
        perform(get("/api/reports/export").with(asManager()))
                .andExpect(status().isForbidden());
    }

    // ── C34 Bench ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-17: getBench_asManager_returns403")
    void getBench_asManager_returns403() throws Exception {
        perform(get("/api/reports/bench").with(asManager()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: getBench_asEmployee_returns403")
    void getBench_asEmployee_returns403() throws Exception {
        perform(get("/api/reports/bench").with(asEmployee()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: exportBench_asManager_returns403")
    void exportBench_asManager_returns403() throws Exception {
        perform(get("/api/reports/bench/export").with(asManager()))
                .andExpect(status().isForbidden());
    }

    // ── C36 Allocation ────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-17: getAllocation_asIT_returns403")
    void getAllocation_asIT_returns403() throws Exception {
        perform(get("/api/reports/allocation").with(asIT()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: getAllocation_asEmployee_returns403")
    void getAllocation_asEmployee_returns403() throws Exception {
        perform(get("/api/reports/allocation").with(asEmployee()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: exportAllocation_asManager_returns403")
    void exportAllocation_asManager_returns403() throws Exception {
        perform(get("/api/reports/allocation/export").with(asManager()))
                .andExpect(status().isForbidden());
    }

    // ── C38 Appraisal Status ──────────────────────────────────────────────────

    @Test
    @DisplayName("AC-17: getAppraisalStatus_asManager_returns403")
    void getAppraisalStatus_asManager_returns403() throws Exception {
        perform(get("/api/reports/appraisal-status").with(asManager()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: getAppraisalStatus_asEmployee_returns403")
    void getAppraisalStatus_asEmployee_returns403() throws Exception {
        perform(get("/api/reports/appraisal-status").with(asEmployee()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: exportAppraisalStatus_asIT_returns403")
    void exportAppraisalStatus_asIT_returns403() throws Exception {
        perform(get("/api/reports/appraisal-status/export").with(asIT()))
                .andExpect(status().isForbidden());
    }

    // ── C40 PIPs ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-17: getPips_asManager_returns403")
    void getPips_asManager_returns403() throws Exception {
        perform(get("/api/reports/pips").with(asManager()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: getPips_asEmployee_returns403")
    void getPips_asEmployee_returns403() throws Exception {
        perform(get("/api/reports/pips").with(asEmployee()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: exportPips_asIT_returns403")
    void exportPips_asIT_returns403() throws Exception {
        perform(get("/api/reports/pips/export").with(asIT()))
                .andExpect(status().isForbidden());
    }

    // ── C42 Onboarding ────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-17: getOnboarding_asManager_returns403")
    void getOnboarding_asManager_returns403() throws Exception {
        perform(get("/api/reports/onboarding").with(asManager()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: getOnboarding_asIT_returns403")
    void getOnboarding_asIT_returns403() throws Exception {
        perform(get("/api/reports/onboarding").with(asIT()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: exportOnboarding_asEmployee_returns403")
    void exportOnboarding_asEmployee_returns403() throws Exception {
        perform(get("/api/reports/onboarding/export").with(asEmployee()))
                .andExpect(status().isForbidden());
    }

    // ── C44 Contracts & Probation ─────────────────────────────────────────────

    @Test
    @DisplayName("AC-17: getContractsProbation_asManager_returns403")
    void getContractsProbation_asManager_returns403() throws Exception {
        perform(get("/api/reports/contracts-probation").with(asManager()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: getContractsProbation_asEmployee_returns403")
    void getContractsProbation_asEmployee_returns403() throws Exception {
        perform(get("/api/reports/contracts-probation").with(asEmployee()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: exportContractsProbation_asIT_returns403")
    void exportContractsProbation_asIT_returns403() throws Exception {
        perform(get("/api/reports/contracts-probation/export").with(asIT()))
                .andExpect(status().isForbidden());
    }

    // ── C46 Unreturned Assets ─────────────────────────────────────────────────

    @Test
    @DisplayName("AC-17: getUnreturnedAssets_asManager_returns403")
    void getUnreturnedAssets_asManager_returns403() throws Exception {
        perform(get("/api/reports/unreturned-assets").with(asManager()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: getUnreturnedAssets_asEmployee_returns403")
    void getUnreturnedAssets_asEmployee_returns403() throws Exception {
        perform(get("/api/reports/unreturned-assets").with(asEmployee()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AC-17: exportUnreturnedAssets_asIT_returns403")
    void exportUnreturnedAssets_asIT_returns403() throws Exception {
        perform(get("/api/reports/unreturned-assets/export").with(asIT()))
                .andExpect(status().isForbidden());
    }
}
