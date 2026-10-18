package com.cubeworks.hr.employee;

/**
 * Application role assigned to an employee, controlling which menus, dashboards and API
 * endpoints they can access (R-UI-7, R-API-2, FRD-00 permissions table).
 *
 * <p>Stored as {@code varchar(10)} with a CHECK constraint in {@code hr.employee.app_role}.
 * Role checks are enforced server-side on every endpoint — hiding a button is never the
 * only protection (R-API-2).</p>
 *
 * <p>Distinct from {@link com.cubeworks.hr.settings.OwnerRole}, which includes the
 * {@code SYSTEM} value used for checklist task ownership.</p>
 */
public enum AppRole {
    /** HR administrator — full access to all employee data and settings. */
    HR,
    /** Line manager — access to their direct reports' profiles, tasks and reviews. */
    MANAGER,
    /** Regular employee — access to own profile, tasks and self-service screens. */
    EMPLOYEE,
    /** IT administrator — access to IT request screens and asset management. */
    IT_ADMIN
}
