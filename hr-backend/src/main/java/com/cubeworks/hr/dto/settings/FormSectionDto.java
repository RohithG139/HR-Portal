package com.cubeworks.hr.dto.settings;

import java.util.List;

/**
 * Response body for a form section, nested inside {@link AppraisalFormDetailDto}
 * (FRD-08 §6 C16, C18, C19).
 *
 * @param id        the section's surrogate PK
 * @param name      the section name
 * @param bySelf    whether the employee completes this section about themselves
 * @param byPeer    whether a chosen peer completes this section
 * @param byManager whether the manager completes this section
 * @param byHr      whether HR completes this section
 * @param sortOrder display position within the form
 * @param questions questions in this section, ordered by sort_order
 */
public record FormSectionDto(
        Long id,
        String name,
        boolean bySelf,
        boolean byPeer,
        boolean byManager,
        boolean byHr,
        int sortOrder,
        List<FormQuestionDto> questions) {
}
