package com.cubeworks.hr.service.settings;

import com.cubeworks.hr.common.audit.AuditLogService;
import com.cubeworks.hr.common.error.ApiException;
import com.cubeworks.hr.common.error.ErrorCodes;
import com.cubeworks.hr.dto.settings.TimerDto;
import com.cubeworks.hr.dto.settings.TimerUpdateRequest;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.settings.RecipientRole;
import com.cubeworks.hr.settings.Setting;
import com.cubeworks.hr.settings.SettingCategory;
import com.cubeworks.hr.settings.SettingRepository;
import com.cubeworks.hr.settings.SettingValueText;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service for reading and updating timer/rule settings (FRD-08 §6 C29–C30).
 *
 * <p>Key business rules:</p>
 * <ul>
 *   <li>ADM-01: {@code code}, {@code category}, {@code module} and {@code label} are never
 *       accepted in update requests — only {@code valueInt}, {@code valueText},
 *       {@code recipientRole} and {@code enabled} are writable.</li>
 *   <li>ADM-02: updating a setting only affects new cycles; running cases read the
 *       setting at their start time and keep that value (enforced by design, not code).</li>
 *   <li>Validation rules per setting code (FRD-08 §5 S6):
 *     <ul>
 *       <li>Most integer-valued settings: {@code valueInt} must be &gt; 0.</li>
 *       <li>{@code PASSWORD_MIN_LENGTH}: {@code valueInt} must be between 8 and 64.</li>
 *       <li>{@code KPI_CYCLE}: {@code valueText} must be QUARTERLY/HALF_YEARLY/YEARLY.</li>
 *     </ul>
 *   </li>
 *   <li>ADM-05: every write is audited in the same transaction (R-API-5).</li>
 * </ul>
 */
@Service
public class TimerSettingsService {

    private static final Logger log = LoggerFactory.getLogger(TimerSettingsService.class);

    /** Setting code for the password-length rule, which has a different valid range. */
    private static final String PASSWORD_MIN_LENGTH = "PASSWORD_MIN_LENGTH";
    /** Setting code for the KPI cycle, which uses valueText instead of valueInt. */
    private static final String KPI_CYCLE = "KPI_CYCLE";

    private final SettingRepository settingRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param settingRepository reads/writes {@code hr.setting}
     * @param auditLogService   writes audit entries (ADM-05)
     * @param objectMapper      serialises before/after snapshots
     * @param clock             application-wide clock bean
     */
    public TimerSettingsService(final SettingRepository settingRepository,
                                 final AuditLogService auditLogService,
                                 final ObjectMapper objectMapper,
                                 final Clock clock) {
        this.settingRepository = settingRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * C29 — Returns all 17 timer/rule settings.
     *
     * <p>All settings are returned regardless of category because the Settings screen's
     * "Timers &amp; rules" tab shows all rows including non-TIMER category rows
     * (PASSWORD_MIN_LENGTH, NOTICE_PERIOD_*, KPI_CYCLE — stored as TIMER per D-06).</p>
     *
     * @return all settings ordered by module then label
     */
    public List<TimerDto> listAll() {
        return settingRepository.findAll().stream()
                .filter(s -> s.getCategory() == SettingCategory.TIMER)
                .sorted((a, b) -> {
                    final int byModule = a.getModule().compareToIgnoreCase(b.getModule());
                    return byModule != 0 ? byModule : a.getLabel().compareToIgnoreCase(b.getLabel());
                })
                .map(this::toDto)
                .toList();
    }

    /**
     * C30 — Updates a single timer/rule setting by code.
     *
     * <p>Transaction boundary: the setting UPDATE and the audit_log INSERT are committed
     * atomically — partial failure rolls back both (ADM-05).</p>
     *
     * <p>ADM-01 compliance: {@code code}, {@code category}, {@code module} and {@code label}
     * are taken from the URL and DB; they are never read from the request body.</p>
     *
     * <p>Only non-null fields in the request are applied; null means "leave unchanged".</p>
     *
     * @param code    the setting code (e.g. "BENCH_CHECK", "KPI_CYCLE")
     * @param request the writable fields; null fields are left unchanged
     * @param actor   the HR user performing the action
     * @return the updated setting
     * @throws ApiException 404 NOT_FOUND if no setting with that code exists
     * @throws ApiException 400 VALIDATION_FAILED for value range violations
     */
    @Transactional
    public TimerDto update(final String code,
                            final TimerUpdateRequest request,
                            final Employee actor) {
        final Setting setting = settingRepository.findById(code)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Setting not found: " + code));

        // This endpoint is scoped to the Timers & rules tab (category=TIMER, see listAll()) —
        // without this check, a caller could edit a TASK_DUE row this tab never shows them.
        if (setting.getCategory() != SettingCategory.TIMER) {
            throw new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Setting not found: " + code);
        }

        // Optimistic-lock check (R-DB-5, spec §12 D-10) — same pattern as the e-mail template
        // and appraisal form update endpoints.
        if (!Objects.equals(setting.getVersion(), request.version())) {
            throw new ApiException(ErrorCodes.STALE_VERSION, HttpStatus.CONFLICT,
                    "Version conflict — reload and retry");
        }

        final String before = snapshotSetting(setting);

        // Apply and validate valueInt if provided
        if (request.valueInt() != null) {
            validateValueInt(code, request.valueInt());
            setting.setValueInt(request.valueInt());
        }

        // Apply and validate valueText if provided
        if (request.valueText() != null) {
            validateValueText(code, request.valueText());
            final SettingValueText textEnum;
            try {
                textEnum = SettingValueText.valueOf(request.valueText());
            } catch (IllegalArgumentException e) {
                throw ApiException.fieldValidation(List.of(
                        Map.of("field", "valueText", "message", "Choose a KPI cycle.")));
            }
            setting.setValueText(textEnum);
        }

        // Apply recipientRole if provided
        if (request.recipientRole() != null) {
            final RecipientRole role;
            try {
                role = RecipientRole.valueOf(request.recipientRole());
            } catch (IllegalArgumentException e) {
                throw ApiException.fieldValidation(List.of(
                        Map.of("field", "recipientRole", "message", "This field is required.")));
            }
            setting.setRecipientRole(role);
        }

        // Apply enabled if provided; only allowed when can_disable = true
        if (request.enabled() != null) {
            if (!setting.isCanDisable() && Boolean.FALSE.equals(request.enabled())) {
                throw ApiException.fieldValidation(List.of(
                        Map.of("message", "This timer cannot be disabled.")));
            }
            setting.setEnabled(request.enabled());
        }

        final OffsetDateTime now = OffsetDateTime.now(clock);
        setting.setUpdatedAt(now);
        setting.setUpdatedBy(actor.getId());
        settingRepository.save(setting);

        auditLogService.record(null, "SETTING", null, "UPDATE",
                actor, before, snapshotSetting(setting));

        log.info("Setting updated: code={} by employee={}", code, actor.getId());
        return toDto(setting);
    }

    // ── Validation helpers ────────────────────────────────────────────────────

    /**
     * Validates the integer value based on the setting's specific rule.
     *
     * <ul>
     *   <li>{@code PASSWORD_MIN_LENGTH}: must be between 8 and 64 inclusive
     *       (FRD-08 §5 S6: "Enter a number between 8 and 64.").</li>
     *   <li>All others: must be &gt; 0
     *       (FRD-08 §5 S6: "Enter a whole number greater than 0.").</li>
     * </ul>
     */
    private void validateValueInt(final String code, final int value) {
        if (PASSWORD_MIN_LENGTH.equals(code)) {
            if (value < 8 || value > 64) {
                throw ApiException.fieldValidation(List.of(
                        Map.of("field", "valueInt", "message", "Enter a number between 8 and 64.")));
            }
        } else {
            if (value <= 0) {
                throw ApiException.fieldValidation(List.of(
                        Map.of("field", "valueInt",
                                "message", "Enter a whole number greater than 0.")));
            }
        }
    }

    /**
     * Validates the text value — only {@code KPI_CYCLE} allows a text value (DB CHECK).
     * The value must be one of QUARTERLY / HALF_YEARLY / YEARLY.
     */
    private void validateValueText(final String code, final String value) {
        if (!KPI_CYCLE.equals(code)) {
            throw ApiException.fieldValidation(List.of(
                    Map.of("message", "This setting does not accept a text value.")));
        }
        try {
            SettingValueText.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.fieldValidation(List.of(
                    Map.of("field", "valueText", "message", "Choose a KPI cycle.")));
        }
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private TimerDto toDto(final Setting s) {
        return new TimerDto(
                s.getCode(),
                s.getCategory().name(),
                s.getModule(),
                s.getLabel(),
                s.getValueInt(),
                s.getValueUnit() != null ? s.getValueUnit().name() : null,
                s.getValueText() != null ? s.getValueText().name() : null,
                s.getRecipientRole() != null ? s.getRecipientRole().name() : null,
                s.isCanDisable(),
                s.isEnabled(),
                s.getVersion());
    }

    private String snapshotSetting(final Setting s) {
        try {
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", s.getCode());
            m.put("valueInt", s.getValueInt());
            m.put("valueText", s.getValueText() != null ? s.getValueText().name() : null);
            m.put("recipientRole", s.getRecipientRole() != null ? s.getRecipientRole().name() : null);
            m.put("enabled", s.isEnabled());
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise setting snapshot", e);
            return "{}";
        }
    }
}
