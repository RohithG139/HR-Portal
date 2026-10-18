package com.cubeworks.hr.employee;

/**
 * Keycloak account provisioning status for an employee.
 *
 * <p>Stored as {@code varchar(12)} with a CHECK constraint in {@code hr.employee.account_status}.
 * The default value at row creation is {@code NOT_INVITED}; HR sends an invitation e-mail
 * (template {@code ONB_INVITE}) which transitions the status to {@code INVITED}.
 * The employee's first login flips it to {@code ACTIVE}.</p>
 */
public enum AccountStatus {
    /** Keycloak account not yet created; invitation e-mail not yet sent. */
    NOT_INVITED,
    /** Invitation e-mail sent; employee has not yet logged in for the first time. */
    INVITED,
    /** Employee has completed first login; account is fully operational. */
    ACTIVE,
    /** Account has been disabled (e.g. during offboarding or after termination). */
    DISABLED
}
