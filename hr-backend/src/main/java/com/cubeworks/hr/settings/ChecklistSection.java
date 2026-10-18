package com.cubeworks.hr.settings;

/**
 * Logical grouping of a checklist item within the onboarding or offboarding checklist.
 *
 * <p>Stored as {@code varchar(30)} with a CHECK constraint in
 * {@code hr.checklist_template_item.section} and snapshotted into
 * {@code hr.checklist_item.section}. The snapshot is intentional: editing or archiving
 * a template must not change in-progress checklists (ADM-04, data-model §5).</p>
 *
 * <p>Used in both {@link com.cubeworks.hr.settings.ChecklistTemplateItem} and
 * {@link com.cubeworks.hr.onboarding.ChecklistItem}.</p>
 */
public enum ChecklistSection {
    /** Document and administrative paperwork tasks. */
    DOCUMENTS_ADMIN,
    /** Equipment issued to the employee. */
    EQUIPMENT_ISSUED,
    /** Account creation and software installation tasks. */
    ACCOUNTS_INSTALLATIONS,
    /** Orientation and introduction tasks. */
    ORIENTATION,
    /** Return of issued assets during offboarding. */
    RETURN_ASSETS,
    /** Removal of system/account access during offboarding. */
    REMOVE_ACCESS,
    /** HR handover tasks during offboarding. */
    HR_HANDOVER,
    /** Reassignment of team or project responsibilities. */
    REASSIGN_TEAM
}
