package com.cubeworks.hr.dto.settings;

/**
 * Response body for a single job title entry (FRD-08 §6 C04).
 *
 * <p>Grade and department are deliberately excluded — the {@code job_title} table has
 * only {@code id} and {@code name}; those attributes belong to the {@code employee} row
 * (FRD-08 §12 D-01, data-model §2.1).</p>
 *
 * @param id   the job title's surrogate PK
 * @param name the job title's display name
 */
public record JobTitleDto(
        Long id,
        String name) {
}
