package com.cubeworks.hr.dto.settings;

/**
 * Response body for a single job title entry (FRD-08 §6 C04).
 *
 * <p>Grade and department added in V15 (FRD-08 §12 D-01 reversed, 2026-10-19) to match the
 * wireframe's Job titles table. Both are {@code null} for job titles created before V15.</p>
 *
 * @param id             the job title's surrogate PK
 * @param name           the job title's display name
 * @param grade          the grade enum name (e.g. {@code MEDIOR}), or {@code null}
 * @param departmentId   the department's PK, or {@code null}
 * @param departmentName the department's name, or {@code null}
 */
public record JobTitleDto(
        Long id,
        String name,
        String grade,
        Long departmentId,
        String departmentName) {
}
