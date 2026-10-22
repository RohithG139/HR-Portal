package com.cubeworks.hr.service.workspace;

import com.cubeworks.hr.asset.AssetRepository;
import com.cubeworks.hr.dto.workspace.DashboardTaskDto;
import com.cubeworks.hr.dto.workspace.EmployeeDashboardDto;
import com.cubeworks.hr.dto.workspace.EquipmentDto;
import com.cubeworks.hr.dto.workspace.HrDashboardDto;
import com.cubeworks.hr.dto.workspace.ItAdminDashboardDto;
import com.cubeworks.hr.dto.workspace.ManagerDashboardDto;
import com.cubeworks.hr.dto.workspace.TeamMemberDto;
import com.cubeworks.hr.dto.workspace.UpcomingEventDto;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.employee.EmployeeRepository;
import com.cubeworks.hr.employee.EmployeeStage;
import com.cubeworks.hr.task.EngineTaskRepository;
import com.cubeworks.hr.task.TaskService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Builds the role-specific dashboard response for {@code GET /api/dashboard} (W01, FRD-01 Wave 3).
 *
 * <p>A single endpoint dispatches to one of four role-shaped DTOs based on the caller's role
 * (never a request parameter — R-API-2). Only the "buildable now" cards are populated; cards
 * depending on tables from F04/F05/F06/F07/F08 are absent from the DTOs (additive DTO contract,
 * spec §7 D-W2). Later features extend the relevant service method and DTO record without adding
 * a new endpoint.</p>
 *
 * <h2>Task list reuse</h2>
 * <p>Every dashboard's task card reuses {@link TaskService#getMyTasks} — the same query and
 * DTO that backs the full My Tasks screen (A36/S12). Dashboard task items are mapped to
 * {@link DashboardTaskDto} to keep the dashboard's own DTO stable even if A36's shape changes.</p>
 */
@Service
public class DashboardService {

    private final EmployeeRepository employeeRepository;
    private final AssetRepository assetRepository;
    private final EngineTaskRepository engineTaskRepository;
    private final TaskService taskService;
    // Fix #11: constructor-inject TeamService instead of passing it as a method parameter.
    // There is no circular dependency: TeamService only depends on EmployeeRepository.
    // The previous pattern (parameter injection) was a code-smell workaround for a
    // non-existent circular dependency.
    private final TeamService teamService;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param employeeRepository   for querying active/onboarding/team counts and names
     * @param assetRepository      for the employee's issued assets card (W03)
     * @param engineTaskRepository for the IT admin's equipment and setup task counts (W04)
     * @param taskService          reuses A36's My Tasks query for every role's task list
     * @param teamService          provides the team list for the Manager dashboard card (W02)
     * @param jdbc                 reads the HR "Upcoming" card from v_upcoming_dates and friends
     * @param clock                application-wide clock bean
     */
    public DashboardService(final EmployeeRepository employeeRepository,
                             final AssetRepository assetRepository,
                             final EngineTaskRepository engineTaskRepository,
                             final TaskService taskService,
                             final TeamService teamService,
                             final NamedParameterJdbcTemplate jdbc,
                             final Clock clock) {
        this.employeeRepository = employeeRepository;
        this.assetRepository = assetRepository;
        this.engineTaskRepository = engineTaskRepository;
        this.taskService = taskService;
        this.teamService = teamService;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Builds the HR dashboard DTO (W01/W1, FRD-01 Wave 3).
     *
     * <p>Read-only aggregation — no business transaction, no audit entry, no engine call
     * (nothing is being changed). {@code @Transactional(readOnly = true)} for Hibernate session
     * consistency across the multiple repository calls.</p>
     *
     * @param caller the authenticated HR employee
     * @return populated HR dashboard DTO with buildable-now cards
     */
    @Transactional(readOnly = true)
    public HrDashboardDto buildHrDashboard(final Employee caller) {
        // Fix #10: replace three findAll().stream().filter() calls (each loading the entire
        // employee table) with targeted COUNT / findTop2 queries — one query per metric.
        // N+1 prevention: countByStage issues a single "SELECT COUNT(*) WHERE stage = ?" rather
        // than loading all employee rows and counting in Java.
        final long activeEmployees = employeeRepository.countByStage(EmployeeStage.ACTIVE);

        // [ASSUMPTION] per spec §5 W1: "activeEmployeesDelta" = count of employees who
        // entered ACTIVE stage this calendar month, using employee.stage_since as the signal.
        // The FRD does not define the exact computation; stage_since is the closest available
        // field. If stage_since is null (legacy data), the row is excluded from the delta count.
        final LocalDate firstOfMonth = LocalDate.now(clock).withDayOfMonth(1);
        final long activeEmployeesDelta = employeeRepository
                .countByStageAndStageSinceGreaterThanEqual(EmployeeStage.ACTIVE, firstOfMonth);

        final long onboardingCount = employeeRepository.countByStage(EmployeeStage.ONBOARDING);
        // Wireframe shows up to 2 names in the "In onboarding" card — findTop2 limits at the DB.
        final List<String> onboardingNames = employeeRepository
                .findTop2ByStageOrderByLastNameAsc(EmployeeStage.ONBOARDING)
                .stream()
                .map(e -> e.getFirstName() + " " + e.getLastName())
                .toList();

        final List<DashboardTaskDto> tasks = toTaskDtos(caller);

        return new HrDashboardDto(activeEmployees, activeEmployeesDelta,
                onboardingCount, onboardingNames, tasks, findUpcoming());
    }

    /** Max rows in the HR dashboard's "Upcoming" card (wireframe shows 5). */
    private static final int UPCOMING_LIMIT = 5;

    /**
     * Next dated events for the HR "Upcoming" card, soonest first.
     *
     * <p>{@code v_upcoming_dates} already covers probation ends, contract ends and PIP final
     * reviews (next 90 days, same source as the C44 report). The wireframe also lists first
     * days of onboarding employees and last working days of approved exits, which the view
     * does not cover, so those two are unioned in here over the same 90-day window.</p>
     */
    private List<UpcomingEventDto> findUpcoming() {
        final String sql = "SELECT event_type, employee_id, employee_name, event_date FROM ("
                + " SELECT vud.event_type, vud.employee_id, vud.employee_name, vud.event_date"
                + "   FROM hr.v_upcoming_dates vud"
                + " UNION ALL"
                + " SELECT 'START', e.id, e.first_name || ' ' || e.last_name, c.start_date"
                + "   FROM hr.contract c JOIN hr.employee e ON e.id = c.employee_id"
                + "  WHERE c.is_current AND e.stage = 'ONBOARDING'"
                + "    AND c.start_date BETWEEN current_date AND current_date + 90"
                + " UNION ALL"
                + " SELECT 'LAST_DAY', e.id, e.first_name || ' ' || e.last_name, x.last_working_day"
                + "   FROM hr.employee_exit x JOIN hr.employee e ON e.id = x.employee_id"
                + "  WHERE x.status IN ('APPROVED', 'IN_PROGRESS')"
                + "    AND x.last_working_day BETWEEN current_date AND current_date + 90"
                + ") ev ORDER BY event_date ASC, employee_name ASC LIMIT :limit";

        return jdbc.query(sql, new MapSqlParameterSource("limit", UPCOMING_LIMIT),
                (rs, rowNum) -> new UpcomingEventDto(
                        rs.getString("event_type"),
                        rs.getLong("employee_id"),
                        rs.getString("employee_name"),
                        rs.getDate("event_date").toLocalDate()));
    }

    /**
     * Builds the Manager dashboard DTO (W01/W2, FRD-01 Wave 3).
     *
     * <p>The {@code myTeam} list reuses {@link TeamService#buildTeamList(Employee)} so there
     * is one source of truth for team member data (spec §7 note — "W01's myTeam call the same
     * service method W05 uses internally").</p>
     *
     * @param caller the authenticated Manager employee
     * @return populated Manager dashboard DTO
     */
    @Transactional(readOnly = true)
    public ManagerDashboardDto buildManagerDashboard(final Employee caller) {
        final long teamMembers = employeeRepository.findByManagerId(caller.getId()).size();
        final List<DashboardTaskDto> tasks = toTaskDtos(caller);
        // Same data as W05 My Team — one source of truth (fix #11: uses the injected field).
        final List<TeamMemberDto> myTeam = teamService.buildTeamList(caller);
        return new ManagerDashboardDto(teamMembers, tasks, myTeam);
    }

    /**
     * Builds the Employee dashboard DTO for active-stage employees (W01/W3, FRD-01 Wave 3).
     *
     * <p>Only called for employees whose {@code stage = 'ACTIVE'}: the controller checks the
     * caller's stage and delegates to F01's A25 for {@code stage = 'ONBOARDING'} employees.
     * An employee in any other stage (unexpected but possible) receives an empty DTO.</p>
     *
     * @param caller the authenticated EMPLOYEE caller
     * @return populated Employee dashboard DTO with equipment and task cards
     */
    @Transactional(readOnly = true)
    public EmployeeDashboardDto buildEmployeeDashboard(final Employee caller) {
        final List<EquipmentDto> equipment = assetRepository.findIssuedToEmployee(caller.getId())
                .stream()
                .map(a -> new EquipmentDto(a.getId(), a.getAssetNumber(),
                        a.getAssetType() != null ? a.getAssetType().name() : null,
                        a.getModel()))
                .toList();
        final List<DashboardTaskDto> tasks = toTaskDtos(caller);
        return new EmployeeDashboardDto(equipment, tasks);
    }

    /**
     * Builds the IT admin dashboard DTO (W01/W4, FRD-01 Wave 3).
     *
     * <p>Equipment request and Open setup items counts use dedicated {@code COUNT} queries
     * rather than loading full entity objects — one scalar query per count, same principle as
     * {@link EmployeeRepository#countEmployeesGroupedByDepartment()} (N+1 prevention).</p>
     *
     * @param caller the authenticated IT admin employee
     * @return populated IT admin dashboard DTO
     */
    @Transactional(readOnly = true)
    public ItAdminDashboardDto buildItAdminDashboard(final Employee caller) {
        // N+1 prevention: two scalar COUNT queries instead of loading full EngineTask lists.
        final long equipmentRequests = engineTaskRepository.countOpenByTaskName("Equipment request");
        final long openSetupItems = engineTaskRepository.countOpenByTaskName("Remaining setup");
        final List<DashboardTaskDto> tasks = toTaskDtos(caller);

        // Card detail lines — small read-only lookups, one query each
        final Map<String, Object> nextEquipment = firstRow(
                "SELECT e.first_name || ' ' || e.last_name AS name, c.start_date AS d"
                + " FROM hr.engine_task t JOIN hr.employee e ON e.id = t.employee_id"
                + " LEFT JOIN hr.contract c ON c.employee_id = e.id AND c.is_current"
                + " WHERE t.task_name = 'Equipment request' AND t.completed_at IS NULL"
                + " ORDER BY t.due_date LIMIT 1");
        final List<String> setupNames = jdbc.queryForList(
                "SELECT e.first_name || ' ' || e.last_name"
                + " FROM hr.engine_task t JOIN hr.employee e ON e.id = t.employee_id"
                + " WHERE t.task_name = 'Remaining setup' AND t.completed_at IS NULL"
                + " ORDER BY t.due_date LIMIT 2", Map.of(), String.class);
        final long revokeCount = queryCount(
                "SELECT count(*) FROM hr.employee_exit WHERE status IN ('APPROVED', 'IN_PROGRESS')");
        final Map<String, Object> nextRevoke = firstRow(
                "SELECT e.first_name || ' ' || e.last_name AS name, x.last_working_day AS d"
                + " FROM hr.employee_exit x JOIN hr.employee e ON e.id = x.employee_id"
                + " WHERE x.status IN ('APPROVED', 'IN_PROGRESS')"
                + " ORDER BY x.last_working_day LIMIT 1");
        final long notReturned = queryCount(
                "SELECT count(*) FROM hr.v_unreturned_assets WHERE asset_status = 'NOT_RETURNED'");

        return new ItAdminDashboardDto(equipmentRequests, openSetupItems, tasks,
                (String) nextEquipment.get("name"), toLocalDate(nextEquipment.get("d")),
                setupNames,
                revokeCount,
                (String) nextRevoke.get("name"), toLocalDate(nextRevoke.get("d")),
                notReturned);
    }

    /** First row of a query as column -> value, or an empty map when there is none. */
    private Map<String, Object> firstRow(final String sql) {
        final List<Map<String, Object>> rows = jdbc.queryForList(sql, Map.of());
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private long queryCount(final String sql) {
        final Long n = jdbc.queryForObject(sql, Map.of(), Long.class);
        return n != null ? n : 0L;
    }

    /** JDBC date column value -> LocalDate (null-safe). */
    private static LocalDate toLocalDate(final Object value) {
        return value instanceof java.sql.Date d ? d.toLocalDate() : null;
    }

    // ── Shared helper ─────────────────────────────────────────────────────────

    /**
     * Maps the caller's open tasks (from A36's My Tasks query) to dashboard task DTOs.
     *
     * <p>Non-obvious stream pipeline: {@link TaskService#getMyTasks} already applies the
     * role-specific filtering (HR sees onboarding tasks, IT admin sees equipment/setup tasks,
     * etc.). This mapping simply reshapes the result into the dashboard's own flat DTO without
     * re-implementing any task-selection logic.</p>
     *
     * @param caller the authenticated employee
     * @return dashboard task DTOs, same order as the My Tasks list (due date ascending)
     */
    private List<DashboardTaskDto> toTaskDtos(final Employee caller) {
        return taskService.getMyTasks(caller, null, null, 0).content().stream()
                .map(t -> new DashboardTaskDto(t.taskId(), t.title(), t.employeeId(),
                        t.dueDate(), t.dueState(), t.employeeName(), t.employeeCode(), t.relatedId()))
                .toList();
    }
}
