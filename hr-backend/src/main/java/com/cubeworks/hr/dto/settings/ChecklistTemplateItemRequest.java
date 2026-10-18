package com.cubeworks.hr.dto.settings;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for adding or updating a checklist template item (FRD-08 §6 C11, C12).
 *
 * <p>All fields are required per spec §5 S2. {@code ownerRole} must not be {@code SYSTEM}
 * (that value is reserved for backend-automated items, never set by HR).</p>
 *
 * @param name          item display name; required
 * @param section       checklist section enum value; required
 * @param ownerRole     owner role enum value; required; must not be SYSTEM
 * @param mandatory     whether the item is mandatory; required
 * @param dueOffsetDays days after start date the item is due; required; must be ≥ 0
 */
public record ChecklistTemplateItemRequest(
        @NotBlank(message = "This field is required.")
        @Size(max = 150)
        String name,

        @NotNull(message = "This field is required.")
        String section,

        @NotNull(message = "This field is required.")
        String ownerRole,

        @NotNull(message = "This field is required.")
        Boolean mandatory,

        @NotNull(message = "This field is required.")
        @Min(value = 0)
        Integer dueOffsetDays) {
}
