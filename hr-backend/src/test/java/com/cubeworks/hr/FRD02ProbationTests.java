package com.cubeworks.hr;

import com.cubeworks.hr.onboarding.OnboardingOverdueScheduler;
import com.cubeworks.hr.service.probation.ProbationReviewScheduler;
import com.cubeworks.hr.service.probation.ProbationReviewService;
import com.cubeworks.hr.task.UndoWindowScheduler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FRD-02 probation review (spec {@code FRD-02-onboarding-probation} §11 AC-P1…AC-P3, P01–P04,
 * ONB-08, D-P2/D-P5 user decisions 2026-10-21). Engine calls go to {@code NoOpCaseEngineClient};
 * the undo window is closed by moving {@code pending_execute_at} into the past and running the
 * scheduler's per-task step directly.
 */
class FRD02ProbationTests extends AbstractApiTest {

    /** AC-P1: probation ends 05-11-2026 (start 05-09-2026 + 2 months); review due 22-10-2026. */
    private static final LocalDate START = LocalDate.parse("2026-09-05");
    private static final LocalDate REVIEW_DAY = LocalDate.parse("2026-10-22");

    @Autowired private ProbationReviewService probationReviewService;
    @Autowired private ProbationReviewScheduler probationReviewScheduler;
    @Autowired private UndoWindowScheduler undoWindowScheduler;
    @Autowired private OnboardingOverdueScheduler onboardingOverdueScheduler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String otherManagerSub;

    @BeforeAll
    void setUpOtherManager() {
        final long id = nextId();
        otherManagerSub = "sub-mg2-" + id;
        jdbc.update("""
                INSERT INTO hr.employee (employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, idp_subject)
                VALUES (?, 'Other', 'Manager', ?, '+31 6 1', '1980-01-01', 'S 1', '1000 AA', 'A',
                    ?, ?, 'SENIOR', 'MANAGER', ?)
                """, "M2" + id, "mg2." + id + "@cubeworks.test", developerJobTitleId,
                developmentDeptId, otherManagerSub);
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /** An active employee (Sanne) managed by the Manager actor, with a 2-month probation. */
    private long[] createProbationer() {
        final long id = nextId();
        final String code = "P" + id;
        final long employeeId = jdbc.queryForObject("""
                INSERT INTO hr.employee (employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, manager_id, stage)
                VALUES (?, 'Sanne', 'de Boer', ?, '+31 6 2', '1996-02-02', 'S 2', '1000 AB', 'A',
                    ?, ?, 'JUNIOR', 'EMPLOYEE', ?, 'ACTIVE') RETURNING id
                """, Long.class, code, "sanne." + id + "@outlook.com",
                developerJobTitleId, developmentDeptId, managerEmployeeId);
        final long contractId = jdbc.queryForObject("""
                INSERT INTO hr.contract (employee_id, contract_type, start_date, hours_per_week, probation_months)
                VALUES (?, 'PERMANENT', ?, 40, 2) RETURNING id
                """, Long.class, employeeId, START);
        return new long[] {employeeId, contractId};
    }

    private long createDueReview(final long contractId) {
        assertTrue(probationReviewService.createReviewIfDue(contractId, REVIEW_DAY));
        return jdbc.queryForObject("SELECT id FROM hr.review WHERE contract_id = ? AND review_type = 'PROBATION'",
                Long.class, contractId);
    }

    private JsonNode getReview(final long reviewId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/probation-reviews/" + reviewId).with(asManager()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /** Full answer body: every scale question rated, summary as given, outcome as given. */
    private String body(final long reviewId, final Integer rating, final String summary, final String outcome)
            throws Exception {
        final List<String> answers = new ArrayList<>();
        final JsonNode review = getReview(reviewId);
        for (final JsonNode q : review.get("questions")) {
            final long qid = q.get("questionId").asLong();
            if ("SCALE_1_5".equals(q.get("answerType").asText())) {
                answers.add("{\"questionId\":" + qid + ",\"rating\":" + rating + "}");
            } else if ("TEXT".equals(q.get("answerType").asText())) {
                answers.add("{\"questionId\":" + qid + ",\"textAnswer\":"
                        + (summary == null ? "null" : "\"" + summary + "\"") + "}");
            }
        }
        return "{\"version\":" + review.get("version").asInt()
                + ",\"answers\":[" + String.join(",", answers) + "],\"outcome\":"
                + (outcome == null ? "null" : "\"" + outcome + "\"") + "}";
    }

    /** Closes the undo window of an engine task and runs the scheduler step (R-API-6). */
    private void expireAndExecute(final long taskId) {
        jdbc.update("UPDATE hr.engine_task SET pending_execute_at = now() - interval '1 minute' WHERE id = ?", taskId);
        undoWindowScheduler.processSingleTask(taskId);
    }

    private long submit(final long reviewId, final String outcome) throws Exception {
        final String resp = mockMvc.perform(withJson(post("/api/probation-reviews/" + reviewId + "/submit")
                        .with(asManager()), body(reviewId, 4, "Strong first months.", outcome)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.undoUntil").exists())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("taskId").asLong();
    }

    private long approveTaskId(final long employeeId) {
        return jdbc.queryForObject("""
                SELECT id FROM hr.engine_task WHERE employee_id = ? AND task_name = 'Approve end of employment'
                  AND completed_at IS NULL
                """, Long.class, employeeId);
    }

    // ── AC-P1 Probation review is created on time ────────────────────────────

    @Test
    @DisplayName("AC-P1: on 22-10-2026 (14 days before 05-11-2026) the manager receives a \"Probation review\" task")
    void acP1_reviewCreatedOnTime() throws Exception {
        final long[] p = createProbationer();

        assertFalse(probationReviewService.createReviewIfDue(p[1], REVIEW_DAY.minusDays(1)), "not due on 21-10");
        assertTrue(probationReviewScheduler.raiseDueReviews(REVIEW_DAY) >= 1);

        final long reviewId = jdbc.queryForObject("SELECT id FROM hr.review WHERE contract_id = ?", Long.class, p[1]);
        assertEquals("2026-11-05", jdbc.queryForObject("SELECT due_date::text FROM hr.review WHERE id = ?",
                String.class, reviewId));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM hr.engine_task WHERE employee_id = ? AND task_name = 'Probation review'
                  AND owner_employee_id = ? AND related_entity = 'REVIEW' AND related_id = ?
                """, Integer.class, p[0], managerEmployeeId, reviewId));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM hr.email_outbox WHERE template_code = 'PROBATION_REVIEW_DUE' AND employee_id = ?
                """, Integer.class, p[0]));
        mockMvc.perform(get("/api/tasks").with(asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.relatedId == " + reviewId + ")].title", hasItem("Probation review")))
                .andExpect(jsonPath("$.content[?(@.relatedId == " + reviewId + ")].module", hasItem("PROBATION")));

        // only one review per contract
        assertFalse(probationReviewService.createReviewIfDue(p[1], REVIEW_DAY.plusDays(1)));
    }

    @Test
    @DisplayName("No retroactive review once probation has ended (e.g. employees imported later)")
    void noReviewAfterProbationEnded() {
        final long[] p = createProbationer();
        assertFalse(probationReviewService.createReviewIfDue(p[1], LocalDate.parse("2026-11-06")));
        assertFalse(probationReviewScheduler.raiseDueReviews(LocalDate.parse("2026-11-06")) > 0
                && jdbc.queryForObject("SELECT count(*) FROM hr.review WHERE contract_id = ?", Integer.class, p[1]) > 0);
        // still created on the last day of probation
        assertTrue(probationReviewService.createReviewIfDue(p[1], LocalDate.parse("2026-11-05")));
    }

    // ── P01 access (WS-02, D-P2, G-02) ───────────────────────────────────────

    @Test
    @DisplayName("P01: manager and HR can read; employee, IT admin and another manager get 403")
    void p01_access() throws Exception {
        final long reviewId = createDueReview(createProbationer()[1]);

        final JsonNode review = getReview(reviewId);
        assertEquals(4, review.get("questions").size(), "3 ratings + summary (V17)");
        assertEquals("Quality of work", review.get("questions").get(0).get("text").asText());
        assertEquals("2026-11-05", review.get("probationEndDate").asText());
        assertTrue(review.get("editable").asBoolean());

        mockMvc.perform(get("/api/probation-reviews/" + reviewId).with(asHR())).andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(false));
        mockMvc.perform(get("/api/probation-reviews/" + reviewId).with(asEmployee())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/probation-reviews/" + reviewId).with(asIT())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/probation-reviews/" + reviewId).with(asUserWithSub(otherManagerSub, "MANAGER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/probation-reviews/999999999").with(asManager())).andExpect(status().isNotFound());
    }

    // ── AC-P3 + validation messages ─────────────────────────────────────────

    @Test
    @DisplayName("AC-P3: all ratings but blank summary -> \"Add a summary and choose an outcome.\"")
    void acP3_blankSummary() throws Exception {
        final long reviewId = createDueReview(createProbationer()[1]);

        mockMvc.perform(withJson(post("/api/probation-reviews/" + reviewId + "/submit").with(asManager()),
                        body(reviewId, 4, "", "CONFIRM")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].message", hasItem("Add a summary and choose an outcome.")));
    }

    @Test
    @DisplayName("Missing outcome -> \"Add a summary and choose an outcome.\" on field outcome; missing rating -> \"This field is required.\"")
    void validation_outcomeAndRating() throws Exception {
        final long reviewId = createDueReview(createProbationer()[1]);

        mockMvc.perform(withJson(post("/api/probation-reviews/" + reviewId + "/submit").with(asManager()),
                        body(reviewId, 4, "Fine.", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'outcome')].message",
                        hasItem("Add a summary and choose an outcome.")));
        mockMvc.perform(withJson(post("/api/probation-reviews/" + reviewId + "/submit").with(asManager()),
                        body(reviewId, null, "Fine.", "CONFIRM")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].message", hasItem("This field is required.")));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM hr.review WHERE id = ?", String.class, reviewId));
    }

    // ── P02 draft ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P02: draft keeps answers, status stays DRAFT; employee/HR get 403")
    void p02_saveDraft() throws Exception {
        final long reviewId = createDueReview(createProbationer()[1]);

        mockMvc.perform(withJson(put("/api/probation-reviews/" + reviewId).with(asManager()),
                        body(reviewId, 3, "Half way.", "CONFIRM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.outcome").value("CONFIRM"))
                .andExpect(jsonPath("$.questions[0].rating").value(3))
                .andExpect(jsonPath("$.questions[3].textAnswer").value("Half way."));
        mockMvc.perform(withJson(put("/api/probation-reviews/" + reviewId).with(asEmployee()), "{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(withJson(put("/api/probation-reviews/" + reviewId).with(asHR()), "{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(withJson(put("/api/probation-reviews/" + reviewId).with(asUserWithSub(otherManagerSub, "MANAGER")), "{}"))
                .andExpect(status().isForbidden());
    }

    // ── P03 submit + undo ────────────────────────────────────────────────────

    @Test
    @DisplayName("P03: submit returns 202, undo reverts to DRAFT; a submitted review cannot be changed (409)")
    void p03_submitAndUndo() throws Exception {
        final long reviewId = createDueReview(createProbationer()[1]);

        final long taskId = submit(reviewId, "CONFIRM");
        assertEquals("SUBMITTED", jdbc.queryForObject("SELECT status FROM hr.review WHERE id = ?", String.class, reviewId));
        mockMvc.perform(withJson(put("/api/probation-reviews/" + reviewId).with(asManager()), "{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_SUBMITTED"));

        mockMvc.perform(post("/api/tasks/" + taskId + "/undo").with(asManager())).andExpect(status().isOk());
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM hr.review WHERE id = ?", String.class, reviewId));

        // after an undo the manager can submit again (same engine task row, re-pended)
        assertEquals(taskId, submit(reviewId, "CONFIRM"));
    }

    @Test
    @DisplayName("R-DB-5: P02 without version -> 400, with a stale version -> 409 STALE_VERSION")
    void p02_versionChecked() throws Exception {
        final long reviewId = createDueReview(createProbationer()[1]);
        final String current = body(reviewId, 3, "x", null);

        mockMvc.perform(withJson(put("/api/probation-reviews/" + reviewId).with(asManager()),
                        current.replaceFirst("\"version\":\\d+,", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"));
        mockMvc.perform(withJson(put("/api/probation-reviews/" + reviewId).with(asManager()), current))
                .andExpect(status().isOk());
        mockMvc.perform(withJson(put("/api/probation-reviews/" + reviewId).with(asManager()), current))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    @DisplayName("HR can decide again after undoing a decision")
    void hrDecision_undoThenDecideAgain() throws Exception {
        final long[] p = createProbationer();
        final long reviewId = createDueReview(p[1]);
        expireAndExecute(submit(reviewId, "END"));
        final String first = mockMvc.perform(withJson(post("/api/probation-reviews/" + reviewId + "/hr-decision")
                        .with(asHR()), "{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/tasks/" + objectMapper.readTree(first).get("taskId").asLong() + "/undo").with(asHR()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/probation-reviews/" + reviewId).with(asHR()))
                .andExpect(jsonPath("$.canDecide").value(true));
        mockMvc.perform(withJson(post("/api/probation-reviews/" + reviewId + "/hr-decision").with(asHR()),
                        "{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isAccepted());
    }

    // ── CONFIRM ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Confirm employment: no HR task, no exit, HR decision is refused (409)")
    void confirm_noHrStep() throws Exception {
        final long[] p = createProbationer();
        final long reviewId = createDueReview(p[1]);

        expireAndExecute(submit(reviewId, "CONFIRM"));

        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM hr.engine_task WHERE employee_id = ? AND task_name = 'Approve end of employment'
                """, Integer.class, p[0]));
        mockMvc.perform(withJson(post("/api/probation-reviews/" + reviewId + "/hr-decision").with(asHR()),
                        "{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_AWAITING_DECISION"));
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT stage FROM hr.employee WHERE id = ?", String.class, p[0]));
    }

    // ── AC-P2 + ONB-08 END → HR ─────────────────────────────────────────────

    @Test
    @DisplayName("AC-P2/ONB-08: End employment waits for HR; approval writes employee_exit and starts offboarding")
    void end_approved_startsOffboarding() throws Exception {
        final long[] p = createProbationer();
        final long reviewId = createDueReview(p[1]);

        // before the undo window closes HR cannot decide yet
        final long submitTask = submit(reviewId, "END");
        mockMvc.perform(withJson(post("/api/probation-reviews/" + reviewId + "/hr-decision").with(asHR()),
                        "{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isConflict());
        expireAndExecute(submitTask);

        // AC-P2: offboarding has not started; HR sees a pending decision
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM hr.employee_exit WHERE employee_id = ?",
                Integer.class, p[0]));
        mockMvc.perform(get("/api/tasks").with(asHR()))
                .andExpect(jsonPath("$.content[?(@.relatedId == " + reviewId + ")].title",
                        hasItem("Approve end of employment")));
        mockMvc.perform(get("/api/probation-reviews/" + reviewId).with(asHR()))
                .andExpect(jsonPath("$.awaitingHrDecision").value(true))
                .andExpect(jsonPath("$.canDecide").value(true));

        // role check + validation
        mockMvc.perform(withJson(post("/api/probation-reviews/" + reviewId + "/hr-decision").with(asManager()),
                        "{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(withJson(post("/api/probation-reviews/" + reviewId + "/hr-decision").with(asHR()), "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("This field is required."));

        mockMvc.perform(withJson(post("/api/probation-reviews/" + reviewId + "/hr-decision").with(asHR()),
                        "{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isAccepted());
        expireAndExecute(approveTaskId(p[0]));

        assertEquals("PROBATION_NOT_PASSED|SYSTEM|APPROVED", jdbc.queryForObject("""
                SELECT exit_type || '|' || initiated_by_role || '|' || status FROM hr.employee_exit WHERE employee_id = ?
                """, String.class, p[0]));
        assertEquals("OFFBOARDING", jdbc.queryForObject("SELECT stage FROM hr.employee WHERE id = ?", String.class, p[0]));
        assertEquals("APPROVED", jdbc.queryForObject("SELECT hr_decision FROM hr.review WHERE id = ?", String.class, reviewId));
    }

    @Test
    @DisplayName("D-P5: HR rejects End employment -> employee stays active, no exit")
    void end_rejected_employeeConfirmed() throws Exception {
        final long[] p = createProbationer();
        final long reviewId = createDueReview(p[1]);
        expireAndExecute(submit(reviewId, "END"));

        mockMvc.perform(withJson(post("/api/probation-reviews/" + reviewId + "/hr-decision").with(asHR()),
                        "{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isAccepted());
        expireAndExecute(approveTaskId(p[0]));

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM hr.employee_exit WHERE employee_id = ?",
                Integer.class, p[0]));
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT stage FROM hr.employee WHERE id = ?", String.class, p[0]));
    }

    @Test
    @DisplayName("HR decision undo clears hr_decision")
    void hrDecision_undo() throws Exception {
        final long[] p = createProbationer();
        final long reviewId = createDueReview(p[1]);
        expireAndExecute(submit(reviewId, "END"));
        final String resp = mockMvc.perform(withJson(post("/api/probation-reviews/" + reviewId + "/hr-decision")
                        .with(asHR()), "{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/tasks/" + objectMapper.readTree(resp).get("taskId").asLong() + "/undo").with(asHR()))
                .andExpect(status().isOk());
        assertEquals(null, jdbc.queryForObject("SELECT hr_decision FROM hr.review WHERE id = ?", String.class, reviewId));
    }

    // ── DB constraints (DoD §2.8.3) ─────────────────────────────────────────

    @Test
    @DisplayName("DB: review_answer rating must be 1-5; hr_decision only on a PROBATION review with outcome END")
    void dbConstraints() {
        final long reviewId = createDueReview(createProbationer()[1]);
        final long questionId = jdbc.queryForObject("""
                SELECT q.id FROM hr.form_question q JOIN hr.form_section s ON s.id = q.section_id
                JOIN hr.review r ON r.form_id = s.form_id WHERE r.id = ? AND q.answer_type = 'SCALE_1_5' LIMIT 1
                """, Long.class, reviewId);

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO hr.review_answer (review_id, question_id, rating) VALUES (?, ?, 6)", reviewId, questionId));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO hr.review_answer (review_id, question_id, rating, text_answer) VALUES (?, ?, 3, 'x')",
                reviewId, questionId));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE hr.review SET outcome = 'CONFIRM', hr_decision = 'APPROVED' WHERE id = ?", reviewId));
    }

    @Test
    @DisplayName("v_contract.probation_end_date = start + probation months (ONB-01)")
    void vContract() {
        final long[] p = createProbationer();
        assertEquals("2026-11-05", jdbc.queryForObject(
                "SELECT probation_end_date::text FROM hr.v_contract WHERE id = ?", String.class, p[1]));
    }

    // ── Onboarding overdue is now a real HR task ────────────────────────────

    @Test
    @DisplayName("Onboarding overdue raises an HR engine task shown in HR's My Tasks")
    void onboardingOverdue_isTask() throws Exception {
        final String email = "ovdtask." + nextId() + "@outlook.com";
        final String resp = mockMvc.perform(withJson(post("/api/onboardings").with(asHR()),
                        validOnboardingBody(email, managerEmployeeId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        final long employeeId = objectMapper.readTree(resp).get("employeeId").asLong();
        final long onboardingId = jdbc.queryForObject("SELECT id FROM hr.onboarding WHERE employee_id = ?",
                Long.class, employeeId);

        onboardingOverdueScheduler.raiseIfOverdue(onboardingId, 3, LocalDate.parse("2027-01-08"));
        onboardingOverdueScheduler.raiseIfOverdue(onboardingId, 3, LocalDate.parse("2027-01-09"));

        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM hr.engine_task WHERE employee_id = ? AND task_name = 'Onboarding overdue'
                """, Integer.class, employeeId));
        mockMvc.perform(get("/api/tasks").with(asHR()))
                .andExpect(jsonPath("$.content[?(@.employeeId == " + employeeId + ")].title", hasItem("Onboarding overdue")))
                .andExpect(jsonPath("$.content[?(@.employeeId == " + employeeId + ")].entityType", hasItem("ENGINE_TASK")));
    }
}
