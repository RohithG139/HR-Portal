package com.cubeworks.hr.dto.settings;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Full-detail response for a checklist template including all its items (FRD-08 §6 C08).
 *
 * <p>This is the admin-tab view, used by the Settings screen to show editable items.
 * Distinct from the F01 read-only preview (A06) which stays in the onboarding package.</p>
 *
 * @param id          the template's surrogate PK
 * @param name        the template's display name
 * @param kind        "ONBOARDING" or "OFFBOARDING"
 * @param jobTitleId  optional FK to the job title this template is scoped to
 * @param copiedFromId optional FK to the template this was cloned from
 * @param archivedAt  null if active; non-null when archived (ADM-04)
 * @param items       all items ordered by section then sort_order
 */
public record ChecklistTemplateDetailDto(
        Long id,
        String name,
        String kind,
        Long jobTitleId,
        Long copiedFromId,
        OffsetDateTime archivedAt,
        List<ChecklistTemplateItemDto> items) {
}
