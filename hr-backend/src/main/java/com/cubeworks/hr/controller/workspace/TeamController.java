package com.cubeworks.hr.controller.workspace;

import com.cubeworks.hr.dto.workspace.TeamPageDto;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.security.CurrentUser;
import com.cubeworks.hr.service.reports.ReportExcelBuilder;
import com.cubeworks.hr.service.workspace.TeamService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the Manager's My Team list (W05/W06, FRD-01 Wave 3).
 *
 * <h2>Covered endpoints</h2>
 * <ul>
 *   <li>W05 {@code GET /api/team} — paginated, filterable team list</li>
 *   <li>W06 {@code GET /api/team/export} — Excel export with the same filters</li>
 * </ul>
 *
 * <p>WS-02 scoping is enforced in {@link TeamService}: all queries are
 * {@code employee.manager_id = caller.id}, regardless of which filters are applied.
 * The controller is thin (R-CODE-1) — all business logic lives in the service.</p>
 */
@RestController
@RequestMapping("/api/team")
public class TeamController {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final TeamService teamService;
    private final CurrentUser currentUser;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param teamService provides WS-02-scoped team queries
     * @param currentUser resolves the authenticated employee from the JWT
     */
    public TeamController(final TeamService teamService, final CurrentUser currentUser) {
        this.teamService = teamService;
        this.currentUser = currentUser;
    }

    /**
     * W05 — Paginated list of the manager's direct reports (My Team screen, FRD-01 Wave 3).
     *
     * <p>WS-02: only employees whose {@code manager_id = caller.id} are returned — enforced
     * server-side in the service, not only by hiding the My Team nav item (R-UI-7).</p>
     *
     * @param status filter by team-member status ("All"/"Onboarding"/"On project"/"Bench"/"PIP");
     *               "On project", "Bench" and "PIP" return empty (deferred to F04/F07)
     * @param q      free-text search on employee name
     * @param sort   sort column (default: lastName)
     * @param page   zero-based page index (default: 0)
     * @param size   page size (default: 20)
     * @return 200 with paginated team member list
     */
    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<TeamPageDto> listTeam(
            @RequestParam(required = false) final String status,
            @RequestParam(required = false) final String q,
            @RequestParam(required = false) final String sort,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        final Employee caller = currentUser.require();
        // Fix #7: clamp page/size to safe bounds. exportTeam intentionally calls listTeam with
        // size=10_000 and is NOT clamped — only this paginated endpoint is.
        final int clampedPage = Math.max(page, 0);
        final int clampedSize = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(teamService.listTeam(caller, status, q, sort, clampedPage, clampedSize));
    }

    /**
     * W06 — Excel export of the manager's team with the same filters as W05 (R-UI-2).
     *
     * <p>Follows the {@link ReportExcelBuilder} pattern (Wave 2): the service provides the data,
     * the builder serialises to Excel, and the controller returns the byte array — single-source
     * of data guarantee (the same service method as W05 is called with size=10_000 to fetch all).</p>
     *
     * @param status filter by team-member status
     * @param q      free-text search
     * @return 200 with an Excel attachment ({@code team.xlsx})
     */
    @GetMapping("/export")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<byte[]> exportTeam(
            @RequestParam(required = false) final String status,
            @RequestParam(required = false) final String q) {
        final Employee caller = currentUser.require();
        // Load all matching rows (no pagination) for the export.
        // Large teams: a limit of 10_000 matches what EmployeeController.exportEmployees uses.
        final TeamPageDto data = teamService.listTeam(caller, status, q, "lastName", 0, 10_000);
        final byte[] bytes = ReportExcelBuilder.buildTeam(data.items());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"team.xlsx\"")
                .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
                .body(bytes);
    }
}
