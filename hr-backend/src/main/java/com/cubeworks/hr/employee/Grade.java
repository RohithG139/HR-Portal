package com.cubeworks.hr.employee;

/**
 * Seniority grade for an employee, stored as {@code varchar} with a CHECK constraint in the
 * {@code hr.employee} table (R-DB-2).
 *
 * <p>The FRD treats grade as a fixed enumeration (data-model §6, assumption 1). If HR must
 * manage grades dynamically in a future release, this enum becomes a separate table.</p>
 */
public enum Grade {
    JUNIOR,
    MEDIOR,
    SENIOR,
    /** Senior level I — distinct from {@link #SENIOR} for promotion tracking. */
    SENIOR_I,
    /** Senior level II. */
    SENIOR_II
}
