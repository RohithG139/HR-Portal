package com.cubeworks.hr.controller.workspace;

import com.cubeworks.hr.dto.workspace.ManagerTasksResponse;
import com.cubeworks.hr.dto.workspace.TransferManagerRequest;
import com.cubeworks.hr.dto.workspace.TransferManagerResponse;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.security.CurrentUser;
import com.cubeworks.hr.service.workspace.TransferManagerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the Transfer Manager feature (W09/W10, FRD-01 Wave 3).
 *
 * <h2>Covered endpoints</h2>
 * <ul>
 *   <li>W09 {@code GET /api/employees/{id}/manager-tasks} — HR only; returns the open engine
 *       tasks for employee {@code id} that are currently owned by that employee's manager,
 *       so the HR user can tick which tasks to move to the new manager.</li>
 *   <li>W10 {@code POST /api/employees/{id}/manager-transfers} — HR only; validates and
 *       schedules (or immediately applies) the manager transfer.</li>
 * </ul>
 *
 * <p>Both endpoints are HR-only (spec §6 role column). Role is resolved from the JWT, never
 * from a request parameter (R-API-2). All business logic lives in {@link TransferManagerService}
 * (R-CODE-1: controllers stay thin).</p>
 *
 * <p>G-13 self-exclusion is enforced in the service layer via
 * {@link com.cubeworks.hr.security.SelfActionGuard#forbidSelf(Employee, Long)} — an HR
 * employee cannot transfer the manager for their own employee record.</p>
 */
@RestController
@RequestMapping("/api/employees")
public class TransferManagerController {

    private final TransferManagerService transferManagerService;
    private final CurrentUser currentUser;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param transferManagerService provides W09/W10 business logic
     * @param currentUser            resolves the authenticated employee from the JWT
     */
    public TransferManagerController(final TransferManagerService transferManagerService,
                                      final CurrentUser currentUser) {
        this.transferManagerService = transferManagerService;
        this.currentUser = currentUser;
    }

    /**
     * W09 — Returns the open engine tasks for an employee that are owned by their current manager.
     *
     * <p>Used to populate the tick-list in the Transfer Manager modal: the HR user sees which
     * tasks the current manager owns and selects which ones to move to the new manager.</p>
     *
     * <p>If the employee has no current manager, returns an empty {@code openManagerTasks} list.</p>
     *
     * @param id the surrogate PK of the employee to query
     * @return 200 with the tick-list response; 404 if the employee does not exist
     */
    @GetMapping("/{id}/manager-tasks")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ManagerTasksResponse> getManagerTasks(
            @PathVariable final Long id) {
        final Employee caller = currentUser.require();
        return ResponseEntity.ok(transferManagerService.getManagerTasks(id, caller));
    }

    /**
     * W10 — Creates a manager transfer request for the given employee.
     *
     * <p>The transfer is scheduled if {@code effectiveDate} is in the future, or applied
     * immediately (in an after-commit hook) if {@code effectiveDate} is today or earlier.
     * A failed same-day apply does not roll back the scheduling record; the scheduler retries
     * it on its next tick (R-ARC-5).</p>
     *
     * <p>Validation errors return {@code 400 {"code":"VALIDATION_FAILED","errors":[...]}} (R-API-3).
     * G-13: an HR employee cannot transfer the manager for their own record — returns {@code 403}.</p>
     *
     * @param id      the surrogate PK of the employee whose manager to transfer
     * @param request the transfer request body
     * @return 201 with the new {@code changeId}
     */
    @PostMapping("/{id}/manager-transfers")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<TransferManagerResponse> createTransfer(
            @PathVariable final Long id,
            @RequestBody @Valid final TransferManagerRequest request) {
        final Employee caller = currentUser.require();
        final TransferManagerResponse response = transferManagerService.createTransfer(id, request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
