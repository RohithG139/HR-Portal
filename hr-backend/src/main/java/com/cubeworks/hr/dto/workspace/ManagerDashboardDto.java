package com.cubeworks.hr.dto.workspace;

import java.util.List;

/**
 * Dashboard DTO for Manager users (W02, FRD-01 Wave 3).
 *
 * <p>The {@code myTeam} list here shares its content with {@code GET /api/team} (W05) —
 * both call the same service method so there is one source of truth, not two parallel
 * queries (spec §7 design note). Deferred fields ({@code benchCount}, {@code reviewsToWrite},
 * {@code plansToApprove}) are absent and will be added by F04/F05/F06 (additive DTO contract,
 * spec §7 D-W2).</p>
 *
 * @param teamMembers count of employees whose {@code manager_id = caller}
 * @param tasks       the manager's open tasks (same shape as A36/S12 My Tasks)
 * @param myTeam      list of the manager's direct reports with basic identity + stage data;
 *                    same data as the My Team screen's "buildable now" columns (W05)
 */
public record ManagerDashboardDto(
        long teamMembers,
        List<DashboardTaskDto> tasks,
        List<TeamMemberDto> myTeam
) {}
