package com.cubeworks.hr.dto.workspace;

import java.time.LocalDate;
import java.util.List;

/**
 * Dashboard DTO for IT admin users (W04, FRD-01 Wave 3).
 *
 * <p>Deferred fields ({@code accessToRevokeCount}, {@code notReturnedCount}) require
 * {@code v_account_access} and {@code v_unreturned_assets} which depend on {@code employee_exit}
 * (F08). They are absent here and will be added additively by F08 (additive DTO contract, D-W2,
 * spec §5 W4).</p>
 *
 * @param equipmentRequestsCount count of open {@code engine_task} rows where
 *                               {@code task_name = 'Equipment request'} and uncompleted
 * @param openSetupItemsCount    count of open {@code engine_task} rows where
 *                               {@code task_name = 'Remaining setup'} and uncompleted
 * @param tasks                  the IT admin's open tasks (same shape as A36/S12 My Tasks)
 */
public record ItAdminDashboardDto(
        long equipmentRequestsCount,
        long openSetupItemsCount,
        List<DashboardTaskDto> tasks,
        // Card detail lines (wireframe #it-dash). Each is null/empty when there is nothing open.
        /** Earliest-due open equipment request: employee name. */
        String nextEquipmentName,
        /** That employee's contract start date. */
        LocalDate nextEquipmentStartDate,
        /** Up to 2 names with open "Remaining setup" tasks. */
        List<String> setupItemNames,
        /** Exits in progress (APPROVED / IN_PROGRESS) whose accounts must be revoked. */
        long accessToRevokeCount,
        /** Earliest such exit: employee name. */
        String nextRevokeName,
        /** That employee's last working day. */
        LocalDate nextRevokeDate,
        /** Assets flagged NOT_RETURNED by ex-employees (v_unreturned_assets). */
        long notReturnedCount
) {}
