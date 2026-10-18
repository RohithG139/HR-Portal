package com.cubeworks.hr;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Base class for F01 HTTP integration tests.
 *
 * <h2>JWT mocking strategy</h2>
 * <p>The {@code jwt()} post-processor from {@code spring-security-test} bypasses the
 * {@link org.springframework.security.oauth2.jwt.JwtDecoder} entirely: it injects a
 * {@link org.springframework.security.oauth2.jwt.Jwt} object directly into the security
 * context.  Combined with the {@link AbstractIntegrationTest#jwtDecoder} {@code @MockBean},
 * no live Keycloak is required.</p>
 *
 * <h2>Actor sub values</h2>
 * <p>Each test actor has a stable {@code sub} value set as {@code idp_subject} on their
 * employee row.  The service layer is expected to look up the employee by
 * {@code employee.idp_subject = JWT.sub} and obtain their {@code app_role} from the
 * database.  The role is also supplied in {@code realm_access.roles} in case the
 * implementation reads it from the JWT instead of the DB.</p>
 *
 * <h2>Unique test data</h2>
 * <p>Each concrete test class creates its own seed employees (with unique codes and e-mail
 * addresses) so that tests from different classes running in the same JVM fork do not
 * conflict.  Raw-SQL inserts via {@link JdbcTemplate} bypass the service layer and let
 * each test assert exactly the DB state it produced.</p>
 *
 * <h2>Schema prefix</h2>
 * <p>All raw JDBC queries use the {@code hr.} prefix because the Testcontainers JDBC URL
 * does not set {@code currentSchema}.</p>
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractApiTest extends AbstractIntegrationTest {

    // ── Stable sub values for per-class test actors ───────────────────────────

    /** The Keycloak {@code sub} for the HR actor created by this test class. */
    protected String hrSub;
    /** The Keycloak {@code sub} for the IT admin actor. */
    protected String itSub;
    /** The Keycloak {@code sub} for the Manager actor. */
    protected String managerSub;
    /** The Keycloak {@code sub} for the Employee actor. */
    protected String employeeSub;

    // ── Spring infrastructure ──────────────────────────────────────────────────

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    // ── Per-class counter to generate unique identifiers ─────────────────────

    /**
     * Shared sequence that each test class seeds from to avoid PK / unique-index conflicts
     * across multiple test class instances in the same JVM fork.  The base value 9000 leaves
     * plenty of room below the MigrationConstraintTests range (which uses E-0001…E-0014).
     */
    private static final AtomicLong COUNTER = new AtomicLong(9_000L);

    /** Returns the next unique integer suffix for codes and e-mail addresses. */
    protected static long nextId() {
        return COUNTER.incrementAndGet();
    }

    // ── Stored IDs for the role actors created in @BeforeAll ─────────────────

    protected long hrEmployeeId;
    protected long itEmployeeId;
    protected long managerEmployeeId;
    protected long baseEmployeeId;

    // ── Seeded lookup IDs (read from V2 seed data) ────────────────────────────

    protected long developmentDeptId;
    protected long developerJobTitleId;
    protected long developmentTemplateId;

    /**
     * Reads the V2-seeded lookup IDs and creates one actor per role.
     *
     * <p>Must be called explicitly by concrete subclass {@code @BeforeAll} methods because
     * JUnit 5 lifecycle callbacks are not inherited from non-{@code @TestInstance(PER_CLASS)}
     * abstract superclasses in all runners.  Subclasses should call
     * {@code super.setUpActors()} as the first statement in their own
     * {@code @BeforeAll}.</p>
     */
    @BeforeAll
    void setUpActors() {
        // ── Read seeded lookup IDs ───────────────────────────────────────────
        developmentDeptId = jdbc.queryForObject(
                "SELECT id FROM hr.department WHERE name = 'Development'", Long.class);
        developerJobTitleId = jdbc.queryForObject(
                "SELECT id FROM hr.job_title WHERE name = 'Developer'", Long.class);
        developmentTemplateId = jdbc.queryForObject(
                "SELECT id FROM hr.checklist_template WHERE name = 'Development'", Long.class);

        // ── HR actor ────────────────────────────────────────────────────────
        long hrId = nextId();
        hrSub = "sub-hr-" + hrId;
        hrEmployeeId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, idp_subject
                ) VALUES (
                    ?, 'HR', 'Actor', ?,
                    '+31 6 11000001', '1985-01-01', 'HR Lane 1', '1000 AA', 'Amsterdam',
                    ?, ?, 'SENIOR', 'HR', ?
                ) RETURNING id
                """, Long.class,
                "E-HR-" + hrId,
                "hr.actor." + hrId + "@cubeworks.test",
                developerJobTitleId, developmentDeptId, hrSub);

        // ── IT admin actor ───────────────────────────────────────────────────
        long itId = nextId();
        itSub = "sub-it-" + itId;
        itEmployeeId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, idp_subject
                ) VALUES (
                    ?, 'IT', 'Actor', ?,
                    '+31 6 11000002', '1987-05-15', 'IT Road 2', '1000 BB', 'Amsterdam',
                    ?, ?, 'MEDIOR', 'IT_ADMIN', ?
                ) RETURNING id
                """, Long.class,
                "E-IT-" + itId,
                "it.actor." + itId + "@cubeworks.test",
                developerJobTitleId, developmentDeptId, itSub);

        // ── Manager actor ────────────────────────────────────────────────────
        long mgId = nextId();
        managerSub = "sub-mg-" + mgId;
        managerEmployeeId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, idp_subject
                ) VALUES (
                    ?, 'Manager', 'Actor', ?,
                    '+31 6 11000003', '1982-03-20', 'Manager Ave 3', '1000 CC', 'Amsterdam',
                    ?, ?, 'SENIOR_I', 'MANAGER', ?
                ) RETURNING id
                """, Long.class,
                "E-MG-" + mgId,
                "mg.actor." + mgId + "@cubeworks.test",
                developerJobTitleId, developmentDeptId, managerSub);

        // ── Employee actor ───────────────────────────────────────────────────
        long empId = nextId();
        employeeSub = "sub-em-" + empId;
        baseEmployeeId = jdbc.queryForObject("""
                INSERT INTO hr.employee (
                    employee_code, first_name, last_name, private_email,
                    mobile_phone, date_of_birth, street, postcode, city,
                    job_title_id, department_id, grade, app_role, idp_subject,
                    manager_id
                ) VALUES (
                    ?, 'Emp', 'Actor', ?,
                    '+31 6 11000004', '1995-07-10', 'Emp Street 4', '1000 DD', 'Amsterdam',
                    ?, ?, 'JUNIOR', 'EMPLOYEE', ?,
                    ?
                ) RETURNING id
                """, Long.class,
                "E-EM-" + empId,
                "em.actor." + empId + "@cubeworks.test",
                developerJobTitleId, developmentDeptId, employeeSub, managerEmployeeId);
    }

    // ── JWT helpers ───────────────────────────────────────────────────────────

    /**
     * Creates a JWT post-processor authenticated as the HR actor.
     *
     * <p>The JWT carries both the {@code sub} and a {@code realm_access.roles = ["HR"]} claim
     * so the service can use either the DB lookup or the claim. <strong>The claim alone is not
     * enough</strong>: {@code spring-security-test}'s {@code jwt()} post-processor does not run
     * the app's real {@link com.cubeworks.hr.security.KeycloakRoleConverter} — by default it
     * derives authorities from a {@code scope}/{@code scp} claim, which is absent here, so
     * without an explicit {@code .authorities(...)} call every {@code @PreAuthorize} check would
     * see no granted authorities at all and fail with 403 regardless of the claim's content.
     * The authority set here must mirror exactly what {@code KeycloakRoleConverter} would
     * produce at runtime: {@code "ROLE_" + role}.</p>
     */
    protected JwtRequestPostProcessor asHR() {
        return jwt().jwt(j -> j
                .subject(hrSub)
                .claim("realm_access", Map.of("roles", java.util.List.of("HR"))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_HR"));
    }

    /** JWT post-processor authenticated as the IT admin actor (see {@link #asHR()} for why {@code .authorities(...)} is required). */
    protected JwtRequestPostProcessor asIT() {
        return jwt().jwt(j -> j
                .subject(itSub)
                .claim("realm_access", Map.of("roles", java.util.List.of("IT_ADMIN"))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_IT_ADMIN"));
    }

    /** JWT post-processor authenticated as the Manager actor (see {@link #asHR()} for why {@code .authorities(...)} is required). */
    protected JwtRequestPostProcessor asManager() {
        return jwt().jwt(j -> j
                .subject(managerSub)
                .claim("realm_access", Map.of("roles", java.util.List.of("MANAGER"))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MANAGER"));
    }

    /** JWT post-processor authenticated as the base Employee actor (see {@link #asHR()} for why {@code .authorities(...)} is required). */
    protected JwtRequestPostProcessor asEmployee() {
        return jwt().jwt(j -> j
                .subject(employeeSub)
                .claim("realm_access", Map.of("roles", java.util.List.of("EMPLOYEE"))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_EMPLOYEE"));
    }

    /**
     * JWT post-processor authenticated as an ad-hoc employee identified by their
     * {@code idp_subject}.  Use this when a specific test needs its own user.
     *
     * @param sub   the {@code idp_subject} value stored in the DB for this employee
     * @param role  the application role string ("HR", "MANAGER", "EMPLOYEE", "IT_ADMIN")
     */
    protected JwtRequestPostProcessor asUserWithSub(final String sub, final String role) {
        return jwt().jwt(j -> j
                .subject(sub)
                .claim("realm_access", Map.of("roles", java.util.List.of(role))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
    }

    // ── MockMvc shortcuts ─────────────────────────────────────────────────────

    /**
     * Performs the given request and returns the {@link ResultActions} for chaining.
     *
     * @param request the pre-built request
     * @return result actions for assertion chaining
     */
    protected ResultActions perform(final MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request);
    }

    // ── JSON body builders ────────────────────────────────────────────────────

    /**
     * Builds a valid {@code POST /api/onboardings} JSON body for a permanent-contract
     * developer.  The values satisfy every validation rule in spec §5 so that callers
     * can override individual fields to trigger specific errors.
     *
     * @param privateEmail  unique private e-mail for this employee
     * @param managerId     the FK id of the manager row (already in the DB)
     * @return JSON string
     */
    protected String validOnboardingBody(final String privateEmail, final long managerId) {
        return """
                {
                  "firstName": "Jesse",
                  "lastName": "Willems",
                  "privateEmail": "%s",
                  "mobilePhone": "+31 6 12345678",
                  "dateOfBirth": "2000-10-21",
                  "street": "Keizersgracht 1",
                  "postcode": "3511 AV",
                  "city": "Utrecht",
                  "contractType": "PERMANENT",
                  "startDate": "2027-01-05",
                  "hoursPerWeek": 40,
                  "probationMonths": 2,
                  "idChecked": true,
                  "contractSignedReceived": true,
                  "rightToWorkVerified": false,
                  "jobTitleId": %d,
                  "departmentId": %d,
                  "managerId": %d,
                  "grade": "MEDIOR",
                  "appRole": "EMPLOYEE",
                  "templateId": %d
                }
                """.formatted(
                privateEmail,
                developerJobTitleId,
                developmentDeptId,
                managerId,
                developmentTemplateId);
    }

    /**
     * Builds a valid {@code PUT /api/onboardings/{id}/it-request} JSON body.
     *
     * @param startDate  the employee's start date (ISO format); ready-by is set to this date
     */
    protected String validItRequestBody(final String startDate) {
        return """
                {
                  "laptopModel": "MacBook Pro 14\\"",
                  "keyboardLayout": "UK",
                  "readyBy": "%s",
                  "deliveryMethod": "OFFICE",
                  "accessories": ["MONITOR", "KEYBOARD_MOUSE"],
                  "accounts": ["MICROSOFT_365", "SLACK", "VPN"],
                  "notes": "Test IT request"
                }
                """.formatted(startDate);
    }

    /**
     * Content-type helper — sets the request's content type to JSON and the body.
     *
     * @param builder  the MockMvc request builder
     * @param json     the JSON body
     * @return the builder with content type and body set
     */
    protected static MockHttpServletRequestBuilder withJson(
            final MockHttpServletRequestBuilder builder, final String json) {
        return builder
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }
}
