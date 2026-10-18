package com.cubeworks.hr.controller.settings;

import com.cubeworks.hr.dto.settings.TimerDto;
import com.cubeworks.hr.dto.settings.TimerUpdateRequest;
import com.cubeworks.hr.security.CurrentUser;
import com.cubeworks.hr.service.settings.TimerSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for reading and updating timer/rule settings (FRD-08 §6 C29–C30).
 *
 * <p>All endpoints require the {@code HR} role (FRD-08 §2). The settings themselves are
 * identified by their immutable {@code code} (ADM-01). Controllers stay thin (R-CODE-1):
 * all logic lives in {@link TimerSettingsService}.</p>
 *
 * <p>The wireframe's single "Save" button on the Timers &amp; rules tab fires one
 * {@code PUT /api/settings/timers/{code}} call per changed row — the frontend sends them
 * all at once, but each is a separate server-side request (per FRD-08 §6 C30 note).</p>
 */
@RestController
@RequestMapping("/api/settings/timers")
@PreAuthorize("hasRole('HR')")
public class TimerSettingsController {

    private final TimerSettingsService timerSettingsService;
    private final CurrentUser currentUser;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param timerSettingsService the service holding all timer-setting logic
     * @param currentUser          resolves the authenticated employee from the JWT
     */
    public TimerSettingsController(final TimerSettingsService timerSettingsService,
                                   final CurrentUser currentUser) {
        this.timerSettingsService = timerSettingsService;
        this.currentUser = currentUser;
    }

    /**
     * C29 — Returns all 17 timer/rule settings.
     *
     * <p>The {@code code}, {@code category}, {@code module} and {@code label} fields are
     * read-only (ADM-01) — returned for display, never accepted in update requests.</p>
     *
     * @return 200 with all timer settings
     */
    @GetMapping
    public ResponseEntity<List<TimerDto>> listTimers() {
        return ResponseEntity.ok(timerSettingsService.listAll());
    }

    /**
     * C30 — Updates a single timer/rule setting by code.
     *
     * <p>Only {@code valueInt}, {@code valueText}, {@code recipientRole} and {@code enabled}
     * are writable (ADM-01). Null fields are left unchanged. Validation rules per code:</p>
     * <ul>
     *   <li>Most settings: {@code valueInt} must be &gt; 0 ("Enter a whole number greater than 0.").</li>
     *   <li>{@code PASSWORD_MIN_LENGTH}: {@code valueInt} must be 8–64 ("Enter a number between 8 and 64.").</li>
     *   <li>{@code KPI_CYCLE}: {@code valueText} must be QUARTERLY/HALF_YEARLY/YEARLY ("Choose a KPI cycle.").</li>
     * </ul>
     *
     * @param code    the setting code (path key, ADM-01)
     * @param request the updated writable fields
     * @return 200 with the updated setting
     */
    @PutMapping("/{code}")
    public ResponseEntity<TimerDto> updateTimer(
            @PathVariable final String code,
            @RequestBody final TimerUpdateRequest request) {
        final var actor = currentUser.require();
        return ResponseEntity.ok(timerSettingsService.update(code, request, actor));
    }
}
