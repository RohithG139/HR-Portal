package com.cubeworks.hr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;

import com.cubeworks.hr.onboarding.OnboardingEmailReleaseScheduler;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for "mail triggered by the case engine" (2026-10-21): "Welcome new employee" is written
 * {@code HELD} at employee creation and only released once {@link
 * com.cubeworks.hr.engine.EngineCallbackController} (the case engine's own callback) or {@link
 * OnboardingEmailReleaseScheduler} (the timeout fallback) calls {@code OnboardingEmailReleaseService}.
 *
 * <h2>Why these don't mock {@code KeycloakAdminClient}</h2>
 * <p>No test in this suite mocks {@code CaseEngineClient}/{@code KeycloakAdminClient} — both
 * already have a {@code @Profile("test")} no-op implementation the test context wires in
 * automatically (same pattern as {@code NoOpCaseEngineClient}). These tests verify the part that
 * is genuinely observable without mocking: the {@code email_outbox} row's actual status
 * transition in the database.</p>
 */
class OnboardingEmailReleaseTests extends AbstractApiTest {

    @Autowired
    private OnboardingEmailReleaseScheduler onboardingEmailReleaseScheduler;

    @Value("${cubeworks.engine.callback-secret}")
    private String callbackSecret;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates an employee via the real A10 endpoint and returns their employee code
     * (the case file's {@code Employee.employeeId}).
     */
    private String createTestEmployee(final String emailPrefix) throws Exception {
        final String email = emailPrefix + "." + nextId() + "@outlook.com";
        final String createResponse = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(createResponse).get("employeeCode").asText();
    }

    @Test
    @DisplayName("createOnboarding writes WELCOME_NEW_EMPLOYEE as HELD, not PENDING")
    void createOnboarding_writesWelcomeEmailAsHeld_notPending() throws Exception {
        final String employeeCode = createTestEmployee("held1");

        final String status = jdbc.queryForObject("""
                SELECT eo.status FROM hr.email_outbox eo
                JOIN hr.employee e ON e.id = eo.employee_id
                WHERE e.employee_code = ? AND eo.template_code = 'WELCOME_NEW_EMPLOYEE'
                """, String.class, employeeCode);

        assertEquals("HELD", status,
                "Welcome new employee must be HELD until the case engine's callback releases it");
    }

    @Test
    @DisplayName("Engine callback with the correct secret releases the HELD row to PENDING")
    void engineCallback_withCorrectSecret_releasesHeldEmail() throws Exception {
        final String employeeCode = createTestEmployee("held2");

        mockMvc.perform(post("/api/internal/engine-events/onboarding-started")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Engine-Callback-Secret", callbackSecret)
                        .content("{\"employeeId\":\"" + employeeCode + "\"}"))
                .andExpect(status().isNoContent());

        final String outboxStatus = jdbc.queryForObject("""
                SELECT eo.status FROM hr.email_outbox eo
                JOIN hr.employee e ON e.id = eo.employee_id
                WHERE e.employee_code = ? AND eo.template_code = 'WELCOME_NEW_EMPLOYEE'
                """, String.class, employeeCode);

        assertEquals("PENDING", outboxStatus,
                "A correct-secret callback must release the HELD row to PENDING");
    }

    @Test
    @DisplayName("Engine callback with a wrong secret returns 403 and does not release the row")
    void engineCallback_withWrongSecret_returns403AndLeavesRowHeld() throws Exception {
        final String employeeCode = createTestEmployee("held3");

        mockMvc.perform(post("/api/internal/engine-events/onboarding-started")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Engine-Callback-Secret", "wrong-secret")
                        .content("{\"employeeId\":\"" + employeeCode + "\"}"))
                .andExpect(status().isForbidden());

        final String outboxStatus = jdbc.queryForObject("""
                SELECT eo.status FROM hr.email_outbox eo
                JOIN hr.employee e ON e.id = eo.employee_id
                WHERE e.employee_code = ? AND eo.template_code = 'WELCOME_NEW_EMPLOYEE'
                """, String.class, employeeCode);

        assertEquals("HELD", outboxStatus, "A wrong-secret callback must not release anything");
    }

    @Test
    @DisplayName("Engine callback with a missing secret header returns 403, not a 400")
    void engineCallback_withMissingSecret_returns403() throws Exception {
        final String employeeCode = createTestEmployee("held4");

        mockMvc.perform(post("/api/internal/engine-events/onboarding-started")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":\"" + employeeCode + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Engine callback for an unknown employee code is a safe no-op (204, not an error)")
    void engineCallback_withUnknownEmployeeCode_returns204AndNoop() throws Exception {
        mockMvc.perform(post("/api/internal/engine-events/onboarding-started")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Engine-Callback-Secret", callbackSecret)
                        .content("{\"employeeId\":\"E-DOES-NOT-EXIST-99999\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Fallback scheduler releases HELD rows older than the timeout, leaves fresh ones alone")
    void releaseStaleHeldEmails_releasesOldRows_leavesFreshRowsHeld() throws Exception {
        final String staleEmployeeCode = createTestEmployee("stale1");
        final String freshEmployeeCode = createTestEmployee("fresh1");

        // Backdate only the stale employee's outbox row past the 10-minute timeout.
        jdbc.update("""
                UPDATE hr.email_outbox SET created_at = ?
                WHERE employee_id = (SELECT id FROM hr.employee WHERE employee_code = ?)
                  AND template_code = 'WELCOME_NEW_EMPLOYEE'
                """, OffsetDateTime.now().minusMinutes(15), staleEmployeeCode);

        onboardingEmailReleaseScheduler.releaseStaleHeldEmails();

        final String staleStatus = jdbc.queryForObject("""
                SELECT eo.status FROM hr.email_outbox eo
                JOIN hr.employee e ON e.id = eo.employee_id
                WHERE e.employee_code = ? AND eo.template_code = 'WELCOME_NEW_EMPLOYEE'
                """, String.class, staleEmployeeCode);
        final String freshStatus = jdbc.queryForObject("""
                SELECT eo.status FROM hr.email_outbox eo
                JOIN hr.employee e ON e.id = eo.employee_id
                WHERE e.employee_code = ? AND eo.template_code = 'WELCOME_NEW_EMPLOYEE'
                """, String.class, freshEmployeeCode);

        assertEquals("PENDING", staleStatus, "A HELD row past the timeout must be released by the fallback");
        assertEquals("HELD", freshStatus, "A freshly-created HELD row must not be touched by the fallback yet");
    }
}
