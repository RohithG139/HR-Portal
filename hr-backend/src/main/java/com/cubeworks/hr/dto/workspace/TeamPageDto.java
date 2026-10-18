package com.cubeworks.hr.dto.workspace;

import java.util.List;

/**
 * Pagination wrapper for the My Team list (W05, {@code GET /api/team}).
 *
 * @param items         the team members on this page
 * @param totalElements total number of matching employees across all pages
 * @param totalPages    total number of pages
 * @param page          zero-based current page index
 * @param size          page size requested
 */
public record TeamPageDto(
        List<TeamMemberDto> items,
        long totalElements,
        int totalPages,
        int page,
        int size
) {}
