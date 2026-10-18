package com.cubeworks.hr.settings;

/**
 * The behavioural kind of a checklist item, which determines how the UI renders it and
 * what side-effect completing it triggers.
 *
 * <p>Stored as {@code varchar(20)} with a CHECK constraint in
 * {@code hr.checklist_template_item.item_kind} and snapshotted into
 * {@code hr.checklist_item.item_kind} (ADM-04 snapshot rule).</p>
 */
public enum ItemKind {
    /** A generic task with no special UI or backend behaviour. */
    GENERIC,
    /** The employee must read and acknowledge a policy document (opens a PDF pop-up). */
    POLICY_ACK,
    /** The employee must complete their own HR profile. */
    PROFILE,
    /** The employee must confirm receipt of issued equipment. */
    EQUIPMENT_CONFIRM,
    /** IT must provision a physical equipment item (creates/links an {@code asset} row). */
    EQUIPMENT,
    /** IT must provision a system account or access right. */
    ACCOUNT,
    /** An exit interview must be conducted. */
    EXIT_INTERVIEW,
    /** All project assignments must be formally ended. */
    ASSIGNMENTS_END
}
