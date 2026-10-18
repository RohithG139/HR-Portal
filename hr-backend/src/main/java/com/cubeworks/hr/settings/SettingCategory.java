package com.cubeworks.hr.settings;

/**
 * Top-level category of a configurable setting.
 *
 * <p>Stored as {@code varchar(10)} with a CHECK constraint in {@code hr.setting.category}.
 * The category determines what the other value columns mean:</p>
 * <ul>
 *   <li>{@link #TIMER} — background job intervals and durations; {@code value_int +
 *       value_unit} carry the period (e.g. 7 DAYS for bench check frequency).</li>
 *   <li>{@link #TASK_DUE} — number of days an engine task has before it becomes overdue;
 *       {@code code} matches the engine task name and {@code value_int} is the day count.</li>
 * </ul>
 *
 * <p>A DB CHECK enforces that TASK_DUE rows always have {@code value_int IS NOT NULL AND
 * value_unit = 'DAYS'} (R-ARC-7).</p>
 */
public enum SettingCategory {
    /** Background-scheduler setting: frequency or duration of a timed process. */
    TIMER,
    /** Per-task due-date setting: how many days after assignment a task becomes overdue. */
    TASK_DUE
}
