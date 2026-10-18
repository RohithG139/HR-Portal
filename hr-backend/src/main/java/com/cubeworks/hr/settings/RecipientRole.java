package com.cubeworks.hr.settings;

/**
 * The role or group that receives a notification or e-mail for a given setting.
 *
 * <p>Stored as {@code varchar(15)} with a CHECK constraint in
 * {@code hr.setting.recipient_role}. Used by the notification and e-mail dispatch
 * infrastructure to determine who gets alerted when a timer or task event fires.</p>
 */
public enum RecipientRole {
    /** Only the line manager of the relevant employee. */
    MANAGER,
    /** Only HR administrators. */
    HR,
    /** Both the manager and HR. */
    MANAGER_HR,
    /** The employee themselves. */
    EMPLOYEE,
    /** The owner of the specific task (may be any role). */
    TASK_OWNER,
    /** IT administrator. */
    IT_ADMIN,
    /** Every active user in the system. */
    ALL_USERS,
    /** Only the new user (used for welcome / invitation notifications). */
    NEW_USER,
    /** A single user specified contextually. */
    USER
}
