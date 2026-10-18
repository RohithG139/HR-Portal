package com.cubeworks.hr.dto.settings;

/**
 * Response body for one timer/rule setting (FRD-08 §6 C29).
 *
 * <p>The {@code code}, {@code category}, {@code module} and {@code label} fields are
 * read-only in the UI (ADM-01) — they are returned for display purposes but never
 * accepted in update requests.</p>
 *
 * @param code          machine-readable setting code; never editable (ADM-01)
 * @param category      setting category (e.g. "TIMER")
 * @param module        the feature module this setting belongs to
 * @param label         human-readable label shown in the UI
 * @param valueInt      numeric value (days/minutes/etc.); null for text-valued settings
 * @param valueUnit     unit for {@code valueInt} (e.g. "DAYS", "MINUTES", "CHARACTERS")
 * @param valueText     text value; only non-null for {@code KPI_CYCLE}
 * @param recipientRole who is notified when this timer fires; null if no notification
 * @param canDisable    whether HR can toggle the enabled state
 * @param enabled       whether this setting is currently active
 * @param version       optimistic-lock version — must be sent back in {@code PUT} requests
 *                      (R-DB-5, spec §12 D-10)
 */
public record TimerDto(
        String code,
        String category,
        String module,
        String label,
        Integer valueInt,
        String valueUnit,
        String valueText,
        String recipientRole,
        boolean canDisable,
        boolean enabled,
        Integer version) {
}
