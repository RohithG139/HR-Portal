package com.cubeworks.hr.dto.settings;

import java.time.OffsetDateTime;

/**
 * Response body for one row in the checklist-template list (FRD-08 §6 C07).
 *
 * <p>The {@code itemCount} avoids a separate detail fetch for the list screen.
 * {@code archivedAt} is {@code null} for active templates and non-null for archived ones
 * (ADM-04).</p>
 *
 * @param id         the template's surrogate PK
 * @param name       the template's display name
 * @param kind       "ONBOARDING" or "OFFBOARDING"
 * @param itemCount  number of items in this template
 * @param archivedAt null if the template is active; non-null when archived (ADM-04)
 */
public record ChecklistTemplateListDto(
        Long id,
        String name,
        String kind,
        int itemCount,
        OffsetDateTime archivedAt) {
}
