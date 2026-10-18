package com.cubeworks.hr.dto.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for adding or updating a form section (FRD-08 §6 C18, C19).
 *
 * <p>At least one of {@code bySelf}, {@code byPeer}, {@code byManager}, {@code byHr} must
 * be {@code true} — a section with no assigned reviewers could never be answered. This is
 * enforced by a DB CHECK and pre-validated in the service layer with the message
 * "This field is required." on the "Completed by" composite field.</p>
 *
 * @param name      section name; required
 * @param bySelf    whether the employee completes this section about themselves
 * @param byPeer    whether a chosen peer completes this section
 * @param byManager whether the manager completes this section
 * @param byHr      whether HR completes this section
 */
public record FormSectionRequest(
        @NotBlank(message = "This field is required.")
        @Size(max = 100)
        String name,

        boolean bySelf,
        boolean byPeer,
        boolean byManager,
        boolean byHr) {
}
