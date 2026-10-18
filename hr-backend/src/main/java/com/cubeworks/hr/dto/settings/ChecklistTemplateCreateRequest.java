package com.cubeworks.hr.dto.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new checklist template (FRD-08 §6 C09).
 *
 * <p>When {@code copiedFromId} is provided, all items from the source template are copied
 * into the new template. {@code jobTitleId} optionally scopes the template to a specific
 * job title so the onboarding flow pre-selects it automatically.</p>
 *
 * @param name          required; must be unique across all templates
 * @param kind          required; "ONBOARDING" or "OFFBOARDING"
 * @param jobTitleId    optional FK to a job title
 * @param copiedFromId  optional FK to a template to copy items from
 */
public record ChecklistTemplateCreateRequest(
        @NotBlank(message = "This field is required.")
        @Size(max = 100)
        String name,

        @NotNull(message = "This field is required.")
        String kind,

        Long jobTitleId,
        Long copiedFromId) {
}
