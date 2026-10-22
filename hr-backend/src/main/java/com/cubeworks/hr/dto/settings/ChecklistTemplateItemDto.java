package com.cubeworks.hr.dto.settings;

/**
 * Response body for a single checklist template item (FRD-08 §6 C08, C11, C12).
 *
 * @param id            the item's surrogate PK
 * @param name          item display name
 * @param section       the checklist section this item appears under
 * @param ownerRole     the role responsible for completing this item
 * @param mandatory     whether this item must be completed before the checklist can close
 * @param dueOffsetDays days after the employee's start date by which this item is due
 * @param sortOrder     display position within the section
 * @param itemKind      behavioural kind ({@code checklist_template_item.item_kind}), e.g. POLICY_ACK
 * @param policyDocumentId the POLICY document a POLICY_ACK item shows (D-07); null otherwise
 */
public record ChecklistTemplateItemDto(
        Long id,
        String name,
        String section,
        String ownerRole,
        boolean mandatory,
        int dueOffsetDays,
        int sortOrder,
        String itemKind,
        Long policyDocumentId) {
}
