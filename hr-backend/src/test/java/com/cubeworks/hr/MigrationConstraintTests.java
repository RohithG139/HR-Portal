package com.cubeworks.hr;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Negative-constraint integration tests for the 17 tables created by {@code V1__init.sql}.
 *
 * <h2>Scope</h2>
 * <p>Exactly one test method per CHECK constraint and per unique index that is declared in
 * the migration, covering the 13 tables that actually carry such constraints:</p>
 * <ul>
 *   <li>{@code department} — unique name</li>
 *   <li>{@code job_title} — unique name</li>
 *   <li>{@code employee} — unique codes / email, self-reference CHECKs, enum CHECKs</li>
 *   <li>{@code contract} — type/end-date CHECKs, hours/probation CHECKs, partial unique index</li>
 *   <li>{@code document} — unique storage_key, size_bytes &gt; 0, doc_type CHECK</li>
 *   <li>{@code checklist_template} — unique name, kind CHECK</li>
 *   <li>{@code checklist_template_item} — section / owner_role / item_kind CHECKs, policy_document_id CHECK</li>
 *   <li>{@code email_template} — unique code / name, at-least-one-recipient CHECK</li>
 *   <li>{@code setting} — category / value_unit / value_text CHECKs, can_disable/enabled CHECK,
 *       TASK_DUE days CHECK, PASSWORD_MIN_LENGTH range CHECK, KPI_CYCLE text CHECK</li>
 *   <li>{@code onboarding} — unique employee_id, id_checked/contract_signed_received CHECK,
 *       it_status details CHECK, status/completed_at CHECK</li>
 *   <li>{@code asset} — unique asset_number, type/status CHECKs, employee_id-for-issued CHECK,
 *       return_condition-for-returned CHECK</li>
 *   <li>{@code checklist_item} — phase / status CHECKs, DONE ↔ done_at/done_by CHECK</li>
 *   <li>{@code engine_task} — pending_status CHECK, pending_output presence CHECK, pending_by CHECK</li>
 *   <li>{@code email_outbox} — status CHECK, FK to {@code email_template.code}</li>
 * </ul>
 *
 * <p>Tables with no CHECK constraints or unique indexes ({@code onboarding_draft},
 * {@code notification}, {@code audit_log}) are covered implicitly by
 * {@link HrApplicationTests#contextLoads()}, which proves Flyway applies the full migration
 * from a clean database.</p>
 *
 * <h2>Clean-migration note</h2>
 * <p>{@link HrApplicationTests#contextLoads()} IS the clean-migration test: Spring Boot starts
 * Flyway as part of context load; any migration error (syntax, duplicate object, constraint
 * violation in seeded data) surfaces there before a single test method runs. Because
 * {@link AbstractIntegrationTest} shares the container and Spring Test caches the context,
 * {@code contextLoads()} and these constraint tests use the same already-migrated schema.</p>
 *
 * <h2>Infrastructure</h2>
 * <p>All Testcontainers and security-stub wiring is inherited from
 * {@link AbstractIntegrationTest}. {@code @TestInstance(PER_CLASS)} lets
 * {@link #seedPrerequisites()} be a non-static {@code @BeforeAll} method that uses
 * the Spring-injected {@link JdbcTemplate} — Spring Test injects {@code @Autowired} fields
 * before any lifecycle callbacks run.</p>
 *
 * <h2>SQL schema prefix</h2>
 * <p>All raw JDBC SQL uses the {@code hr.} schema prefix because the test DataSource does
 * not set {@code currentSchema} in the JDBC URL. Flyway and Hibernate use the schema via
 * their own settings ({@code schemas: hr} / {@code hibernate.default_schema: hr}) which do
 * not affect raw JDBC connections.</p>
 *
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MigrationConstraintTests extends AbstractIntegrationTest {

    /** Spring-injected JDBC template — available from {@code @BeforeAll} onward. */
    @Autowired
    private JdbcTemplate jdbc;

    // ── Prerequisite row IDs (set once by seedPrerequisites) ────────────────

    /** Primary department used as FK in employee inserts. */
    private long deptId;

    /** Primary job title used as FK in employee inserts. */
    private long jobTitleId;

    /**
     * Primary employee — used as FK for document, checklist_item, engine_task tests.
     * All contract / employee self-reference tests target this row.
     */
    private long employeeId;

    /**
     * Second employee — used to avoid conflicts with employeeId in tests that perform
     * a valid INSERT followed by a violating duplicate INSERT.
     */
    private long employee2Id;

    /** Checklist template used as FK for onboarding and checklist_template_item tests. */
    private long templateId;

    // (No separate asset ID needed — asset.employee_id references employee,
    //  so the returned-without-condition test uses employeeId directly.)

    /** Code of the seed email template used as FK for email_outbox tests. */
    private static final String EMAIL_TEMPLATE_CODE = "CONSTRAINT_TEST_WELCOME";

    // ── One-time setup ───────────────────────────────────────────────────────

    /**
     * Seeds the minimum prerequisite rows into the live PostgreSQL schema.
     *
     * <p>This runs once before any test method in this class. Every INSERT is in
     * auto-commit mode: rows persist for the lifetime of the Testcontainers instance
     * (the container is not reset between test methods).</p>
     */
    @BeforeAll
    void seedPrerequisites() {
        deptId = jdbc.queryForObject(
                "INSERT INTO hr.department (name) VALUES ('Engineering') RETURNING id",
                Long.class);

        jobTitleId = jdbc.queryForObject(
                "INSERT INTO hr.job_title (name) VALUES ('Software Engineer') RETURNING id",
                Long.class);

        // email_template must exist before any email_outbox row (FK on code).
        jdbc.update("""
                INSERT INTO hr.email_template (code, name, trigger_description, subject, body, to_employee)
                VALUES (?, 'Constraint Test Welcome', 'Onboarding invite', 'Welcome!', 'Body text', true)
                """, EMAIL_TEMPLATE_CODE);

        employeeId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role
                ) VALUES (
                    'E-0001', 'John', 'Doe', 'john.doe@cubeworks.test',
                    '+1234567890', '1990-01-15', '1 Main Street', '12345', 'Amsterdam',
                    ?, ?, 'MEDIOR', 'EMPLOYEE'
                ) RETURNING id
                """, Long.class, jobTitleId, deptId);

        employee2Id = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role
                ) VALUES (
                    'E-0002', 'Jane', 'Smith', 'jane.smith@cubeworks.test',
                    '+0987654321', '1992-06-20', '2 Side Street', '67890', 'Rotterdam',
                    ?, ?, 'JUNIOR', 'HR'
                ) RETURNING id
                """, Long.class, jobTitleId, deptId);

        templateId = jdbc.queryForObject("""
                INSERT INTO hr.checklist_template (name, kind)
                VALUES ('Standard Onboarding', 'ONBOARDING')
                RETURNING id
                """, Long.class);

        // Seed one asset so the asset_duplicateAssetNumber_fails test has a pre-existing row.
        jdbc.update("""
                INSERT INTO hr.asset (asset_number, asset_type, model, status)
                VALUES ('SEED-ASSET-001', 'LAPTOP', 'ThinkPad X1 Carbon', 'IN_STORAGE')
                """);
    }

    // ════════════════════════════════════════════════════════════════════════
    // department
    // ════════════════════════════════════════════════════════════════════════

    /**
     * {@code department.name} carries a UNIQUE constraint.
     * Inserting two rows with the same name must fail.
     */
    @Test
    void department_duplicateName_fails() {
        jdbc.update("INSERT INTO hr.department (name) VALUES ('Finance')");
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO hr.department (name) VALUES ('Finance')")
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // job_title
    // ════════════════════════════════════════════════════════════════════════

    /**
     * {@code job_title.name} carries a UNIQUE constraint.
     * Inserting two rows with the same name must fail.
     */
    @Test
    void jobTitle_duplicateName_fails() {
        jdbc.update("INSERT INTO hr.job_title (name) VALUES ('Data Engineer')");
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO hr.job_title (name) VALUES ('Data Engineer')")
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // employee
    // ════════════════════════════════════════════════════════════════════════

    /**
     * {@code employee.private_email} is UNIQUE.
     * Reusing john.doe's email for a second employee must fail.
     */
    @Test
    void employee_duplicatePrivateEmail_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.employee (
                            employee_code, first_name, last_name, private_email,
                            mobile_phone, date_of_birth, street, postcode, city,
                            job_title_id, department_id, grade, app_role
                        ) VALUES (
                            'E-9901', 'Alice', 'Dup', 'john.doe@cubeworks.test',
                            '+1111111111', '1991-03-01', '3 Test St', '11111', 'Utrecht',
                            ?, ?, 'JUNIOR', 'EMPLOYEE'
                        )
                        """, jobTitleId, deptId)
        );
    }

    /**
     * {@code employee.employee_code} is UNIQUE.
     * Inserting a second row with code E-0001 must fail.
     */
    @Test
    void employee_duplicateEmployeeCode_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.employee (
                            employee_code, first_name, last_name, private_email,
                            mobile_phone, date_of_birth, street, postcode, city,
                            job_title_id, department_id, grade, app_role
                        ) VALUES (
                            'E-0001', 'Bob', 'Code', 'bob.code@cubeworks.test',
                            '+2222222222', '1993-07-04', '4 Test St', '22222', 'Eindhoven',
                            ?, ?, 'MEDIOR', 'MANAGER'
                        )
                        """, jobTitleId, deptId)
        );
    }

    /**
     * {@code employee.grade} has CHECK {@code grade in ('JUNIOR','MEDIOR','SENIOR','SENIOR_I','SENIOR_II')}.
     * An invalid grade value must fail.
     */
    @Test
    void employee_invalidGrade_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.employee (
                            employee_code, first_name, last_name, private_email,
                            mobile_phone, date_of_birth, street, postcode, city,
                            job_title_id, department_id, grade, app_role
                        ) VALUES (
                            'E-9902', 'Carol', 'Grade', 'carol.grade@cubeworks.test',
                            '+3333333333', '1994-09-12', '5 Test St', '33333', 'Delft',
                            ?, ?, 'EXPERT', 'EMPLOYEE'
                        )
                        """, jobTitleId, deptId)
        );
    }

    /**
     * {@code employee.app_role} has CHECK {@code app_role in ('HR','MANAGER','EMPLOYEE','IT_ADMIN')}.
     * An unrecognised role value must fail.
     */
    @Test
    void employee_invalidAppRole_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.employee (
                            employee_code, first_name, last_name, private_email,
                            mobile_phone, date_of_birth, street, postcode, city,
                            job_title_id, department_id, grade, app_role
                        ) VALUES (
                            'E-9903', 'Dave', 'Role', 'dave.role@cubeworks.test',
                            '+4444444444', '1995-11-22', '6 Test St', '44444', 'Leiden',
                            ?, ?, 'MEDIOR', 'ADMIN'
                        )
                        """, jobTitleId, deptId)
        );
    }

    /**
     * {@code employee} CHECK: {@code manager_id is null or manager_id <> id}.
     * Setting manager_id = id (self-manager) on an existing row must fail.
     */
    @Test
    void employee_managerId_selfReference_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("UPDATE hr.employee SET manager_id = ? WHERE id = ?",
                        employeeId, employeeId)
        );
    }

    /**
     * {@code employee} CHECK: {@code buddy_id is null or buddy_id <> id}.
     * Setting buddy_id = id (self-buddy) on an existing row must fail.
     */
    @Test
    void employee_buddyId_selfReference_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("UPDATE hr.employee SET buddy_id = ? WHERE id = ?",
                        employeeId, employeeId)
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // contract
    // ════════════════════════════════════════════════════════════════════════

    /**
     * {@code contract} CHECK: {@code (contract_type = 'PERMANENT') = (end_date is null)}.
     * A PERMANENT contract with a non-null end_date must fail.
     */
    @Test
    void contract_permanent_withEndDate_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.contract (employee_id, contract_type, start_date, end_date,
                            hours_per_week, probation_months)
                        VALUES (?, 'PERMANENT', '2024-01-01', '2024-12-31', 40, 0)
                        """, employeeId)
        );
    }

    /**
     * {@code contract} CHECK: {@code (contract_type = 'PERMANENT') = (end_date is null)}.
     * A FIXED_TERM contract with a null end_date must fail.
     */
    @Test
    void contract_fixedTerm_withNullEndDate_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.contract (employee_id, contract_type, start_date,
                            hours_per_week, probation_months)
                        VALUES (?, 'FIXED_TERM', '2024-01-01', 40, 0)
                        """, employeeId)
        );
    }

    /**
     * {@code contract} CHECK: {@code end_date is null or end_date > start_date}.
     * end_date == start_date must fail (must be strictly AFTER).
     */
    @Test
    void contract_endDateEqualToStartDate_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.contract (employee_id, contract_type, start_date, end_date,
                            hours_per_week, probation_months)
                        VALUES (?, 'FIXED_TERM', '2024-06-01', '2024-06-01', 40, 0)
                        """, employeeId)
        );
    }

    /**
     * {@code contract} CHECK: {@code end_date is null or end_date > start_date}.
     * end_date before start_date must fail.
     */
    @Test
    void contract_endDateBeforeStartDate_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.contract (employee_id, contract_type, start_date, end_date,
                            hours_per_week, probation_months)
                        VALUES (?, 'FIXED_TERM', '2024-06-01', '2024-05-01', 40, 0)
                        """, employeeId)
        );
    }

    /**
     * Partial unique index {@code ux_contract_current ON contract(employee_id) WHERE is_current}.
     * A second current contract for the same employee must fail.
     */
    @Test
    void contract_twoCurrentContracts_fails() {
        // First current contract for employee2 — must succeed.
        jdbc.update("""
                INSERT INTO hr.contract (employee_id, contract_type, start_date,
                    hours_per_week, probation_months, is_current)
                VALUES (?, 'PERMANENT', '2024-01-01', 40, 1, true)
                """, employee2Id);

        // Second current contract for the same employee — must violate the partial unique index.
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.contract (employee_id, contract_type, start_date,
                            hours_per_week, probation_months, is_current)
                        VALUES (?, 'PERMANENT', '2025-01-01', 40, 0, true)
                        """, employee2Id)
        );
    }

    /**
     * {@code contract} CHECK: {@code hours_per_week between 1 and 40}.
     * Zero hours is outside the valid range.
     */
    @Test
    void contract_hoursPerWeek_zero_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.contract (employee_id, contract_type, start_date,
                            hours_per_week, probation_months)
                        VALUES (?, 'PERMANENT', '2024-03-01', 0, 0)
                        """, employeeId)
        );
    }

    /**
     * {@code contract} CHECK: {@code probation_months in (0,1,2)}.
     * 3 months is outside the allowed set.
     */
    @Test
    void contract_probationMonths_three_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.contract (employee_id, contract_type, start_date,
                            hours_per_week, probation_months)
                        VALUES (?, 'PERMANENT', '2024-04-01', 40, 3)
                        """, employeeId)
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // document
    // ════════════════════════════════════════════════════════════════════════

    /**
     * {@code document.storage_key} is UNIQUE.
     * Inserting two documents with the same storage key must fail.
     */
    @Test
    void document_duplicateStorageKey_fails() {
        jdbc.update("""
                INSERT INTO hr.document (employee_id, doc_type, file_name, content_type,
                    size_bytes, storage_key, uploaded_by)
                VALUES (?, 'CONTRACT', 'contract-v1.pdf', 'application/pdf',
                    4096, 'storage/contracts/unique-key-abc', ?)
                """, employeeId, employeeId);

        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.document (employee_id, doc_type, file_name, content_type,
                            size_bytes, storage_key, uploaded_by)
                        VALUES (?, 'CONTRACT', 'contract-v2.pdf', 'application/pdf',
                            8192, 'storage/contracts/unique-key-abc', ?)
                        """, employeeId, employeeId)
        );
    }

    /**
     * {@code document} CHECK: {@code size_bytes > 0}.
     * A document with zero bytes must fail.
     */
    @Test
    void document_zeroSizeBytes_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.document (employee_id, doc_type, file_name, content_type,
                            size_bytes, storage_key, uploaded_by)
                        VALUES (?, 'OTHER', 'empty.pdf', 'application/pdf',
                            0, 'storage/zero-size/key-001', ?)
                        """, employeeId, employeeId)
        );
    }

    /**
     * {@code document} CHECK: {@code doc_type in ('CONTRACT','POLICY',...)}.
     * An unrecognised doc_type must fail.
     */
    @Test
    void document_invalidDocType_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.document (employee_id, doc_type, file_name, content_type,
                            size_bytes, storage_key, uploaded_by)
                        VALUES (?, 'INVALID_TYPE', 'file.pdf', 'application/pdf',
                            1024, 'storage/invalid-type/key-001', ?)
                        """, employeeId, employeeId)
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // checklist_template
    // ════════════════════════════════════════════════════════════════════════

    /**
     * {@code checklist_template.name} is UNIQUE.
     * Inserting two templates with the same name must fail.
     */
    @Test
    void checklistTemplate_duplicateName_fails() {
        jdbc.update("INSERT INTO hr.checklist_template (name, kind) VALUES ('Duplicate Template', 'OFFBOARDING')");
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO hr.checklist_template (name, kind) VALUES ('Duplicate Template', 'ONBOARDING')")
        );
    }

    /**
     * {@code checklist_template} CHECK: {@code kind in ('ONBOARDING','OFFBOARDING')}.
     * An invalid kind value must fail.
     */
    @Test
    void checklistTemplate_invalidKind_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO hr.checklist_template (name, kind) VALUES ('Bad Kind Template', 'HIRING')")
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // checklist_template_item
    // ════════════════════════════════════════════════════════════════════════

    /**
     * {@code checklist_template_item} CHECK:
     * {@code policy_document_id is null or item_kind = 'POLICY_ACK'}.
     * Setting policy_document_id on a GENERIC item must fail.
     */
    @Test
    void checklistTemplateItem_policyDocumentId_withNonPolicyAckKind_fails() {
        // Upload a POLICY document to use as policy_document_id.
        long docId = jdbc.queryForObject("""
                INSERT INTO hr.document (employee_id, doc_type, file_name, content_type,
                    size_bytes, storage_key, uploaded_by)
                VALUES (?, 'POLICY', 'policy-doc.pdf', 'application/pdf',
                    2048, 'storage/policies/policy-ack-test', ?)
                RETURNING id
                """, Long.class, employeeId, employeeId);

        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.checklist_template_item (template_id, name, section,
                            owner_role, item_kind, mandatory, due_offset_days, policy_document_id)
                        VALUES (?, 'Generic with policy doc', 'DOCUMENTS_ADMIN',
                            'HR', 'GENERIC', true, 0, ?)
                        """, templateId, docId)
        );
    }

    /**
     * {@code checklist_template_item} CHECK: {@code section in (...)}.
     * An unrecognised section value must fail.
     */
    @Test
    void checklistTemplateItem_invalidSection_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.checklist_template_item (template_id, name, section,
                            owner_role, item_kind, mandatory, due_offset_days)
                        VALUES (?, 'Test Item', 'INVALID_SECTION', 'HR', 'GENERIC', true, 0)
                        """, templateId)
        );
    }

    /**
     * {@code checklist_template_item} CHECK: {@code owner_role in ('HR','IT_ADMIN','MANAGER','EMPLOYEE','SYSTEM')}.
     * An unrecognised owner role must fail.
     */
    @Test
    void checklistTemplateItem_invalidOwnerRole_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.checklist_template_item (template_id, name, section,
                            owner_role, item_kind, mandatory, due_offset_days)
                        VALUES (?, 'Test Item', 'DOCUMENTS_ADMIN', 'NOBODY', 'GENERIC', true, 0)
                        """, templateId)
        );
    }

    /**
     * {@code checklist_template_item} CHECK: {@code item_kind in ('GENERIC','POLICY_ACK',...)}.
     * An unrecognised item_kind must fail.
     */
    @Test
    void checklistTemplateItem_invalidItemKind_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.checklist_template_item (template_id, name, section,
                            owner_role, item_kind, mandatory, due_offset_days)
                        VALUES (?, 'Test Item', 'DOCUMENTS_ADMIN', 'HR', 'UNKNOWN_KIND', true, 0)
                        """, templateId)
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // email_template
    // ════════════════════════════════════════════════════════════════════════

    /**
     * {@code email_template.code} is UNIQUE.
     * Inserting a second template with the same code must fail.
     */
    @Test
    void emailTemplate_duplicateCode_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.email_template (code, name, trigger_description,
                            subject, body, to_hr)
                        VALUES (?, 'Duplicate Code Template', 'trigger', 'Subj', 'Body', true)
                        """, EMAIL_TEMPLATE_CODE)
        );
    }

    /**
     * {@code email_template.name} is UNIQUE.
     * Inserting two templates with the same name must fail.
     */
    @Test
    void emailTemplate_duplicateName_fails() {
        jdbc.update("""
                INSERT INTO hr.email_template (code, name, trigger_description, subject, body, to_employee)
                VALUES ('UNIQUE_CODE_A', 'Shared Template Name', 'trigger', 'Subj', 'Body', true)
                """);
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.email_template (code, name, trigger_description,
                            subject, body, to_manager)
                        VALUES ('UNIQUE_CODE_B', 'Shared Template Name', 'trigger', 'Subj', 'Body', true)
                        """)
        );
    }

    /**
     * {@code email_template} CHECK:
     * {@code to_employee or to_manager or to_hr or to_it_admin or to_task_owner or to_peer}.
     * All recipient flags false must fail.
     */
    @Test
    void emailTemplate_allRecipientsOff_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.email_template (code, name, trigger_description,
                            subject, body,
                            to_employee, to_manager, to_hr, to_it_admin, to_task_owner, to_peer)
                        VALUES ('NO_RECIPIENT_CODE', 'No Recipient Template', 'trigger',
                            'Subj', 'Body',
                            false, false, false, false, false, false)
                        """)
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // setting
    // ════════════════════════════════════════════════════════════════════════

    /**
     * {@code setting} CHECK: {@code category in ('TIMER','TASK_DUE')}.
     * An invalid category must fail.
     */
    @Test
    void setting_invalidCategory_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.setting (code, category, module, label)
                        VALUES ('TEST_INVALID_CAT', 'INVALID', 'GENERAL', 'Test setting')
                        """)
        );
    }

    /**
     * {@code setting} CHECK: {@code value_unit in ('DAYS','SECONDS','MINUTES','CHARACTERS')}.
     * 'WEEKS' is not in the allowed list.
     */
    @Test
    void setting_invalidValueUnit_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.setting (code, category, module, label, value_int, value_unit)
                        VALUES ('TEST_INVALID_UNIT', 'TIMER', 'GENERAL', 'Test timer', 7, 'WEEKS')
                        """)
        );
    }

    /**
     * {@code setting} CHECK: {@code can_disable or enabled}.
     * A non-disableable setting that is currently disabled must fail.
     */
    @Test
    void setting_canDisableFalse_enabledFalse_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.setting (code, category, module, label,
                            value_int, value_unit, can_disable, enabled)
                        VALUES ('TEST_DISABLED_FORCED', 'TIMER', 'GENERAL', 'Forced off',
                            7, 'DAYS', false, false)
                        """)
        );
    }

    /**
     * {@code setting} CHECK: {@code category = 'TIMER' or (value_int is not null and value_unit = 'DAYS')}.
     * A TASK_DUE setting with a null value_int must fail.
     */
    @Test
    void setting_taskDue_withNullValueInt_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.setting (code, category, module, label)
                        VALUES ('APPROVE_PIP_TEST', 'TASK_DUE', 'PIP', 'Approve PIP task due days')
                        """)
        );
    }

    /**
     * {@code setting} CHECK: {@code category = 'TIMER' or (value_int is not null and value_unit = 'DAYS')}.
     * A TASK_DUE setting with value_unit ≠ 'DAYS' must fail.
     */
    @Test
    void setting_taskDue_withNonDaysUnit_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.setting (code, category, module, label, value_int, value_unit)
                        VALUES ('SUBMIT_REVIEW_TEST', 'TASK_DUE', 'APPRAISAL', 'Submit review due', 7, 'SECONDS')
                        """)
        );
    }

    /**
     * {@code setting} CHECK: {@code code <> 'PASSWORD_MIN_LENGTH' or value_int between 8 and 64}.
     * value_int = 7 (below minimum 8) must fail.
     */
    @Test
    void setting_passwordMinLength_belowMinimum_fails() {
        // Delete any seeded row first to avoid a PK conflict (V2 may seed this code).
        jdbc.update("DELETE FROM hr.setting WHERE code = 'PASSWORD_MIN_LENGTH'");
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.setting (code, category, module, label, value_int, value_unit)
                        VALUES ('PASSWORD_MIN_LENGTH', 'TIMER', 'SECURITY', 'Password minimum length', 7, 'CHARACTERS')
                        """)
        );
    }

    /**
     * {@code setting} CHECK: {@code code <> 'PASSWORD_MIN_LENGTH' or value_int between 8 and 64}.
     * value_int = 65 (above maximum 64) must fail.
     */
    @Test
    void setting_passwordMinLength_aboveMaximum_fails() {
        // Delete any row created or left by the sibling test.
        jdbc.update("DELETE FROM hr.setting WHERE code = 'PASSWORD_MIN_LENGTH'");
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.setting (code, category, module, label, value_int, value_unit)
                        VALUES ('PASSWORD_MIN_LENGTH', 'TIMER', 'SECURITY', 'Password minimum length', 65, 'CHARACTERS')
                        """)
        );
    }

    /**
     * {@code setting} CHECK: {@code (code = 'KPI_CYCLE') = (value_text is not null)}.
     * KPI_CYCLE without value_text must fail.
     */
    @Test
    void setting_kpiCycle_withoutValueText_fails() {
        // Delete any seeded row first.
        jdbc.update("DELETE FROM hr.setting WHERE code = 'KPI_CYCLE'");
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.setting (code, category, module, label, value_int, value_unit)
                        VALUES ('KPI_CYCLE', 'TIMER', 'KPI', 'KPI cycle frequency', 90, 'DAYS')
                        """)
        );
    }

    /**
     * {@code setting} CHECK: {@code (code = 'KPI_CYCLE') = (value_text is not null)}.
     * Any setting code other than 'KPI_CYCLE' must not have value_text set.
     */
    @Test
    void setting_nonKpiCycle_withValueText_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.setting (code, category, module, label,
                            value_int, value_unit, value_text)
                        VALUES ('SOME_OTHER_TIMER', 'TIMER', 'GENERAL', 'Other timer',
                            90, 'DAYS', 'QUARTERLY')
                        """)
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // onboarding
    // ════════════════════════════════════════════════════════════════════════

    /**
     * {@code onboarding.employee_id} is UNIQUE.
     * Inserting a second onboarding row for the same employee must fail.
     */
    @Test
    void onboarding_duplicateEmployeeId_fails() {
        long emp3Id = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role
                ) VALUES (
                    'E-0003', 'Third', 'User', 'third.user@cubeworks.test',
                    '+5555555553', '1995-03-03', '3 New St', '55555', 'Groningen',
                    ?, ?, 'MEDIOR', 'EMPLOYEE'
                ) RETURNING id
                """, Long.class, jobTitleId, deptId);

        // First onboarding — must succeed (id_checked AND contract_signed_received must both be true).
        jdbc.update("""
                INSERT INTO hr.onboarding (employee_id, template_id, id_checked, contract_signed_received)
                VALUES (?, ?, true, true)
                """, emp3Id, templateId);

        // Duplicate onboarding for the same employee — must fail.
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.onboarding (employee_id, template_id,
                            id_checked, contract_signed_received)
                        VALUES (?, ?, true, true)
                        """, emp3Id, templateId)
        );
    }

    /**
     * {@code onboarding} CHECK: {@code id_checked and contract_signed_received}.
     * id_checked = false must fail even when contract_signed_received = true.
     */
    @Test
    void onboarding_idCheckedFalse_fails() {
        long empId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role
                ) VALUES (
                    'E-0011', 'OnbTest', 'One', 'onbtest1@cubeworks.test',
                    '+6600000001', '1996-01-11', '11 Onb St', '11100', 'Maastricht',
                    ?, ?, 'MEDIOR', 'EMPLOYEE'
                ) RETURNING id
                """, Long.class, jobTitleId, deptId);

        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.onboarding (employee_id, template_id,
                            id_checked, contract_signed_received)
                        VALUES (?, ?, false, true)
                        """, empId, templateId)
        );
    }

    /**
     * {@code onboarding} CHECK: {@code id_checked and contract_signed_received}.
     * contract_signed_received = false must fail even when id_checked = true.
     */
    @Test
    void onboarding_contractSignedReceivedFalse_fails() {
        long empId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role
                ) VALUES (
                    'E-0012', 'OnbTest', 'Two', 'onbtest2@cubeworks.test',
                    '+6600000002', '1996-01-12', '12 Onb St', '11200', 'Tilburg',
                    ?, ?, 'MEDIOR', 'EMPLOYEE'
                ) RETURNING id
                """, Long.class, jobTitleId, deptId);

        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.onboarding (employee_id, template_id,
                            id_checked, contract_signed_received)
                        VALUES (?, ?, true, false)
                        """, empId, templateId)
        );
    }

    /**
     * {@code onboarding} CHECK:
     * {@code it_status = 'NOT_SENT' or (it_laptop_model is not null and it_ready_by is not null
     * and it_delivery_method is not null)}.
     * A status of 'SENT' without any laptop details must fail.
     */
    @Test
    void onboarding_itStatus_sent_withoutLaptopDetails_fails() {
        long empId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role
                ) VALUES (
                    'E-0013', 'OnbTest', 'Three', 'onbtest3@cubeworks.test',
                    '+6600000003', '1996-01-13', '13 Onb St', '11300', 'Breda',
                    ?, ?, 'MEDIOR', 'EMPLOYEE'
                ) RETURNING id
                """, Long.class, jobTitleId, deptId);

        // id_checked AND contract_signed_received are true; it_status = 'SENT' but
        // it_laptop_model / it_ready_by / it_delivery_method are all null.
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.onboarding (employee_id, template_id,
                            id_checked, contract_signed_received, it_status)
                        VALUES (?, ?, true, true, 'SENT')
                        """, empId, templateId)
        );
    }

    /**
     * {@code onboarding} CHECK: {@code status in ('IN_PROGRESS','COMPLETED','CANCELLED')}.
     * An invalid status value must fail.
     */
    @Test
    void onboarding_invalidStatus_fails() {
        long empId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role
                ) VALUES (
                    'E-0014', 'OnbTest', 'Four', 'onbtest4@cubeworks.test',
                    '+6600000004', '1996-01-14', '14 Onb St', '11400', 'Arnhem',
                    ?, ?, 'MEDIOR', 'EMPLOYEE'
                ) RETURNING id
                """, Long.class, jobTitleId, deptId);

        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.onboarding (employee_id, template_id,
                            id_checked, contract_signed_received, status)
                        VALUES (?, ?, true, true, 'PENDING')
                        """, empId, templateId)
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // asset
    // ════════════════════════════════════════════════════════════════════════

    /**
     * {@code asset.asset_number} is UNIQUE.
     * Inserting a second asset with the same asset number must fail.
     */
    @Test
    void asset_duplicateAssetNumber_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.asset (asset_number, asset_type, model, status)
                        VALUES ('SEED-ASSET-001', 'MONITOR', 'Dell U2722', 'IN_STORAGE')
                        """)
        );
    }

    /**
     * {@code asset} CHECK:
     * {@code status not in ('ISSUED','RETURN_DUE','NOT_RETURNED') or employee_id is not null}.
     * An ISSUED asset with a null employee_id must fail.
     */
    @Test
    void asset_issued_withoutEmployeeId_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.asset (asset_number, asset_type, model, status)
                        VALUES ('ASSET-ISSUED-NOEP', 'LAPTOP', 'MacBook Pro 14', 'ISSUED')
                        """)
        );
    }

    /**
     * {@code asset} CHECK: employee_id required when status = 'RETURN_DUE'.
     */
    @Test
    void asset_returnDue_withoutEmployeeId_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.asset (asset_number, asset_type, model, status)
                        VALUES ('ASSET-RD-NOEP', 'PHONE', 'iPhone 15', 'RETURN_DUE')
                        """)
        );
    }

    /**
     * {@code asset} CHECK: employee_id required when status = 'NOT_RETURNED'.
     */
    @Test
    void asset_notReturned_withoutEmployeeId_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.asset (asset_number, asset_type, model, status)
                        VALUES ('ASSET-NR-NOEP', 'HEADSET', 'Jabra 75', 'NOT_RETURNED')
                        """)
        );
    }

    /**
     * {@code asset} CHECK: {@code status <> 'RETURNED' or return_condition is not null}.
     * A RETURNED asset without a return_condition must fail.
     */
    @Test
    void asset_returned_withoutReturnCondition_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.asset (asset_number, asset_type, model,
                            status, employee_id, returned_on)
                        VALUES ('ASSET-RET-NOCON', 'KEYBOARD_MOUSE', 'Logitech MX Keys',
                            'RETURNED', ?, '2024-06-01')
                        """, employeeId)
        );
    }

    /**
     * {@code asset} CHECK: {@code asset_type in ('LAPTOP','MONITOR',...,'OTHER')}.
     * 'DESK' is not in the allowed list.
     */
    @Test
    void asset_invalidAssetType_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.asset (asset_number, asset_type, model, status)
                        VALUES ('ASSET-BAD-TYPE', 'DESK', 'Standing Desk', 'IN_STORAGE')
                        """)
        );
    }

    /**
     * {@code asset} CHECK: {@code status in ('IN_STORAGE','ISSUED','RETURN_DUE','RETURNED','NOT_RETURNED')}.
     * 'LOST' is not in the allowed list.
     */
    @Test
    void asset_invalidStatus_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.asset (asset_number, asset_type, model, status)
                        VALUES ('ASSET-BAD-STATUS', 'LAPTOP', 'HP EliteBook', 'LOST')
                        """)
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // checklist_item
    // ════════════════════════════════════════════════════════════════════════

    /**
     * {@code checklist_item} CHECK:
     * {@code (status = 'DONE') = (done_at is not null and done_by is not null)}.
     * status = 'DONE' with done_at NULL must fail.
     */
    @Test
    void checklistItem_done_withoutDoneAt_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.checklist_item (employee_id, phase, name, section,
                            owner_role, item_kind, mandatory, due_date, status, done_by)
                        VALUES (?, 'ONBOARDING', 'Sign NDA', 'DOCUMENTS_ADMIN',
                            'HR', 'GENERIC', true, '2024-02-01', 'DONE', ?)
                        """, employeeId, employeeId)
        );
    }

    /**
     * {@code checklist_item} CHECK: DONE without done_by.
     * status = 'DONE' with done_by NULL must fail.
     */
    @Test
    void checklistItem_done_withoutDoneBy_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.checklist_item (employee_id, phase, name, section,
                            owner_role, item_kind, mandatory, due_date, status, done_at)
                        VALUES (?, 'ONBOARDING', 'Submit docs', 'DOCUMENTS_ADMIN',
                            'HR', 'GENERIC', true, '2024-02-01', 'DONE', now())
                        """, employeeId)
        );
    }

    /**
     * {@code checklist_item} CHECK: the reverse direction — OPEN status with done_at set.
     * The biconditional constraint: (DONE) = (done_at NOT NULL AND done_by NOT NULL).
     * Evaluating with status='OPEN', done_at NOT NULL, done_by NOT NULL:
     * false = (true AND true) → false = true → violated.
     */
    @Test
    void checklistItem_open_withDoneAt_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.checklist_item (employee_id, phase, name, section,
                            owner_role, item_kind, mandatory, due_date, status, done_at, done_by)
                        VALUES (?, 'ONBOARDING', 'Open item', 'EQUIPMENT_ISSUED',
                            'IT_ADMIN', 'EQUIPMENT', true, '2024-02-15', 'OPEN', now(), ?)
                        """, employeeId, employeeId)
        );
    }

    /**
     * {@code checklist_item} CHECK: {@code phase in ('ONBOARDING','OFFBOARDING')}.
     * 'HIRING' is not in the allowed list.
     */
    @Test
    void checklistItem_invalidPhase_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.checklist_item (employee_id, phase, name, section,
                            owner_role, item_kind, mandatory, due_date, status)
                        VALUES (?, 'HIRING', 'Test Item', 'DOCUMENTS_ADMIN',
                            'HR', 'GENERIC', true, '2024-03-01', 'OPEN')
                        """, employeeId)
        );
    }

    /**
     * {@code checklist_item} CHECK: {@code status in ('OPEN','DONE','CANCELLED')}.
     * 'PENDING' is not in the allowed list.
     */
    @Test
    void checklistItem_invalidStatus_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.checklist_item (employee_id, phase, name, section,
                            owner_role, item_kind, mandatory, due_date, status)
                        VALUES (?, 'ONBOARDING', 'Test Item', 'DOCUMENTS_ADMIN',
                            'HR', 'GENERIC', true, '2024-03-01', 'PENDING')
                        """, employeeId)
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // engine_task
    // ════════════════════════════════════════════════════════════════════════

    /**
     * {@code engine_task} CHECK: {@code pending_status in ('PENDING','UNDONE','EXECUTED','FAILED')}.
     * 'WAITING' is not in the allowed list.
     */
    @Test
    void engineTask_invalidPendingStatus_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.engine_task (engine_task_id, employee_id, task_name,
                            due_date, created_at,
                            pending_status, pending_output, pending_by, pending_execute_at)
                        VALUES ('etask-bad-status', ?, 'Approve Onboarding',
                            '2024-04-01', now(),
                            'WAITING', '{}', ?, '2024-04-01 12:00:00+00')
                        """, employeeId, employeeId)
        );
    }

    /**
     * {@code engine_task} CHECK:
     * {@code (pending_status is null) = (pending_output is null)}.
     * pending_status set but pending_output NULL must fail.
     */
    @Test
    void engineTask_pendingStatusSet_withoutPendingOutput_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.engine_task (engine_task_id, employee_id, task_name,
                            due_date, created_at,
                            pending_status, pending_by, pending_execute_at)
                        VALUES ('etask-no-output', ?, 'Approve Onboarding',
                            '2024-04-01', now(),
                            'PENDING', ?, '2024-04-01 12:00:00+00')
                        """, employeeId, employeeId)
        );
    }

    /**
     * {@code engine_task} CHECK:
     * {@code pending_status is null or (pending_by is not null and pending_execute_at is not null)}.
     * pending_status set but pending_by NULL must fail.
     */
    @Test
    void engineTask_pendingStatusSet_withoutPendingBy_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.engine_task (engine_task_id, employee_id, task_name,
                            due_date, created_at,
                            pending_status, pending_output, pending_execute_at)
                        VALUES ('etask-no-by', ?, 'Approve Onboarding',
                            '2024-04-01', now(),
                            'PENDING', '{}', '2024-04-01 12:00:00+00')
                        """, employeeId)
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // email_outbox
    // ════════════════════════════════════════════════════════════════════════

    /**
     * {@code email_outbox} CHECK: {@code status in ('PENDING','SENT','FAILED')}.
     * 'QUEUED' is not in the allowed list.
     */
    @Test
    void emailOutbox_invalidStatus_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.email_outbox (template_code, to_address, subject, body, status)
                        VALUES (?, 'alice@example.com', 'Hello', 'Body text', 'QUEUED')
                        """, EMAIL_TEMPLATE_CODE)
        );
    }

    /**
     * {@code email_outbox.template_code} is a FK on {@code email_template.code}.
     * Referencing a non-existent template code must fail.
     */
    @Test
    void emailOutbox_nonExistentTemplateCode_fails() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO hr.email_outbox (template_code, to_address, subject, body, status)
                        VALUES ('DOES_NOT_EXIST', 'bob@example.com', 'Hello', 'Body text', 'PENDING')
                        """)
        );
    }
}
