package com.cubeworks.hr.settings;

import com.cubeworks.hr.employee.AppRole;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Provides reference / lookup data for the onboarding form dropdowns and IT request form.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>A01 {@code GET /api/me} — current user identity and menu</li>
 *   <li>A02 {@code GET /api/lookups/onboarding} — HR form dropdowns</li>
 *   <li>A03 {@code GET /api/lookups/it} — IT request option lists (D-06)</li>
 * </ul>
 *
 * <p>Controllers stay thin (R-CODE-1): all logic lives in {@link LookupService}.</p>
 */
@RestController
@RequestMapping("/api")
public class LookupController {

    private final LookupService lookupService;
    private final CurrentUser currentUser;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param lookupService service providing lookup data
     * @param currentUser   resolves the authenticated employee from the JWT
     */
    public LookupController(final LookupService lookupService, final CurrentUser currentUser) {
        this.lookupService = lookupService;
        this.currentUser = currentUser;
    }

    /**
     * A01 — Returns the current user's identity, initials, role, stage and menu items.
     *
     * <p>The {@code menu[]} array lists only the items that F01 builds, per §4. This
     * endpoint is called on every page load to re-hydrate the top bar.</p>
     *
     * <p>{@code stage} was added 2026-10-17 (F03/FRD-01 Wave 3, Gate 4 finding) — the
     * dashboard (W01) needs it to pick between the Employee new-joiner dashboard
     * (F01's existing {@code stage='ONBOARDING'} build) and the active-stage dashboard
     * (this wave's build); without it, the newly-built active-stage dashboard component
     * had no way to ever be reached. Purely additive — no existing field changed.</p>
     *
     * @return 200 with the current user's details
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMe() {
        final Employee emp = currentUser.require();
        final List<String> menu = menuItemsForRole(emp.getAppRole());
        final String fullName = emp.getFirstName() + " " + emp.getLastName();
        final String initials = String.valueOf(emp.getFirstName().charAt(0))
                + emp.getLastName().charAt(0);
        // Shown in the top-bar user menu; work email is null until IT creates it
        final String email = emp.getWorkEmail() != null ? emp.getWorkEmail() : emp.getPrivateEmail();
        return ResponseEntity.ok(Map.of(
                "employeeId", emp.getId(),
                "name", fullName,
                "initials", initials.toUpperCase(),
                "email", email,
                "appRole", emp.getAppRole().name(),
                "stage", emp.getStage().name(),
                "menu", menu
        ));
    }

    /**
     * A02 — Returns all reference data needed to populate the onboarding form dropdowns.
     *
     * @return 200 with departments, job titles, managers, buddies, grades, roles, templates
     */
    @GetMapping("/lookups/onboarding")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<LookupService.OnboardingLookupsDto> getOnboardingLookups() {
        return ResponseEntity.ok(lookupService.getOnboardingLookups());
    }

    /**
     * A03 — Returns the fixed IT option lists for the equipment request form (D-06).
     *
     * @return 200 with laptop models, keyboard layouts, delivery methods, accessories, accounts
     */
    @GetMapping("/lookups/it")
    @PreAuthorize("hasRole('HR') or hasRole('IT_ADMIN')")
    public ResponseEntity<LookupService.ItLookupsDto> getItLookups() {
        return ResponseEntity.ok(lookupService.getItLookups());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the menu item codes available to the given role, limited to what F01 builds
     * (§4). Later features add entries here as they are built.
     *
     * @param role the application role
     * @return ordered list of menu item codes
     */
    private List<String> menuItemsForRole(final AppRole role) {
        // Fix #12: HR and MANAGER now have a real dashboard (FRD-01 Wave 3 W01).
        // "DASHBOARD" is the first entry for both, matching the wireframe order and the
        // convention already established for EMPLOYEE and IT_ADMIN.
        return switch (role) {
            case HR -> List.of("DASHBOARD", "EMPLOYEES", "MY_TASKS", "NOTIFICATIONS");
            case MANAGER -> List.of("DASHBOARD", "MY_TEAM", "MY_TASKS", "NOTIFICATIONS");
            case EMPLOYEE -> List.of("DASHBOARD", "MY_TASKS", "MY_PROFILE", "NOTIFICATIONS");
            case IT_ADMIN -> List.of("DASHBOARD", "MY_TASKS", "EMPLOYEES", "NOTIFICATIONS");
        };
    }
}
