package com.cubeworks.hr.settings;

/**
 * Text value for the {@code KPI_CYCLE} setting — the only setting that uses
 * {@code value_text} instead of {@code value_int + value_unit}.
 *
 * <p>Stored as {@code varchar(20)} with a CHECK constraint in {@code hr.setting.value_text}.
 * A DB CHECK enforces {@code (code = 'KPI_CYCLE') = (value_text IS NOT NULL)}, preventing
 * other settings from accidentally using this field.</p>
 */
public enum SettingValueText {
    /** KPIs are measured and reset every quarter. */
    QUARTERLY,
    /** KPIs are measured and reset every half year. */
    HALF_YEARLY,
    /** KPIs are measured and reset once a year. */
    YEARLY
}
