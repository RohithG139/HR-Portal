package com.cubeworks.hr;

import com.cubeworks.hr.onboarding.OnboardingOverdueScheduler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for "Onboarding overdue" (FRD-02 §6, D-20, F01 spec §8): HR is alerted once when an
 * onboarding is still in progress {@code ONBOARDING_OVERDUE} (3) days after the start date.
 * {@code validOnboardingBody} uses start date 2027-01-05, so the deadline is 2027-01-08.
 */
class OnboardingOverdueTests extends AbstractApiTest {

    private static final LocalDate START = LocalDate.parse("2027-01-05");

    @Autowired
    private OnboardingOverdueScheduler onboardingOverdueScheduler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Creates an employee via A10 and returns the onboarding row's id. */
    private long createOnboarding(final String emailPrefix) throws Exception {
        final String email = emailPrefix + "." + nextId() + "@outlook.com";
        final String body = mockMvc.perform(withJson(
                        post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        final long employeeId = objectMapper.readTree(body).get("employeeId").asLong();
        return jdbc.queryForObject("SELECT id FROM hr.onboarding WHERE employee_id = ?",
                Long.class, employeeId);
    }

    private int overdueAlerts(final long onboardingId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM hr.notification n
                JOIN hr.onboarding o ON o.employee_id = n.employee_id
                WHERE o.id = ? AND n.title = 'New task: Onboarding overdue'
                """, Integer.class, onboardingId);
    }

    @Test
    @DisplayName("Before start date + 3 days: no alert")
    void notYetOverdue_raisesNothing() throws Exception {
        final long onboardingId = createOnboarding("ovd1");

        onboardingOverdueScheduler.raiseIfOverdue(onboardingId, 3, START.plusDays(2));

        assertEquals(0, overdueAlerts(onboardingId));
    }

    @Test
    @DisplayName("On start date + 3 days: HR is alerted, and only once")
    void overdue_raisesAlertOnce() throws Exception {
        final long onboardingId = createOnboarding("ovd2");

        onboardingOverdueScheduler.raiseIfOverdue(onboardingId, 3, START.plusDays(3));
        final int afterFirst = overdueAlerts(onboardingId);
        onboardingOverdueScheduler.raiseIfOverdue(onboardingId, 3, START.plusDays(4));

        assertEquals(true, afterFirst > 0, "at least one HR user must be notified");
        assertEquals(afterFirst, overdueAlerts(onboardingId), "a second poll must not alert again");
    }

    @Test
    @DisplayName("Completed onboarding: no alert even when past the deadline")
    void completedOnboarding_raisesNothing() throws Exception {
        final long onboardingId = createOnboarding("ovd3");
        jdbc.update("""
                UPDATE hr.onboarding SET status = 'COMPLETED', completed_at = now(),
                       completed_by = (SELECT id FROM hr.employee WHERE app_role = 'HR' LIMIT 1)
                WHERE id = ?
                """, onboardingId);

        onboardingOverdueScheduler.raiseIfOverdue(onboardingId, 3, START.plusDays(10));

        assertEquals(0, overdueAlerts(onboardingId));
    }
}
