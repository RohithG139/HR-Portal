package com.cubeworks.hr.settings;

/**
 * The lifecycle phase a checklist template applies to.
 *
 * <p>Stored as {@code varchar(12)} with a CHECK constraint in {@code hr.checklist_template.kind}.
 * A template is either for onboarding or for offboarding; its kind determines which phase's
 * checklist items are instantiated from it.</p>
 */
public enum TemplateKind {
    /** Template items are instantiated during an employee's onboarding. */
    ONBOARDING,
    /** Template items are instantiated during an employee's offboarding. */
    OFFBOARDING
}
