package com.cubeworks.hr.employee;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link Employee}.
 *
 * <p>Derived finders match the unique indexes already defined in the schema. They support
 * service-layer validation before a constraint violation would surface from the DB, and
 * they are needed for authentication lookup (by Keycloak subject) and for the
 * invitation flow (by private e-mail).</p>
 *
 * <p>Gate 2 adds complex query methods with JOIN FETCH (e.g. fetching an employee with
 * jobTitle and department in one query to avoid the N+1 on the employee list screen).</p>
 */
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /**
     * Finds an employee by private e-mail address, ignoring case (ONB-09 duplicate check).
     *
     * <p>E-mail addresses are compared case-insensitively ({@code lower()} on both sides), so
     * "Jesse@Outlook.com" is detected as a duplicate of "jesse@outlook.com".</p>
     *
     * @param privateEmail the private e-mail address to look up
     * @return the matching employee, if any
     */
    @Query("SELECT e FROM Employee e WHERE lower(e.privateEmail) = lower(:privateEmail)")
    Optional<Employee> findByPrivateEmailIgnoreCase(@Param("privateEmail") String privateEmail);

    /**
     * Returns every employee holding the given application role who can still receive
     * e-mail: not archived and whose login account is not disabled (IT_EQUIPMENT_REQUEST recipients).
     *
     * @param appRole the application role
     * @return matching employees
     */
    @Query("""
            SELECT e FROM Employee e
            WHERE e.appRole = :appRole
              AND e.stage <> com.cubeworks.hr.employee.EmployeeStage.ARCHIVED
              AND (e.accountStatus IS NULL OR e.accountStatus <> com.cubeworks.hr.employee.AccountStatus.DISABLED)
            """)
    List<Employee> findActiveByAppRole(@Param("appRole") AppRole appRole);

    /**
     * Finds an employee by their unique work e-mail address.
     *
     * @param workEmail the work e-mail address to look up
     * @return the matching employee, if any
     */
    Optional<Employee> findByWorkEmail(String workEmail);

    /**
     * Finds an employee by their unique employee code (e.g. {@code E-0031}).
     *
     * @param employeeCode the employee code to look up
     * @return the matching employee, if any
     */
    Optional<Employee> findByEmployeeCode(String employeeCode);

    /**
     * Finds an employee by their CaseFabric case ID.
     *
     * @param caseId the CaseFabric case UUID
     * @return the matching employee, if any
     */
    Optional<Employee> findByCaseId(String caseId);

    /**
     * Finds an employee by their Keycloak subject claim ({@code sub}).
     *
     * <p>Used by the JWT filter to resolve the current user from the token subject.</p>
     *
     * @param idpSubject the Keycloak subject UUID string
     * @return the matching employee, if any
     */
    Optional<Employee> findByIdpSubject(String idpSubject);

    /**
     * Returns all employees with a given application role.
     *
     * <p>Used by the onboarding-form manager dropdown (D-10): managers are employees
     * whose {@code app_role = MANAGER}.</p>
     *
     * @param appRole the application role to filter by
     * @return all employees with the given role
     */
    List<Employee> findByAppRole(AppRole appRole);

    /**
     * Returns a page of employees filtered by optional stage and department, supporting
     * the Employees list screen (A04, S1).
     *
     * <p>Uses JOIN FETCH to load {@code jobTitle} and {@code department} in one query,
     * preventing N+1 on the list screen where every row needs job title and department.
     * COUNT query is a separate join-free query for performance.</p>
     *
     * <p>N+1 prevention: without JOIN FETCH, loading 50 employees on the list page would
     * issue 100 additional SELECT statements (one each for jobTitle and department per row).
     * With JOIN FETCH: 1 query total.</p>
     *
     * @param stage        filter by stage, or null for all stages
     * @param departmentId filter by department, or null for all departments
     * @param managerId    filter by direct manager, or null for all managers
     * @param q            case-insensitive free-text search on full name or employee code, or null to skip
     * @param pageable     paging and sorting parameters
     * @return a page of matching employees with jobTitle and department loaded
     */
    @Query(value = """
            SELECT e FROM Employee e
            JOIN FETCH e.jobTitle
            JOIN FETCH e.department
            WHERE (:stage IS NULL OR e.stage = :stage)
              AND (:departmentId IS NULL OR e.department.id = :departmentId)
              AND (:managerId IS NULL OR e.manager.id = :managerId)
              AND (CAST(:q AS string) IS NULL
                   OR LOWER(CONCAT(e.firstName, ' ', e.lastName)) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                   OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
            """,
            countQuery = """
            SELECT COUNT(e) FROM Employee e
            WHERE (:stage IS NULL OR e.stage = :stage)
              AND (:departmentId IS NULL OR e.department.id = :departmentId)
              AND (:managerId IS NULL OR e.manager.id = :managerId)
              AND (CAST(:q AS string) IS NULL
                   OR LOWER(CONCAT(e.firstName, ' ', e.lastName)) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                   OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
            """)
    Page<Employee> findFiltered(
            @Param("stage") EmployeeStage stage,
            @Param("departmentId") Long departmentId,
            @Param("managerId") Long managerId,
            @Param("q") String q,
            Pageable pageable);

    /**
     * Returns all employees whose line manager is the given employee (WS-02).
     *
     * <p>Used by the manager dashboard to show only the manager's own team.</p>
     *
     * @param managerId the manager's employee ID
     * @return direct reports of this manager
     */
    List<Employee> findByManagerId(Long managerId);

    /**
     * Counts how many employees belong to the given department.
     *
     * <p>Kept for call sites that only need one department's count. The Settings departments
     * list (FRD-08 C01) uses {@link #countEmployeesGroupedByDepartment()} instead, to avoid
     * issuing one SELECT per department row (Gate 5 code review finding).</p>
     *
     * @param departmentId the department's surrogate PK
     * @return number of employees whose {@code department_id} matches
     */
    long countByDepartmentId(Long departmentId);

    /**
     * Returns the employee count for every department in one query, as
     * {@code [departmentId, count]} pairs.
     *
     * <p>N+1 prevention (FRD-08 C01, Gate 5 code review): the Settings departments list shows
     * every department's "People" count on one screen; this single aggregate query replaces
     * what would otherwise be one {@link #countByDepartmentId} call per department. Departments
     * with zero employees are absent from the result — callers default missing ids to 0.</p>
     *
     * @return one {@code Object[]} per department that has at least one employee:
     *         index 0 is the department id ({@code Long}), index 1 is the count ({@code Long})
     */
    @Query("SELECT e.department.id, COUNT(e) FROM Employee e WHERE e.department IS NOT NULL GROUP BY e.department.id")
    List<Object[]> countEmployeesGroupedByDepartment();

    /**
     * Counts employees whose current stage matches the given value.
     *
     * <p>Used by {@link com.cubeworks.hr.service.workspace.DashboardService#buildHrDashboard}
     * to count active and onboarding employees in a single scalar query instead of loading the
     * full table and filtering in memory (fix #10 — replaces three {@code findAll()} calls).</p>
     *
     * @param stage the lifecycle stage to count
     * @return number of employees with this stage
     */
    long countByStage(EmployeeStage stage);

    /**
     * Counts employees whose stage matches and whose {@code stage_since} date is on or after
     * the given date.
     *
     * <p>Used by {@link com.cubeworks.hr.service.workspace.DashboardService#buildHrDashboard}
     * to compute the "active this month" delta (fix #10). Employees with a {@code null}
     * {@code stage_since} are excluded automatically by the non-null path comparison in JPQL.</p>
     *
     * @param stage the lifecycle stage to match
     * @param date  only rows with {@code stage_since >= date} are counted
     * @return number of matching employees
     */
    long countByStageAndStageSinceGreaterThanEqual(EmployeeStage stage, LocalDate date);

    /**
     * Returns at most 2 employees in the given stage, ordered by last name ascending.
     *
     * <p>Used by {@link com.cubeworks.hr.service.workspace.DashboardService#buildHrDashboard}
     * to populate the "in onboarding" name preview card (fix #10 — replaces a findAll + filter
     * + limit stream pipeline).</p>
     *
     * @param stage the lifecycle stage to filter
     * @return up to 2 matching employees
     */
    List<Employee> findTop2ByStageOrderByLastNameAsc(EmployeeStage stage);

    /**
     * Returns the highest numeric {@code E-NNNN} employee code for sequence generation.
     *
     * <p>The service extracts the numeric suffix and increments by 1 to generate the next
     * employee code (e.g. {@code E-0050} → {@code E-0051}). The query filters to codes that
     * match the pattern {@code E-<digits>} only — test-seed codes like {@code E-HR-1},
     * {@code E-MG-3} etc. are excluded so that {@code MAX} always returns a parseable number.</p>
     *
     * <p>N+1 prevention: one scalar query rather than loading all employees and reducing in
     * application code.</p>
     *
     * @return the code with the highest numeric suffix, or empty if no E-NNNN codes exist yet
     */
    @Query(value = """
            SELECT employee_code
            FROM hr.employee
            WHERE employee_code ~ '^E-[0-9]+$'
            ORDER BY CAST(SUBSTRING(employee_code, 3) AS INTEGER) DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<String> findMaxEmployeeCode();
}
