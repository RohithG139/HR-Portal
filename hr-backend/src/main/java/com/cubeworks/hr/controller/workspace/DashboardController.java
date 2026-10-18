package com.cubeworks.hr.controller.workspace;

import com.cubeworks.hr.dto.workspace.EmployeeDashboardDto;
import com.cubeworks.hr.dto.workspace.HrDashboardDto;
import com.cubeworks.hr.dto.workspace.ItAdminDashboardDto;
import com.cubeworks.hr.dto.workspace.ManagerDashboardDto;
import com.cubeworks.hr.employee.AppRole;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.employee.EmployeeStage;
import com.cubeworks.hr.security.CurrentUser;
import com.cubeworks.hr.service.workspace.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the role-dispatched dashboard endpoint (W01, FRD-01 Wave 3).
 *
 * <h2>Covered endpoints</h2>
 * <ul>
 *   <li>W01 {@code GET /api/dashboard} — single endpoint, role-dispatched DTO shape</li>
 * </ul>
 *
 * <p>The controller resolves the caller's role from the authenticated principal and
 * dispatches to the appropriate service method. The role is <strong>never</strong> a
 * request parameter (R-API-2). All business logic lives in {@link DashboardService}
 * (R-CODE-1: controllers stay thin).</p>
 *
 * <p>WS-01: the dashboard and menu shown depend only on the user's role (enforced here by
 * the role-dispatch switch, not only by frontend routing).</p>
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUser currentUser;

    /**
     * Constructor injection (R-CODE-1).
     *
     * <p>Fix #11: {@link TeamService} is no longer injected here — it is now a field on
     * {@link DashboardService} (constructor-injected). The controller only delegates to the
     * service; no business logic or data retrieval happens here (R-CODE-1).</p>
     *
     * @param dashboardService builds the per-role dashboard DTO (includes TeamService internally)
     * @param currentUser      resolves the authenticated employee from the JWT
     */
    public DashboardController(final DashboardService dashboardService,
                                final CurrentUser currentUser) {
        this.dashboardService = dashboardService;
        this.currentUser = currentUser;
    }

    /**
     * W01 — Role-dispatched dashboard (FRD-01 v1.2 §4.2 / spec §6).
     *
     * <p>The server determines the response shape from the caller's role. The possible
     * response types are:</p>
     * <ul>
     *   <li>HR → {@link HrDashboardDto}</li>
     *   <li>Manager → {@link ManagerDashboardDto}</li>
     *   <li>Employee ({@code stage = 'ACTIVE'}) → {@link EmployeeDashboardDto}</li>
     *   <li>IT admin → {@link ItAdminDashboardDto}</li>
     * </ul>
     *
     * <p>An ONBOARDING-stage employee is served by F01's A25 endpoint; this endpoint returns
     * an empty {@link EmployeeDashboardDto} for them as a safe fallback (the frontend always
     * routes them to the onboarding dashboard instead of calling this endpoint).</p>
     *
     * @return 200 with the role-appropriate dashboard DTO
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('HR','MANAGER','EMPLOYEE','IT_ADMIN')")
    public ResponseEntity<Object> getDashboard() {
        final Employee caller = currentUser.require();
        final AppRole role = caller.getAppRole();

        // WS-01: the dashboard depends only on the user's role — dispatched server-side,
        // never from a request parameter (R-API-2).
        return switch (role) {
            case HR -> ResponseEntity.ok(dashboardService.buildHrDashboard(caller));
            case MANAGER -> ResponseEntity.ok(
                    dashboardService.buildManagerDashboard(caller));
            case EMPLOYEE -> ResponseEntity.ok(dashboardService.buildEmployeeDashboard(caller));
            case IT_ADMIN -> ResponseEntity.ok(dashboardService.buildItAdminDashboard(caller));
        };
    }
}
