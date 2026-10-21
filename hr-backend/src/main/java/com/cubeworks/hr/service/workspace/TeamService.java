package com.cubeworks.hr.service.workspace;

import com.cubeworks.hr.common.error.ApiException;
import com.cubeworks.hr.dto.workspace.TeamMemberDto;
import com.cubeworks.hr.dto.workspace.TeamPageDto;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.employee.EmployeeRepository;
import com.cubeworks.hr.employee.EmployeeStage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Business logic for the Manager's My Team list (W05/W06, FRD-01 Wave 3).
 *
 * <h2>WS-02 enforcement</h2>
 * <p>Every query in this service is scoped to {@code employee.manager_id = caller.id} on the
 * server side. The caller's ID comes from the authenticated principal (never a request parameter
 * — R-API-2). Hiding the "Employees" menu from Manager is not the only protection (R-UI-7).</p>
 *
 * <h2>Deferred columns</h2>
 * <p>"Current projects", "Allocation" and "Development" columns from the wireframe
 * ({@code p-mg-team}) are absent from the DTOs — the tables they depend on ({@code
 * project_assignment}, {@code v_employee_allocation}, {@code v_kpi_score}) are not yet migrated.
 * F04/F05 will extend {@link TeamMemberDto} and the queries here when they ship (additive DTO
 * contract, spec §7 D-W2). The frontend renders placeholder text in those columns.</p>
 *
 * <h2>Status filter</h2>
 * <p>The "On project", "Bench" and "PIP" status filter values are accepted but return an empty
 * result set — those concepts need {@code project_assignment} and {@code pip} tables that don't
 * exist yet. Only "All" and "Onboarding" are functional now. This is documented with an inline
 * comment at the branch point rather than silently erroring or ignoring the parameter (spec §7
 * "the filter options that depend on it should accept the param but return an empty result set
 * with a code comment").</p>
 */
@Service
public class TeamService {

    /**
     * Sort fields that a Manager is permitted to use on the My Team list (fix #6).
     *
     * <p>The entire {@link TeamService} is MANAGER-only and new in Wave 3, so the allowlist
     * applies unconditionally (no role-branch check needed at the call site). Allowing arbitrary
     * sort properties would let a caller leak row ordering by hidden columns even though those
     * column values are not returned in the response.</p>
     */
    private static final Set<String> TEAM_SORT_ALLOWLIST = Set.of(
            "lastName", "firstName", "employeeCode", "stage", "jobTitle.name");

    /** Status values derived from project allocation rather than the stored stage. */
    private static final Set<String> DERIVED_STATUSES = Set.of("ON_PROJECT", "BENCH", "PIP");

    /** Upper bound when a derived-status filter has to load all direct reports first. */
    private static final int MAX_TEAM_SCAN = 1_000;

    private final EmployeeRepository employeeRepository;
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param employeeRepository for WS-02-scoped queries on the employee table
     * @param jdbc               reads current projects/allocation from v_employee_allocation
     */
    public TeamService(final EmployeeRepository employeeRepository,
                       final NamedParameterJdbcTemplate jdbc) {
        this.employeeRepository = employeeRepository;
        this.jdbc = jdbc;
    }

    /**
     * Returns a paginated, filtered list of the manager's direct reports (W05).
     *
     * <p>WS-02 filter is {@code employee.manager_id = caller.id}, enforced server-side.
     * The {@code status} parameter maps to {@link EmployeeStage} where possible; values that
     * require tables not yet migrated (On project, Bench, PIP) return an empty page.</p>
     *
     * <p>Transaction: read-only; no data is modified. Hibernate session kept open across
     * the repository call so that lazy-loaded {@code jobTitle} does not trigger a
     * LazyInitializationException in the DTO mapping. The {@code findFiltered} query
     * already JOIN FETCHes {@code jobTitle}, so no N+1 occurs.</p>
     *
     * @param caller     the authenticated Manager employee (WS-02 scope owner)
     * @param status     optional status filter ("All"/"Onboarding"/"On project"/"Bench"/"PIP")
     * @param q          optional free-text search on employee name
     * @param sortField  sort column (default: lastName)
     * @param page       zero-based page index (default: 0)
     * @param size       page size (default: 20)
     * @return paginated team member list
     */
    @Transactional(readOnly = true)
    public TeamPageDto listTeam(final Employee caller,
                                 final String status,
                                 final String q,
                                 final String sortField,
                                 final int page,
                                 final int size) {
        final String sort = (sortField != null && !sortField.isBlank()) ? sortField : "lastName";

        // Fix #6: reject sort properties not in the allowlist — prevents data-ordering leaks
        // via hidden columns (e.g. dateOfBirth). TeamService is MANAGER-only so no role-branch
        // check is needed here.
        if (!TEAM_SORT_ALLOWLIST.contains(sort)) {
            throw ApiException.fieldValidation(
                    List.of(Map.of("field", "sort", "message", "Invalid sort field.")));
        }

        final PageRequest pageable = PageRequest.of(page, size, Sort.by(sort));

        // "On project" / "Bench" / "PIP" depend on live project allocation, which is not an
        // employee column: load every direct report matching q, derive each status, filter,
        // then page in memory (a manager's team is small).
        final String derived = normaliseStatus(status);
        if (derived != null && DERIVED_STATUSES.contains(derived)) {
            final List<Employee> all = employeeRepository.findFiltered(
                    null, null, caller.getId(), q,
                    PageRequest.of(0, MAX_TEAM_SCAN, Sort.by(sort))).getContent();
            final List<TeamMemberDto> matching = toTeamMemberDtos(all).stream()
                    .filter(m -> derived.equals(m.teamStatus()))
                    .toList();
            final int from = Math.min(page * size, matching.size());
            final int to = Math.min(from + size, matching.size());
            final int totalPages = (matching.size() + size - 1) / size;
            return new TeamPageDto(matching.subList(from, to), matching.size(),
                    totalPages, page, size);
        }

        // Map the status string to a stage filter: "Onboarding" → ONBOARDING; "All" or null → no stage filter
        final EmployeeStage stageFilter = "Onboarding".equalsIgnoreCase(status)
                ? EmployeeStage.ONBOARDING : null;

        // WS-02: always scope to the caller's own direct reports
        final Page<Employee> empPage = employeeRepository.findFiltered(
                stageFilter, null, caller.getId(), q, pageable);

        final List<TeamMemberDto> items = toTeamMemberDtos(empPage.getContent());

        return new TeamPageDto(items, empPage.getTotalElements(),
                empPage.getTotalPages(), page, size);
    }

    /**
     * Returns ALL direct reports of the manager, unpaginated, for the dashboard team card (W02).
     *
     * <p>Called by {@link DashboardService#buildManagerDashboard} — same data source as W05,
     * one method so both callers stay in sync when columns are added by F04/F05.</p>
     *
     * @param caller the authenticated Manager employee
     * @return all direct reports, lastName-ordered
     */
    @Transactional(readOnly = true)
    public List<TeamMemberDto> buildTeamList(final Employee caller) {
        return toTeamMemberDtos(employeeRepository.findByManagerId(caller.getId()));
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /** "On project" / "on_project" / "Bench" ... -> ON_PROJECT / BENCH ...; null for All. */
    private static String normaliseStatus(final String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.trim().toUpperCase().replace(' ', '_');
    }

    /**
     * Maps employees to team rows, loading current projects and allocation for all of them
     * in one query (N+1 prevention: one read of v_employee_allocation per page, not per row).
     *
     * @param employees the employees (jobTitle must be loaded — use JOIN FETCH in the query)
     * @return team member DTOs in the same order
     */
    private List<TeamMemberDto> toTeamMemberDtos(final List<Employee> employees) {
        final Map<Long, Allocation> allocations = loadAllocations(
                employees.stream().map(Employee::getId).toList());
        return employees.stream()
                .map(e -> toTeamMemberDto(e, allocations.getOrDefault(e.getId(), Allocation.NONE)))
                .toList();
    }

    /** Current projects and total allocation per employee id (absent = no current project). */
    private Map<Long, Allocation> loadAllocations(final List<Long> ids) {
        final Map<Long, Allocation> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbc.query("SELECT employee_id, projects, total_allocation_pct"
                        + " FROM hr.v_employee_allocation WHERE employee_id IN (:ids)",
                new MapSqlParameterSource("ids", ids),
                rs -> {
                    result.put(rs.getLong("employee_id"), new Allocation(
                            rs.getString("projects"), rs.getInt("total_allocation_pct")));
                });
        return result;
    }

    private TeamMemberDto toTeamMemberDto(final Employee e, final Allocation allocation) {
        return new TeamMemberDto(
                e.getId(),
                e.getEmployeeCode(),
                e.getFirstName(),
                e.getLastName(),
                e.getJobTitle() != null ? e.getJobTitle().getName() : null,
                e.getStage() != null ? e.getStage().name() : null,
                allocation.projects(),
                allocation.pct(),
                teamStatus(e.getStage(), allocation.pct())
        );
    }

    /** My Team status: lifecycle stage wins, otherwise project allocation decides. */
    private static String teamStatus(final EmployeeStage stage, final int allocationPct) {
        if (stage == EmployeeStage.ONBOARDING || stage == EmployeeStage.PIP
                || stage == EmployeeStage.OFFBOARDING) {
            return stage.name();
        }
        return allocationPct > 0 ? "ON_PROJECT" : "BENCH";
    }

    /** Row from v_employee_allocation. */
    private record Allocation(String projects, int pct) {
        static final Allocation NONE = new Allocation(null, 0);
    }
}
