package com.cubeworks.hr;

import com.cubeworks.hr.repository.workspace.ManagerChangeRepository;
import com.cubeworks.hr.service.workspace.ManagerTransferApplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for W09 ({@code GET /api/employees/{id}/manager-tasks}) and
 * W10 ({@code POST /api/employees/{id}/manager-transfers}).
 *
 * <h2>Coverage map</h2>
 * <pre>
 * AC-W02  createTransfer_withTaskIds_linksTasksToChange
 * AC-W04  createTransfer_withNullNewManager_returnsChooseManagerMessage
 * AC-W05  createTransfer_withPastDate_returnsDateInPastMessage
 * EC-2    applyChange_movesEmployeeAndTasks_leavesUntickedTaskUnchanged
 * EC-3    createTransfer_withTodayDate_appliesImmediatelyAfterCommit
 * EC-4    applyChange_onPastDateScheduledChange_appliesSuccessfully
 * W09     getManagerTasks_returnsOpenTasksOwnedByCurrentManager
 * W10     createTransfer_withFutureDate_schedulesChange
 * W10     createTransfer_selfTransfer_returns403 (G-13)
 * Fix #1  IDOR negative tests (wrong employee task, completed task, non-existent task id)
 * Fix #2  newManagerId validation (self-manager, non-MANAGER role)
 * Fix #5  Offboarding/Archived guard, duplicate-SCHEDULED guard
 * Fix #6  Invalid sort field for IT_ADMIN and HR (sort allowlist)
 * Fix #7  Page-size cap (size=99999 → clamped to 100)
 * </pre>
 *
 * <h2>Seed data</h2>
 * <p>Each transfer-creating test uses its own dedicated target employee to avoid the
 * duplicate-SCHEDULED-transfer guard (fix #5b) rejecting a second SCHEDULED transfer for
 * the same employee.  Read-only and validation tests share {@code targetEmployeeId}.</p>
 */
@DisplayName("W09/W10 — Transfer Manager")
class FRD01TransferManagerTests extends AbstractApiTest {

    // ── Autowired Spring beans for direct-apply tests ─────────────────────────

    @Autowired
    private ManagerTransferApplier managerTransferApplier;

    @Autowired
    private ManagerChangeRepository managerChangeRepository;

    // ── Per-class test data ───────────────────────────────────────────────────

    /** Target employee for read-only and validation tests (W09, AC-W04, AC-W05, G-13). */
    private long targetEmployeeId;

    /** New manager employee (MANAGER role) — used as the transfer target in all tests. */
    private long newManagerId;

    /** Open engine_task for targetEmployeeId, owned by managerEmployeeId (W09 tick-list). */
    private long openTaskId;

    // ── Separate target employees (one per transfer-creating test) ─────────────

    /** Dedicated target for the future-date scheduling test. */
    private long futureTransferTargetId;

    /** Dedicated target for the task-move test. */
    private long taskMoveTargetId;
    /** Open task for taskMoveTargetId, owned by managerEmployeeId. */
    private long taskMoveOpenTaskId;

    /** Dedicated target for EC-2 (direct applyChange test with ticked+unticked tasks). */
    private long ec2TargetId;
    /** Ticked task for ec2TargetId (will be moved). */
    private long ec2TickedTaskId;
    /** Unticked task for ec2TargetId (will NOT be moved, owner unchanged). */
    private long ec2UntickedTaskId;

    /** Dedicated target for EC-3 (same-day HTTP POST, applies immediately after commit). */
    private long ec3TargetId;

    /** Dedicated target for EC-4 (SCHEDULED with effectiveDate = yesterday, applied directly). */
    private long ec4TargetId;
    /** Ticked task for ec4TargetId. */
    private long ec4TickedTaskId;

    /** Dedicated target for the offboarding-stage guard test (fix #5a). */
    private long offboardingTargetId;

    /** Dedicated target for the duplicate-SCHEDULED-transfer guard test (fix #5b). */
    private long dupTransferTargetId;

    /** A completed engine_task for targetEmployeeId owned by managerEmployeeId (IDOR test). */
    private long completedTaskId;

    /** An engine_task belonging to a DIFFERENT employee (baseEmployeeId) — for IDOR test. */
    private long otherEmpTaskId;

    // ── Setup ─────────────────────────────────────────────────────────────────

    /**
     * Seeds all per-class test data.
     */
    @BeforeAll
    @Override
    void setUpActors() {
        super.setUpActors();

        // ── New manager employee (MANAGER role) ──────────────────────────────
        final long nmId = nextId();
        newManagerId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, idp_subject
                ) VALUES (
                    ?, 'NewMgr', 'Transfer', ?,
                    '+31 6 30000002', '1978-05-22', 'NewMgr St 2', '1000 BB', 'Amsterdam',
                    ?, ?, 'SENIOR', 'MANAGER', ?
                ) RETURNING id
                """, Long.class,
                "E-NMG-" + nmId,
                "newmgr.transfer." + nmId + "@cubeworks.test",
                developerJobTitleId, developmentDeptId, "sub-nmg-" + nmId);

        // ── Target employee (validation + W09 read tests only) ───────────────
        final long tId = nextId();
        targetEmployeeId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role,
                    manager_id
                ) VALUES (
                    ?, 'Transfer', 'Target', ?,
                    '+31 6 30000001', '1991-03-15', 'Target St 1', '1000 AA', 'Amsterdam',
                    ?, ?, 'MEDIOR', 'EMPLOYEE',
                    ?
                ) RETURNING id
                """, Long.class,
                "E-TGT-" + tId,
                "transfer.target." + tId + "@cubeworks.test",
                developerJobTitleId, developmentDeptId, managerEmployeeId);

        // Open task for W09 tick-list
        openTaskId = jdbc.queryForObject("""
                INSERT INTO hr.engine_task (
                    engine_task_id, employee_id, task_name, owner_employee_id,
                    due_date, version, created_at, created_by, updated_at, updated_by
                ) VALUES (
                    ?, ?, 'Review onboarding checklist', ?,
                    '2030-01-01', 0, NOW(), ?, NOW(), ?
                ) RETURNING id
                """, Long.class,
                "et-frd01-" + nextId(),
                targetEmployeeId, managerEmployeeId, managerEmployeeId, managerEmployeeId);

        // Completed task for IDOR test — targetEmployeeId, owned by managerEmployeeId, but done
        completedTaskId = jdbc.queryForObject("""
                INSERT INTO hr.engine_task (
                    engine_task_id, employee_id, task_name, owner_employee_id,
                    due_date, completed_at, version, created_at, created_by, updated_at, updated_by
                ) VALUES (
                    ?, ?, 'Completed Task', ?,
                    '2030-01-01', NOW(), 0, NOW(), ?, NOW(), ?
                ) RETURNING id
                """, Long.class,
                "et-completed-" + nextId(),
                targetEmployeeId, managerEmployeeId, managerEmployeeId, managerEmployeeId);

        // Task for a DIFFERENT employee (baseEmployeeId) — for IDOR test (wrong employee)
        otherEmpTaskId = jdbc.queryForObject("""
                INSERT INTO hr.engine_task (
                    engine_task_id, employee_id, task_name, owner_employee_id,
                    due_date, version, created_at, created_by, updated_at, updated_by
                ) VALUES (
                    ?, ?, 'Other Employee Task', ?,
                    '2030-01-01', 0, NOW(), ?, NOW(), ?
                ) RETURNING id
                """, Long.class,
                "et-other-" + nextId(),
                baseEmployeeId, managerEmployeeId, managerEmployeeId, managerEmployeeId);

        // ── Dedicated employee for future-date scheduling test ───────────────
        final long ft = nextId();
        futureTransferTargetId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, manager_id
                ) VALUES (
                    ?, 'Future', 'Target', ?,
                    '+31 6 40000001', '1993-04-01', 'Future Ln 1', '2000 AA', 'Rotterdam',
                    ?, ?, 'JUNIOR', 'EMPLOYEE', ?
                ) RETURNING id
                """, Long.class,
                "E-FTR-" + ft,
                "future.target." + ft + "@cubeworks.test",
                developerJobTitleId, developmentDeptId, managerEmployeeId);

        // ── Dedicated employee + task for task-move test ─────────────────────
        final long tm = nextId();
        taskMoveTargetId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, manager_id
                ) VALUES (
                    ?, 'TaskMove', 'Target', ?,
                    '+31 6 40000002', '1994-05-01', 'Task Ln 2', '2000 BB', 'Rotterdam',
                    ?, ?, 'JUNIOR', 'EMPLOYEE', ?
                ) RETURNING id
                """, Long.class,
                "E-TMT-" + tm,
                "taskmove.target." + tm + "@cubeworks.test",
                developerJobTitleId, developmentDeptId, managerEmployeeId);

        taskMoveOpenTaskId = jdbc.queryForObject("""
                INSERT INTO hr.engine_task (
                    engine_task_id, employee_id, task_name, owner_employee_id,
                    due_date, version, created_at, created_by, updated_at, updated_by
                ) VALUES (
                    ?, ?, 'Move candidate task', ?,
                    '2030-06-01', 0, NOW(), ?, NOW(), ?
                ) RETURNING id
                """, Long.class,
                "et-taskmove-" + nextId(),
                taskMoveTargetId, managerEmployeeId, managerEmployeeId, managerEmployeeId);

        // ── Dedicated employee + tasks for EC-2 (direct apply test) ─────────
        final long e2 = nextId();
        ec2TargetId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, manager_id
                ) VALUES (
                    ?, 'EC2', 'Target', ?,
                    '+31 6 50000001', '1995-06-01', 'EC2 Ln 1', '3000 AA', 'Utrecht',
                    ?, ?, 'MEDIOR', 'EMPLOYEE', ?
                ) RETURNING id
                """, Long.class,
                "E-EC2-" + e2,
                "ec2.target." + e2 + "@cubeworks.test",
                developerJobTitleId, developmentDeptId, managerEmployeeId);

        ec2TickedTaskId = jdbc.queryForObject("""
                INSERT INTO hr.engine_task (
                    engine_task_id, employee_id, task_name, owner_employee_id,
                    due_date, version, created_at, created_by, updated_at, updated_by
                ) VALUES (
                    ?, ?, 'EC2 ticked task', ?,
                    '2030-02-01', 0, NOW(), ?, NOW(), ?
                ) RETURNING id
                """, Long.class,
                "et-ec2-ticked-" + nextId(),
                ec2TargetId, managerEmployeeId, managerEmployeeId, managerEmployeeId);

        ec2UntickedTaskId = jdbc.queryForObject("""
                INSERT INTO hr.engine_task (
                    engine_task_id, employee_id, task_name, owner_employee_id,
                    due_date, version, created_at, created_by, updated_at, updated_by
                ) VALUES (
                    ?, ?, 'EC2 unticked task', ?,
                    '2030-03-01', 0, NOW(), ?, NOW(), ?
                ) RETURNING id
                """, Long.class,
                "et-ec2-unticked-" + nextId(),
                ec2TargetId, managerEmployeeId, managerEmployeeId, managerEmployeeId);

        // ── Dedicated employee for EC-3 (same-day HTTP POST) ─────────────────
        final long e3 = nextId();
        ec3TargetId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, manager_id
                ) VALUES (
                    ?, 'EC3', 'Target', ?,
                    '+31 6 50000002', '1996-07-01', 'EC3 Ln 2', '3000 BB', 'Utrecht',
                    ?, ?, 'JUNIOR', 'EMPLOYEE', ?
                ) RETURNING id
                """, Long.class,
                "E-EC3-" + e3,
                "ec3.target." + e3 + "@cubeworks.test",
                developerJobTitleId, developmentDeptId, managerEmployeeId);

        // ── Dedicated employee + task for EC-4 (past effectiveDate, direct apply) ──
        final long e4 = nextId();
        ec4TargetId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, manager_id
                ) VALUES (
                    ?, 'EC4', 'Target', ?,
                    '+31 6 50000003', '1997-08-01', 'EC4 Ln 3', '3000 CC', 'Utrecht',
                    ?, ?, 'MEDIOR', 'EMPLOYEE', ?
                ) RETURNING id
                """, Long.class,
                "E-EC4-" + e4,
                "ec4.target." + e4 + "@cubeworks.test",
                developerJobTitleId, developmentDeptId, managerEmployeeId);

        ec4TickedTaskId = jdbc.queryForObject("""
                INSERT INTO hr.engine_task (
                    engine_task_id, employee_id, task_name, owner_employee_id,
                    due_date, version, created_at, created_by, updated_at, updated_by
                ) VALUES (
                    ?, ?, 'EC4 ticked task', ?,
                    '2030-04-01', 0, NOW(), ?, NOW(), ?
                ) RETURNING id
                """, Long.class,
                "et-ec4-ticked-" + nextId(),
                ec4TargetId, managerEmployeeId, managerEmployeeId, managerEmployeeId);

        // ── Dedicated employee for offboarding guard test (fix #5a) ──────────
        final long ob = nextId();
        offboardingTargetId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, manager_id, stage
                ) VALUES (
                    ?, 'Offboarding', 'Target', ?,
                    '+31 6 60000001', '1988-01-01', 'Offboard Ln 1', '4000 AA', 'Eindhoven',
                    ?, ?, 'SENIOR', 'EMPLOYEE', ?, 'OFFBOARDING'
                ) RETURNING id
                """, Long.class,
                "E-OBD-" + ob,
                "offboarding.target." + ob + "@cubeworks.test",
                developerJobTitleId, developmentDeptId, managerEmployeeId);

        // ── Dedicated employee for duplicate SCHEDULED guard test (fix #5b) ──
        final long dp = nextId();
        dupTransferTargetId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, manager_id
                ) VALUES (
                    ?, 'Dup', 'Target', ?,
                    '+31 6 60000002', '1989-02-02', 'Dup Ln 2', '4000 BB', 'Eindhoven',
                    ?, ?, 'JUNIOR', 'EMPLOYEE', ?
                ) RETURNING id
                """, Long.class,
                "E-DUP-" + dp,
                "dup.target." + dp + "@cubeworks.test",
                developerJobTitleId, developmentDeptId, managerEmployeeId);
    }

    // ── W09: Manager tasks tick-list ─────────────────────────────────────────

    /**
     * W09: HR calling GET /api/employees/{id}/manager-tasks for an employee who has an open
     * task owned by their current manager receives a non-empty tick-list.
     */
    @Test
    @DisplayName("W09: GET /api/employees/{id}/manager-tasks as HR → returns open task")
    void getManagerTasks_returnsOpenTasksOwnedByCurrentManager() throws Exception {
        mockMvc.perform(get("/api/employees/{id}/manager-tasks", targetEmployeeId)
                        .with(asHR()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openManagerTasks").isArray())
                .andExpect(jsonPath("$.openManagerTasks[0].id").value(openTaskId))
                .andExpect(jsonPath("$.openManagerTasks[0].taskName")
                        .value("Review onboarding checklist"));
    }

    // ── W10: Future-date transfer — schedule path ──────────────────────────

    /**
     * W10: a transfer with a future effective date creates a SCHEDULED manager_change row.
     * Returns 201 with the new changeId.
     * Uses a dedicated target employee to avoid the duplicate-SCHEDULED guard (fix #5b).
     */
    @Test
    @DisplayName("W10: POST manager-transfers with future date → 201 with changeId")
    void createTransfer_withFutureDate_schedulesChange() throws Exception {
        final String futureDate = LocalDate.now().plusDays(30).toString();
        final String body = """
                {
                  "newManagerId": %d,
                  "effectiveDate": "%s",
                  "reason": "Org restructure",
                  "taskIdsToMove": []
                }
                """.formatted(newManagerId, futureDate);

        mockMvc.perform(withJson(
                        post("/api/employees/{id}/manager-transfers", futureTransferTargetId)
                                .with(asHR()), body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.changeId").isNumber());
    }

    // ── AC-W02: ticked tasks are linked to the change ─────────────────────────

    /**
     * AC-W02: when a task is ticked in the modal ({@code taskIdsToMove}), the resulting
     * manager_change_task row links the change to that task — verified by checking the
     * DB row count after the POST.  Uses a dedicated target employee to avoid the
     * duplicate-SCHEDULED guard (fix #5b).
     */
    @Test
    @DisplayName("AC-W02: POST with taskIdsToMove → DB contains manager_change_task row")
    void createTransfer_withTaskIds_linksTasksToChange_AC_W02() throws Exception {
        final String futureDate = LocalDate.now().plusDays(60).toString();
        final String body = """
                {
                  "newManagerId": %d,
                  "effectiveDate": "%s",
                  "reason": "Task move test",
                  "taskIdsToMove": [%d]
                }
                """.formatted(newManagerId, futureDate, taskMoveOpenTaskId);

        final String responseJson = mockMvc.perform(withJson(
                        post("/api/employees/{id}/manager-transfers", taskMoveTargetId)
                                .with(asHR()), body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        final long changeId = Long.parseLong(
                responseJson.replaceAll(".*\"changeId\":(\\d+).*", "$1"));

        final int taskLinkCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr.manager_change_task WHERE change_id = ? AND engine_task_id = ?",
                Integer.class, changeId, taskMoveOpenTaskId);
        assertThat(taskLinkCount).isEqualTo(1);
    }

    // ── AC-W04: "Choose a new manager." ──────────────────────────────────────

    /**
     * AC-W04: a transfer request without {@code newManagerId} returns 400 with the exact
     * validation message "Choose a new manager." (R-SRC-3).
     */
    @Test
    @DisplayName("AC-W04: POST without newManagerId → 400 'Choose a new manager.'")
    void createTransfer_withNullNewManager_returnsChooseManagerMessage_AC_W04() throws Exception {
        final String body = """
                {
                  "effectiveDate": "%s",
                  "reason": "Test"
                }
                """.formatted(LocalDate.now().plusDays(1));

        mockMvc.perform(withJson(
                        post("/api/employees/{id}/manager-transfers", targetEmployeeId)
                                .with(asHR()), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='newManagerId')].message")
                        .value(hasItem("Choose a new manager.")));
    }

    // ── AC-W05: "The effective date cannot be in the past." ──────────────────

    /**
     * AC-W05: a transfer request with a past effective date returns 400 with the exact
     * validation message "The effective date cannot be in the past." (R-SRC-3).
     */
    @Test
    @DisplayName("AC-W05: POST with past effectiveDate → 400 'The effective date cannot be in the past.'")
    void createTransfer_withPastDate_returnsDateInPastMessage_AC_W05() throws Exception {
        final String body = """
                {
                  "newManagerId": %d,
                  "effectiveDate": "2020-01-01",
                  "reason": "Test"
                }
                """.formatted(newManagerId);

        mockMvc.perform(withJson(
                        post("/api/employees/{id}/manager-transfers", targetEmployeeId)
                                .with(asHR()), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='effectiveDate')].message")
                        .value(hasItem("The effective date cannot be in the past.")));
    }

    // ── G-13: HR cannot act on their own record ───────────────────────────────

    /**
     * G-13: HR calling the transfer endpoint with their own employeeId (self-action) receives
     * 403 FORBIDDEN. The service guards against this via {@code SelfActionGuard.forbidSelf()}.
     */
    @Test
    @DisplayName("G-13: POST /api/employees/{hrId}/manager-transfers → 403 (self-action)")
    void createTransfer_selfAction_returns403() throws Exception {
        final String body = """
                {
                  "newManagerId": %d,
                  "effectiveDate": "%s",
                  "reason": "Self-action test"
                }
                """.formatted(newManagerId, LocalDate.now().plusDays(1));

        mockMvc.perform(withJson(
                        post("/api/employees/{id}/manager-transfers", hrEmployeeId)
                                .with(asHR()), body))
                .andExpect(status().isForbidden());
    }

    // ── EC-2: applyChange moves employee and ticked tasks, leaves unticked intact ──

    /**
     * EC-2: Seeding a SCHEDULED {@code ManagerChange} directly (bypassing the HTTP endpoint)
     * and then calling {@code ManagerTransferApplier.applyChange()} directly verifies that:
     * <ul>
     *   <li>the employee's {@code manager_id} is updated to the new manager</li>
     *   <li>the ticked task's {@code owner_employee_id} is updated to the new manager</li>
     *   <li>the unticked task's {@code owner_employee_id} is UNCHANGED (still the old manager)</li>
     * </ul>
     * This exercises the apply logic directly, independent of the HTTP/scheduling path.
     */
    @Test
    @DisplayName("EC-2: applyChange moves employee + ticked task, leaves unticked task unchanged")
    void applyChange_movesEmployeeAndTickedTask_leavesUntickedTaskUnchanged_EC2() {
        // Seed the SCHEDULED manager_change row directly — bypasses the HTTP validation so
        // we can seed deterministic data without worrying about duplicate guards.
        final long changeId = jdbc.queryForObject("""
                INSERT INTO hr.manager_change (
                    scope, employee_id, from_manager_id, to_manager_id,
                    effective_date, status, requested_by,
                    created_at, updated_at, version
                ) VALUES (
                    'LINE', ?, ?, ?, CURRENT_DATE + 1, 'SCHEDULED', ?,
                    NOW(), NOW(), 0
                ) RETURNING id
                """, Long.class,
                ec2TargetId, managerEmployeeId, newManagerId, hrEmployeeId);

        // Tick only ec2TickedTaskId — ec2UntickedTaskId remains un-ticked.
        jdbc.update(
                "INSERT INTO hr.manager_change_task (change_id, engine_task_id) VALUES (?, ?)",
                changeId, ec2TickedTaskId);

        // Apply the change directly via the Spring bean.
        managerTransferApplier.applyChange(changeId);

        // Assert employee's manager_id is now newManagerId.
        final Long actualManagerId = jdbc.queryForObject(
                "SELECT manager_id FROM hr.employee WHERE id = ?", Long.class, ec2TargetId);
        assertThat(actualManagerId).isEqualTo(newManagerId);

        // Assert ticked task's owner_employee_id is now newManagerId.
        final Long tickedOwner = jdbc.queryForObject(
                "SELECT owner_employee_id FROM hr.engine_task WHERE id = ?",
                Long.class, ec2TickedTaskId);
        assertThat(tickedOwner).isEqualTo(newManagerId);

        // Assert unticked task's owner_employee_id is still managerEmployeeId (unchanged).
        final Long untickedOwner = jdbc.queryForObject(
                "SELECT owner_employee_id FROM hr.engine_task WHERE id = ?",
                Long.class, ec2UntickedTaskId);
        assertThat(untickedOwner).isEqualTo(managerEmployeeId);

        // Assert the manager_change status is now APPLIED.
        final String status = jdbc.queryForObject(
                "SELECT status FROM hr.manager_change WHERE id = ?", String.class, changeId);
        assertThat(status).isEqualTo("APPLIED");
    }

    // ── EC-3: same-day transfer applies immediately after the HTTP commit ─────

    /**
     * EC-3: POSTing a transfer with {@code effectiveDate = today} triggers the
     * {@code afterCommit()} hook, which calls {@code ManagerTransferApplier.applyChange()}
     * in a {@code REQUIRES_NEW} transaction. By the time the 201 response is received,
     * the apply transaction has committed. The DB must show:
     * <ul>
     *   <li>{@code employee.manager_id} updated to the new manager</li>
     *   <li>{@code manager_change.status = 'APPLIED'}</li>
     * </ul>
     * Without the {@code REQUIRES_NEW} fix (fix #3), the apply writes would be silently
     * lost and the status would still be {@code SCHEDULED}.
     */
    @Test
    @DisplayName("EC-3: POST with effectiveDate=today → employee manager updated + status=APPLIED in DB")
    void createTransfer_withTodayDate_appliesImmediatelyAfterCommit_EC3() throws Exception {
        final String today = LocalDate.now().toString();
        final String body = """
                {
                  "newManagerId": %d,
                  "effectiveDate": "%s",
                  "reason": "Same-day transfer",
                  "taskIdsToMove": []
                }
                """.formatted(newManagerId, today);

        final String responseJson = mockMvc.perform(withJson(
                        post("/api/employees/{id}/manager-transfers", ec3TargetId)
                                .with(asHR()), body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.changeId").isNumber())
                .andReturn().getResponse().getContentAsString();

        final long changeId = Long.parseLong(
                responseJson.replaceAll(".*\"changeId\":(\\d+).*", "$1"));

        // Verify the employee's manager_id is already updated (afterCommit ran synchronously).
        final Long actualManagerId = jdbc.queryForObject(
                "SELECT manager_id FROM hr.employee WHERE id = ?", Long.class, ec3TargetId);
        assertThat(actualManagerId).isEqualTo(newManagerId);

        // Verify status is APPLIED, not SCHEDULED.
        final String status = jdbc.queryForObject(
                "SELECT status FROM hr.manager_change WHERE id = ?", String.class, changeId);
        assertThat(status).isEqualTo("APPLIED");
    }

    // ── EC-4: past-date scheduled change applied via direct applyChange call ──

    /**
     * EC-4: Seeding a SCHEDULED {@code ManagerChange} with {@code effectiveDate = yesterday}
     * (simulating a transfer that was scheduled but the scheduler hasn't run yet) and calling
     * {@code applyChange()} directly (as the scheduler would) must produce the same result
     * as EC-2: employee's manager updated, ticked task reassigned, status = APPLIED.
     */
    @Test
    @DisplayName("EC-4: SCHEDULED change with past effectiveDate → applies correctly via direct applyChange")
    void applyChange_onPastDateScheduledChange_appliesSuccessfully_EC4() {
        // Seed SCHEDULED change with effectiveDate = yesterday.
        final long changeId = jdbc.queryForObject("""
                INSERT INTO hr.manager_change (
                    scope, employee_id, from_manager_id, to_manager_id,
                    effective_date, status, requested_by,
                    created_at, updated_at, version
                ) VALUES (
                    'LINE', ?, ?, ?, CURRENT_DATE - 1, 'SCHEDULED', ?,
                    NOW(), NOW(), 0
                ) RETURNING id
                """, Long.class,
                ec4TargetId, managerEmployeeId, newManagerId, hrEmployeeId);

        jdbc.update(
                "INSERT INTO hr.manager_change_task (change_id, engine_task_id) VALUES (?, ?)",
                changeId, ec4TickedTaskId);

        managerTransferApplier.applyChange(changeId);

        final Long actualManagerId = jdbc.queryForObject(
                "SELECT manager_id FROM hr.employee WHERE id = ?", Long.class, ec4TargetId);
        assertThat(actualManagerId).isEqualTo(newManagerId);

        final Long tickedOwner = jdbc.queryForObject(
                "SELECT owner_employee_id FROM hr.engine_task WHERE id = ?",
                Long.class, ec4TickedTaskId);
        assertThat(tickedOwner).isEqualTo(newManagerId);

        final String status = jdbc.queryForObject(
                "SELECT status FROM hr.manager_change WHERE id = ?", String.class, changeId);
        assertThat(status).isEqualTo("APPLIED");
    }

    // ── Fix #1: IDOR negative tests for taskIdsToMove ────────────────────────

    /**
     * Fix #1 IDOR: sending a task id that belongs to a DIFFERENT employee's case must
     * return 400 VALIDATION_FAILED with field "taskIdsToMove" — not silently succeed
     * or 500 from a DB constraint.
     */
    @Test
    @DisplayName("Fix #1 IDOR: taskIdsToMove with other employee's task id → 400 VALIDATION_FAILED")
    void createTransfer_withOtherEmployeeTaskId_returns400() throws Exception {
        final String body = """
                {
                  "newManagerId": %d,
                  "effectiveDate": "%s",
                  "reason": "IDOR test",
                  "taskIdsToMove": [%d]
                }
                """.formatted(newManagerId, LocalDate.now().plusDays(10).toString(), otherEmpTaskId);

        mockMvc.perform(withJson(
                        post("/api/employees/{id}/manager-transfers", targetEmployeeId)
                                .with(asHR()), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='taskIdsToMove')].message")
                        .value(hasItem("One or more selected tasks are no longer available to move.")));
    }

    /**
     * Fix #1 IDOR: sending an already-completed task's id must return 400 — the task is
     * no longer in the open-tasks allowlist (completed tasks are excluded by the W09 query).
     */
    @Test
    @DisplayName("Fix #1 IDOR: taskIdsToMove with completed task id → 400 VALIDATION_FAILED")
    void createTransfer_withCompletedTaskId_returns400() throws Exception {
        final String body = """
                {
                  "newManagerId": %d,
                  "effectiveDate": "%s",
                  "reason": "Completed task IDOR test",
                  "taskIdsToMove": [%d]
                }
                """.formatted(newManagerId, LocalDate.now().plusDays(10).toString(), completedTaskId);

        mockMvc.perform(withJson(
                        post("/api/employees/{id}/manager-transfers", targetEmployeeId)
                                .with(asHR()), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='taskIdsToMove')].message")
                        .value(hasItem("One or more selected tasks are no longer available to move.")));
    }

    /**
     * Fix #1 IDOR: sending a non-existent task id must return 400 — it is not in the
     * allowlist. Must NOT produce a 500 from a DB FK constraint (the validation runs before
     * any INSERT).
     */
    @Test
    @DisplayName("Fix #1 IDOR: taskIdsToMove with non-existent task id → 400 VALIDATION_FAILED (not 500)")
    void createTransfer_withNonExistentTaskId_returns400NotServerError() throws Exception {
        final long nonExistentId = 9_999_999_999L;
        final String body = """
                {
                  "newManagerId": %d,
                  "effectiveDate": "%s",
                  "reason": "Non-existent task IDOR test",
                  "taskIdsToMove": [%d]
                }
                """.formatted(newManagerId, LocalDate.now().plusDays(10).toString(), nonExistentId);

        mockMvc.perform(withJson(
                        post("/api/employees/{id}/manager-transfers", targetEmployeeId)
                                .with(asHR()), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='taskIdsToMove')].message")
                        .value(hasItem("One or more selected tasks are no longer available to move.")));
    }

    // ── Fix #2: newManagerId validation (self-manager, wrong role) ────────────

    /**
     * Fix #2: supplying the employee's own id as {@code newManagerId} (self-manager) must
     * return 400 with "Choose a new manager." — an employee cannot be their own manager.
     */
    @Test
    @DisplayName("Fix #2: newManagerId == employeeId (self-manager) → 400 'Choose a new manager.'")
    void createTransfer_selfManager_returns400() throws Exception {
        final String body = """
                {
                  "newManagerId": %d,
                  "effectiveDate": "%s",
                  "reason": "Self-manager test"
                }
                """.formatted(targetEmployeeId, LocalDate.now().plusDays(1).toString());

        mockMvc.perform(withJson(
                        post("/api/employees/{id}/manager-transfers", targetEmployeeId)
                                .with(asHR()), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='newManagerId')].message")
                        .value(hasItem("Choose a new manager.")));
    }

    /**
     * Fix #2: supplying a non-MANAGER employee's id as {@code newManagerId} must return 400
     * with "Choose a new manager." — the new manager must actually have the MANAGER role.
     * Uses the seeded IT admin actor (IT_ADMIN role).
     */
    @Test
    @DisplayName("Fix #2: newManagerId has IT_ADMIN role → 400 'Choose a new manager.'")
    void createTransfer_newManagerIsItAdmin_returns400() throws Exception {
        final String body = """
                {
                  "newManagerId": %d,
                  "effectiveDate": "%s",
                  "reason": "Wrong role test"
                }
                """.formatted(itEmployeeId, LocalDate.now().plusDays(1).toString());

        mockMvc.perform(withJson(
                        post("/api/employees/{id}/manager-transfers", targetEmployeeId)
                                .with(asHR()), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='newManagerId')].message")
                        .value(hasItem("Choose a new manager.")));
    }

    /**
     * Fix #2: supplying a regular EMPLOYEE's id as {@code newManagerId} must return 400
     * with "Choose a new manager." — only MANAGER-role employees are valid targets.
     */
    @Test
    @DisplayName("Fix #2: newManagerId has EMPLOYEE role → 400 'Choose a new manager.'")
    void createTransfer_newManagerIsEmployee_returns400() throws Exception {
        final String body = """
                {
                  "newManagerId": %d,
                  "effectiveDate": "%s",
                  "reason": "Wrong role employee test"
                }
                """.formatted(baseEmployeeId, LocalDate.now().plusDays(1).toString());

        mockMvc.perform(withJson(
                        post("/api/employees/{id}/manager-transfers", targetEmployeeId)
                                .with(asHR()), body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='newManagerId')].message")
                        .value(hasItem("Choose a new manager.")));
    }

    // ── Fix #5a: offboarding/archived employee guard ──────────────────────────

    /**
     * Fix #5a: attempting to create a manager transfer for an employee whose
     * {@code stage = 'OFFBOARDING'} must return 409 CONFLICT — transfers are blocked
     * for employees in terminal or near-terminal stages.
     */
    @Test
    @DisplayName("Fix #5a: POST for OFFBOARDING employee → 409 CONFLICT")
    void createTransfer_offboardingEmployee_returns409() throws Exception {
        final String body = """
                {
                  "newManagerId": %d,
                  "effectiveDate": "%s",
                  "reason": "Offboarding test"
                }
                """.formatted(newManagerId, LocalDate.now().plusDays(1).toString());

        mockMvc.perform(withJson(
                        post("/api/employees/{id}/manager-transfers", offboardingTargetId)
                                .with(asHR()), body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    // ── Fix #5b: duplicate SCHEDULED transfer guard ───────────────────────────

    /**
     * Fix #5b: creating a second SCHEDULED transfer for the same employee while one is
     * already SCHEDULED must return 409 CONFLICT. The first transfer (future date) is
     * created via HTTP and left as SCHEDULED; the second attempt must be rejected.
     */
    @Test
    @DisplayName("Fix #5b: second SCHEDULED transfer for same employee → 409 CONFLICT")
    void createTransfer_duplicateScheduled_returns409() throws Exception {
        final String futureDate = LocalDate.now().plusDays(45).toString();
        final String firstBody = """
                {
                  "newManagerId": %d,
                  "effectiveDate": "%s",
                  "reason": "First scheduled transfer"
                }
                """.formatted(newManagerId, futureDate);

        // Create the first SCHEDULED transfer — must succeed.
        mockMvc.perform(withJson(
                        post("/api/employees/{id}/manager-transfers", dupTransferTargetId)
                                .with(asHR()), firstBody))
                .andExpect(status().isCreated());

        // Attempt to create a second SCHEDULED transfer — must be rejected.
        final String secondBody = """
                {
                  "newManagerId": %d,
                  "effectiveDate": "%s",
                  "reason": "Second scheduled transfer — should fail"
                }
                """.formatted(newManagerId, LocalDate.now().plusDays(50).toString());

        mockMvc.perform(withJson(
                        post("/api/employees/{id}/manager-transfers", dupTransferTargetId)
                                .with(asHR()), secondBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    // ── Fix #6: sort allowlist tests ──────────────────────────────────────────

    /**
     * Fix #6: GET /api/employees?sort=dateOfBirth as IT_ADMIN must return 400 VALIDATION_FAILED
     * with field "sort" — dateOfBirth is not in the IT_ADMIN sort allowlist.
     */
    @Test
    @DisplayName("Fix #6: GET /api/employees?sort=dateOfBirth as IT_ADMIN → 400 invalid sort")
    void listEmployees_itAdminInvalidSort_returns400() throws Exception {
        mockMvc.perform(get("/api/employees").param("sort", "dateOfBirth").with(asIT()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='sort')].message")
                        .value(hasItem("Invalid sort field.")));
    }

    /**
     * Fix #6: GET /api/employees?sort=dateOfBirth as HR must NOT be rejected — HR's existing
     * sort behaviour is unchanged (the allowlist only applies to IT_ADMIN). This test explicitly
     * confirms no regression.
     */
    @Test
    @DisplayName("Fix #6: GET /api/employees?sort=dateOfBirth as HR → 200 (HR sort is unrestricted)")
    void listEmployees_hrArbitrarySort_isNotRejected() throws Exception {
        mockMvc.perform(get("/api/employees").param("sort", "dateOfBirth").with(asHR()))
                .andExpect(status().isOk());
    }

    // ── Fix #7: page-size cap test ────────────────────────────────────────────

    /**
     * Fix #7: GET /api/employees?size=99999 must return a response whose page metadata
     * reflects a clamped size of 100 (not 99999). Tests that the controller clamp is
     * effective — without it a huge size would load the entire table.
     */
    @Test
    @DisplayName("Fix #7: GET /api/employees?size=99999 as HR → response size clamped to 100")
    void listEmployees_hugeSizeClamped_to100() throws Exception {
        mockMvc.perform(get("/api/employees").param("size", "99999").with(asHR()))
                .andExpect(status().isOk())
                // The EmployeePageDto.size field reflects the clamped value passed to the service.
                .andExpect(jsonPath("$.size").value(100));
    }
}
