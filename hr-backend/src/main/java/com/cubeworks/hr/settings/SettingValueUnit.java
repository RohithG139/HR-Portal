package com.cubeworks.hr.settings;

/**
 * Unit for the numeric {@code value_int} in a {@link Setting} row.
 *
 * <p>Stored as {@code varchar(12)} with a CHECK constraint in {@code hr.setting.value_unit}.
 * Applicable to {@link SettingCategory#TIMER} rows; {@link SettingCategory#TASK_DUE} rows
 * always use {@code DAYS} (enforced by a DB CHECK).</p>
 */
public enum SettingValueUnit {
    /** Calendar days. */
    DAYS,
    /** Seconds — used for short-interval scheduler tests. */
    SECONDS,
    /** Minutes — used for medium-interval timers. */
    MINUTES,
    /** Characters — used for text-length constraints such as password minimum length. */
    CHARACTERS
}
