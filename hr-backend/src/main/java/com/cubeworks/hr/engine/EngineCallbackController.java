package com.cubeworks.hr.engine;

import com.cubeworks.hr.onboarding.OnboardingEmailReleaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives inbound HTTP callbacks from the CaseFabric engine's own {@code ProcessTask}s (added
 * 2026-10-21, "mail triggered by the case engine").
 *
 * <h2>Not a normal API endpoint</h2>
 * <p>Every other controller in this app is called by hr-frontend on behalf of an authenticated
 * employee and is protected by a Keycloak JWT (R-API-2). This one is called by the engine itself
 * — a {@code HTTPCallDefinition} process task has no Keycloak identity of its own to present.
 * It is instead protected by a static shared secret header
 * ({@code cubeworks.engine.callback-secret}), checked here, and is explicitly permitted without
 * a JWT in {@link com.cubeworks.hr.security.SecurityConfig} (path {@code /api/internal/engine-events/**}).
 * The secret is baked into the matching {@code .process} definition's {@code http-headers} —
 * see {@code casefabric/getting-started/src/definitions/ReleaseOnboardingEmails.process}.</p>
 *
 * <h2>Only ever carries an ID</h2>
 * <p>Per R-ARC-3, the case file (and therefore anything a process task can send here) never
 * carries personal data — only the case-file-safe {@code Employee.employeeId}
 * ({@code employee_code}). Every handler here looks up the real employee from the database by
 * that code; nothing personal ever crosses from the engine into this controller.</p>
 */
@RestController
@RequestMapping("/api/internal/engine-events")
public class EngineCallbackController {

    private static final Logger log = LoggerFactory.getLogger(EngineCallbackController.class);

    private final OnboardingEmailReleaseService onboardingEmailReleaseService;
    private final String callbackSecret;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param onboardingEmailReleaseService releases the held onboarding e-mails
     * @param callbackSecret                shared secret every callback must present
     *                                      ({@code cubeworks.engine.callback-secret})
     */
    public EngineCallbackController(final OnboardingEmailReleaseService onboardingEmailReleaseService,
                                     @Value("${cubeworks.engine.callback-secret}") final String callbackSecret) {
        this.onboardingEmailReleaseService = onboardingEmailReleaseService;
        this.callbackSecret = callbackSecret;
    }

    /**
     * Request body for every engine callback below: just the case-file-safe employee code.
     *
     * @param employeeId the case file's {@code Employee.employeeId} (= {@code employee_code})
     */
    public record EngineEventRequest(String employeeId) {}

    /**
     * Called by the "Release onboarding emails" process task the moment an employee's case
     * actually starts. Releases "Welcome new employee" (HELD in the outbox) and triggers the
     * Keycloak set-password e-mail — see {@link OnboardingEmailReleaseService}.
     *
     * @param secret  the shared-secret header; checked against {@link #callbackSecret}
     * @param request the engine's callback body (employee code only)
     * @return 204 on success; 403 if the secret is missing or wrong; 404 if the employee
     *         code does not resolve (logged, not thrown — see
     *         {@link OnboardingEmailReleaseService#releaseOnboardingEmails(String)})
     */
    @PostMapping("/onboarding-started")
    public ResponseEntity<Void> onboardingStarted(
            @RequestHeader(value = "X-Engine-Callback-Secret", required = false) final String secret,
            @RequestBody final EngineEventRequest request) {
        // required = false so a missing header is a clean 403 here, same as a wrong one —
        // not Spring's own 400 MissingRequestHeaderException for an absent required header.
        if (!callbackSecret.equals(secret)) {
            log.warn("[EngineCallbackController] Rejected onboarding-started callback: bad secret");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        onboardingEmailReleaseService.releaseOnboardingEmails(request.employeeId());
        return ResponseEntity.noContent().build();
    }
}
