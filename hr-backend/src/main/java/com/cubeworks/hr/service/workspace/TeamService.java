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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final EmployeeRepository employeeRepository;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param employeeRepository for WS-02-scoped queries on the employee table
     */
    public TeamService(final EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
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

        // "On project", "Bench" and "PIP" status values cannot be applied until project_assignment
        // and pip tables exist (F04/F07). Return an empty page rather than an error, so the
        // frontend can display an "No results" state without a 400 response.
        if ("On project".equalsIgnoreCase(status) || "Bench".equalsIgnoreCase(status)
                || "PIP".equalsIgnoreCase(status)) {
            // Deferred: project_assignment (F04) / pip (F07) tables do not exist yet.
            // Accept the filter value but return empty until those features ship.
            return new TeamPageDto(List.of(), 0, 0, page, size);
        }

        // Map the status string to a stage filter: "Onboarding" → ONBOARDING; "All" or null → no stage filter
        final EmployeeStage stageFilter = "Onboarding".equalsIgnoreCase(status)
                ? EmployeeStage.ONBOARDING : null;

        // WS-02: always scope to the caller's own direct reports
        final Page<Employee> empPage = employeeRepository.findFiltered(
                stageFilter, null, caller.getId(), q, pageable);

        final List<TeamMemberDto> items = empPage.getContent().stream()
                .map(this::toTeamMemberDto)
                .toList();

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
        return employeeRepository.findByManagerId(caller.getId()).stream()
                .map(this::toTeamMemberDto)
                .toList();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Maps an {@link Employee} to a {@link TeamMemberDto}.
     *
     * <p>Deferred fields (currentProjects, allocation, development) are absent from the record —
     * F04/F05 will add them when their tables exist (additive DTO contract, D-W2).</p>
     *
     * @param e the employee entity (jobTitle must be loaded — use JOIN FETCH in the caller's query)
     * @return the team member DTO
     */
    private TeamMemberDto toTeamMemberDto(final Employee e) {
        return new TeamMemberDto(
                e.getId(),
                e.getEmployeeCode(),
                e.getFirstName(),
                e.getLastName(),
                e.getJobTitle() != null ? e.getJobTitle().getName() : null,
                e.getStage() != null ? e.getStage().name() : null
        );
    }
}
