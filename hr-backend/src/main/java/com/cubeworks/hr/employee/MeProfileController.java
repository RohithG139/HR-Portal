package com.cubeworks.hr.employee;

import com.cubeworks.hr.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the "My Profile" self-service screen (A29, A30, WS-03).
 *
 * <h2>Covered endpoints</h2>
 * <ul>
 *   <li>A29 {@code GET  /api/me/profile} — every role: view own full profile</li>
 *   <li>A30 {@code PUT  /api/me/profile} — every role: update own contact details</li>
 * </ul>
 *
 * <p>Open to every authenticated role, not just EMPLOYEE (G-13: "Every role includes the
 * Employee functions for the user's own record" — FRD-00). HR uses
 * {@code GET /api/employees/{id}} to view <em>other</em> employees' profiles, which is a
 * separate, HR-only capability (A31/A32/A33) unaffected by this endpoint being open to all
 * roles here. The caller can only ever see and update their own data — no path variable is
 * needed because the subject is always the authenticated caller, so there is no cross-employee
 * exposure from the role gate being open (R-API-2's ownership check here is inherent to the
 * endpoint shape, not role-based).</p>
 *
 * <p>Controllers stay thin (R-CODE-1): all business logic, cross-field validation, and
 * optimistic-lock handling live in {@link EmployeeService}.</p>
 */
@RestController
@RequestMapping("/api/me/profile")
public class MeProfileController {

    private final EmployeeService employeeService;
    private final CurrentUser currentUser;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param employeeService service holding all employee business logic
     * @param currentUser     resolves the authenticated employee from the JWT
     */
    public MeProfileController(final EmployeeService employeeService,
                                final CurrentUser currentUser) {
        this.employeeService = employeeService;
        this.currentUser = currentUser;
    }

    /**
     * A29 — Returns the authenticated employee's own full profile (WS-03).
     *
     * <p>The response includes all contact fields and employment meta-data but no salary,
     * bank details or ID-document numbers (R-DB-4). The {@code version} field is included
     * so the client can send it back on the next PUT for optimistic-lock validation.</p>
     *
     * @return 200 with the caller's {@link EmployeeService.EmployeeProfileDto}
     */
    @GetMapping
    public ResponseEntity<EmployeeService.EmployeeProfileDto> getMyProfile() {
        final Employee caller = currentUser.require();
        return ResponseEntity.ok(employeeService.getMyProfile(caller));
    }

    /**
     * A30 — Updates the authenticated employee's own contact and emergency-contact details (WS-03).
     *
     * <p>Employment-immutable fields (firstName, lastName, dateOfBirth, workEmail, appRole) are
     * silently ignored even if present in the JSON body (WS-03 restriction). The caller must
     * supply the current {@code version} for optimistic-lock validation (R-DB-5).</p>
     *
     * <p>Bean Validation fires before the service call:
     * {@code mobilePhone} must match {@code +[0-9 ]{5,20}} and {@code postcode} must match
     * {@code [0-9]{4} [A-Z]{2}}. A cross-field validation error (emergencyName without
     * emergencyPhone) is detected in the service and returned as 400 VALIDATION_FAILED.</p>
     *
     * @param request the contact fields to update; null fields are left unchanged
     * @return 200 with the updated {@link EmployeeService.EmployeeProfileDto},
     *         or 400 VALIDATION_FAILED on input errors,
     *         or 409 STALE_VERSION on optimistic-lock mismatch (R-DB-5)
     */
    @PutMapping
    public ResponseEntity<EmployeeService.EmployeeProfileDto> updateMyProfile(
            @Valid @RequestBody final EmployeeService.UpdateMyProfileRequest request) {
        final Employee caller = currentUser.require();
        return ResponseEntity.ok(employeeService.updateMyProfile(caller, request));
    }
}
