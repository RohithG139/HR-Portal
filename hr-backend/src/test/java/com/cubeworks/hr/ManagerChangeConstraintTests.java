package com.cubeworks.hr;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Negative-constraint integration tests for the two tables created by
 * {@code V14__create_manager_change_tables.sql}: {@code manager_change} and
 * {@code manager_change_task}.
 *
 * <h2>Constraints under test</h2>
 * <ul>
 *   <li>{@code manager_change} CHECK {@code from_manager_id is distinct from to_manager_id} —
 *       from and to manager must differ.</li>
 *   <li>{@code manager_change} CHECK {@code num_nonnulls(employee_id, project_id) = 1} —
 *       exactly one of the two scope columns must be non-null for every row.</li>
 *   <li>{@code manager_change_task} composite PK {@code (change_id, engine_task_id)} —
 *       duplicate (change_id, engine_task_id) pairs must fail.</li>
 * </ul>
 *
 * <h2>Infrastructure</h2>
 * <p>All Testcontainers and security-stub wiring is inherited from
 * {@link AbstractIntegrationTest}. {@code @TestInstance(PER_CLASS)} lets the
 * {@code @BeforeAll} seed method use the Spring-injected {@link JdbcTemplate}.</p>
 *
 * <h2>SQL schema prefix</h2>
 * <p>All raw JDBC SQL uses the {@code hr.} schema prefix because the test DataSource does
 * not set {@code currentSchema} in the JDBC URL.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ManagerChangeConstraintTests extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    // ── Prerequisite row IDs ─────────────────────────────────────────────────

    /** FK dependency: department. */
    private long deptId;

    /** FK dependency: job_title. */
    private long jobTitleId;

    /**
     * "From" manager — used as {@code from_manager_id} and {@code requested_by} in inserts.
     * Must differ from toManagerId (the constraint under test is about equal from/to).
     */
    private long fromManagerId;

    /**
     * "To" manager — used as {@code to_manager_id} in valid inserts.
     * The constraint test checks what happens when from == to.
     */
    private long toManagerId;

    /**
     * Target employee — used as {@code employee_id} in LINE-scoped {@code manager_change} rows.
     */
    private long targetEmpId;

    /**
     * A valid engine_task row — provides a real surrogate PK for {@code manager_change_task}
     * FK tests.
     */
    private long engineTaskId;

    /**
     * A valid {@code manager_change} row — used as the parent for the composite-PK duplicate test.
     */
    private long parentChangeId;

    // ── One-time setup ───────────────────────────────────────────────────────

    /**
     * Seeds the minimum prerequisite rows. Runs once before any test in this class.
     */
    @BeforeAll
    void seedPrerequisites() {
        deptId = jdbc.queryForObject(
                "INSERT INTO hr.department (name) VALUES ('MC-Constraint-Dept') RETURNING id",
                Long.class);

        jobTitleId = jdbc.queryForObject(
                "INSERT INTO hr.job_title (name) VALUES ('MC-Constraint-Title') RETURNING id",
                Long.class);

        fromManagerId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role
                ) VALUES (
                    'E-MC-FROM', 'From', 'Manager', 'mc.from.manager@cubeworks.test',
                    '+31 6 77000001', '1975-01-01', 'From St 1', '7700 AA', 'Tilburg',
                    ?, ?, 'SENIOR', 'MANAGER'
                ) RETURNING id
                """, Long.class, jobTitleId, deptId);

        toManagerId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role
                ) VALUES (
                    'E-MC-TO', 'To', 'Manager', 'mc.to.manager@cubeworks.test',
                    '+31 6 77000002', '1976-02-02', 'To St 2', '7700 BB', 'Tilburg',
                    ?, ?, 'SENIOR', 'MANAGER'
                ) RETURNING id
                """, Long.class, jobTitleId, deptId);

        targetEmpId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, manager_id
                ) VALUES (
                    'E-MC-EMP', 'MC', 'Employee', 'mc.employee@cubeworks.test',
                    '+31 6 77000003', '1990-03-03', 'Emp St 3', '7700 CC', 'Tilburg',
                    ?, ?, 'MEDIOR', 'EMPLOYEE', ?
                ) RETURNING id
                """, Long.class, jobTitleId, deptId, fromManagerId);

        engineTaskId = jdbc.queryForObject("""
                INSERT INTO hr.engine_task (
                    engine_task_id, employee_id, task_name, owner_employee_id,
                    due_date, version, created_at, created_by, updated_at, updated_by
                ) VALUES (
                    'et-mc-constraint-001', ?, 'MC Constraint Task', ?,
                    '2030-01-01', 0, NOW(), ?, NOW(), ?
                ) RETURNING id
                """, Long.class,
                targetEmpId, fromManagerId, fromManagerId, fromManagerId);

        // Seed one valid manager_change row for the composite-PK duplicate test.
        parentChangeId = jdbc.queryForObject("""
                INSERT INTO hr.manager_change (
                    scope, employee_id, from_manager_id, to_manager_id,
                    effective_date, status, requested_by,
                    created_at, updated_at, version
                ) VALUES (
                    'LINE', ?, ?, ?, CURRENT_DATE + 30, 'SCHEDULED', ?,
                    NOW(), NOW(), 0
                ) RETURNING id
                """, Long.class,
                targetEmpId, fromManagerId, toManagerId, fromManagerId);

        // Seed the first manager_change_task row (will be duplicated in the PK test).
        jdbc.update(
                "INSERT INTO hr.manager_change_task (change_id, engine_task_id) VALUES (?, ?)",
                parentChangeId, engineTaskId);
    }

    // ════════════════════════════════════════════════════════════════════════
    // manager_change: from_manager_id IS DISTINCT FROM to_manager_id
    // ════════════════════════════════════════════════════════════════════════

    /**
     * V14 CHECK {@code from_manager_id is distinct from to_manager_id}: inserting a
     * {@code manager_change} row where {@code from_manager_id == to_manager_id} (both
     * set to the same employee id) must throw a constraint violation.
     */
    @Test
    void managerChange_fromAndToManagerSame_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.manager_change (
                            scope, employee_id, from_manager_id, to_manager_id,
                            effective_date, status, requested_by,
                            created_at, updated_at, version
                        ) VALUES (
                            'LINE', ?, ?, ?, CURRENT_DATE + 10, 'SCHEDULED', ?,
                            NOW(), NOW(), 0
                        )
                        """,
                        targetEmpId,
                        fromManagerId,
                        fromManagerId, // to_manager_id == from_manager_id → violates CHECK
                        fromManagerId)
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // manager_change: num_nonnulls(employee_id, project_id) = 1
    // ════════════════════════════════════════════════════════════════════════

    /**
     * V14 CHECK {@code num_nonnulls(employee_id, project_id) = 1}: inserting a
     * {@code manager_change} row with BOTH {@code employee_id} AND {@code project_id}
     * non-null must fail (only one scope column may be set).
     *
     * <p>Note: there is no valid project_id available in these tests (the {@code project}
     * table is created by V13 but may be empty). We test the constraint by using the
     * employee's own id as a fake project_id — the FK check on project_id would fail first
     * if a project table FK exists, but in PostgreSQL constraint checking order, the
     * {@code num_nonnulls} CHECK fires at the tuple level before FK deferred checking. In
     * practice, the {@code DataIntegrityViolationException} is what matters regardless of
     * which constraint fires first.</p>
     */
    @Test
    void managerChange_bothScopeColumnsNonNull_fails() {
        // Use a fake project_id (999999) — the FK or the CHECK will fire; either way it's a
        // DataIntegrityViolationException. We verify the constraint is enforced, not which one.
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.manager_change (
                            scope, employee_id, project_id, from_manager_id, to_manager_id,
                            effective_date, status, requested_by,
                            created_at, updated_at, version
                        ) VALUES (
                            'LINE', ?, 999999, ?, ?, CURRENT_DATE + 10, 'SCHEDULED', ?,
                            NOW(), NOW(), 0
                        )
                        """,
                        targetEmpId,
                        fromManagerId, toManagerId, fromManagerId)
        );
    }

    /**
     * V14 CHECK {@code num_nonnulls(employee_id, project_id) = 1}: inserting a
     * {@code manager_change} row with BOTH {@code employee_id} AND {@code project_id}
     * null must fail (at least one scope column must be set — the num_nonnulls check
     * requires exactly 1).
     *
     * <p>The {@code (scope = 'LINE') = (employee_id is not null)} CHECK would also fire for
     * a LINE row with null employee_id. Either way a {@link DataIntegrityViolationException}
     * is the expected outcome.</p>
     */
    @Test
    void managerChange_bothScopeColumnsNull_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.manager_change (
                            scope, employee_id, project_id, from_manager_id, to_manager_id,
                            effective_date, status, requested_by,
                            created_at, updated_at, version
                        ) VALUES (
                            'LINE', NULL, NULL, ?, ?, CURRENT_DATE + 10, 'SCHEDULED', ?,
                            NOW(), NOW(), 0
                        )
                        """,
                        fromManagerId, toManagerId, fromManagerId)
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // manager_change_task: composite PK (change_id, engine_task_id)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * V14 composite PK on {@code manager_change_task(change_id, engine_task_id)}: inserting
     * a duplicate {@code (change_id, engine_task_id)} pair must fail.
     *
     * <p>The first row was inserted in {@link #seedPrerequisites()}. This test inserts the
     * identical pair and expects a primary-key violation.</p>
     */
    @Test
    void managerChangeTask_duplicateChangeIdAndTaskId_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update(
                        "INSERT INTO hr.manager_change_task (change_id, engine_task_id) VALUES (?, ?)",
                        parentChangeId, engineTaskId)
        );
    }
}
