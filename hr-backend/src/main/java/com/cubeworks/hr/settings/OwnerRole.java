package com.cubeworks.hr.settings;

/**
 * The role responsible for completing a checklist item.
 *
 * <p>Stored as {@code varchar(10)} with a CHECK constraint in
 * {@code hr.checklist_template_item.owner_role} and snapshotted into
 * {@code hr.checklist_item.owner_role}.</p>
 *
 * <p>Distinct from {@link com.cubeworks.hr.employee.AppRole}: this enum adds
 * {@link #SYSTEM} for items completed automatically by the backend
 * (e.g. when the engine fires a system task).</p>
 */
public enum OwnerRole {
    /** HR team member performs this item. */
    HR,
    /** IT administrator performs this item. */
    IT_ADMIN,
    /** Line manager performs this item. */
    MANAGER,
    /** The employee themselves performs this item. */
    EMPLOYEE,
    /** The backend system completes this item automatically. */
    SYSTEM
}
