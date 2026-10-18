package com.cubeworks.hr.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link Asset}.
 *
 * <p>Asset history (issue, return, condition changes) is tracked via {@code hr.audit_log} rows
 * with {@code entity_type = 'ASSET'} rather than a separate history table (data-model §4).</p>
 */
public interface AssetRepository extends JpaRepository<Asset, Long> {

    /**
     * Finds an asset by its unique human-readable asset number (e.g. {@code CW-LT-0042}).
     *
     * <p>Used for duplicate-check before registering a new asset (A21, AC-22).</p>
     *
     * @param assetNumber the asset number to look up
     * @return the asset, if any
     */
    Optional<Asset> findByAssetNumber(String assetNumber);

    /**
     * Returns all assets currently issued to a given employee.
     *
     * <p>Used by the IT equipment-request screen (A20) and the employee overview tab.</p>
     *
     * @param employeeId the employee ID
     * @return issued assets, ordered by asset type
     */
    @Query("""
            SELECT a FROM Asset a
            WHERE a.employee.id = :employeeId
              AND a.status IN ('ISSUED', 'RETURN_DUE', 'NOT_RETURNED')
            ORDER BY a.assetType
            """)
    List<Asset> findIssuedToEmployee(@Param("employeeId") Long employeeId);

    /**
     * Returns asset counts grouped by employee ID for a batch of employee IDs.
     *
     * <p>N+1 prevention: the IT admin Employees list (W09 / D-W9) shows an asset count per row.
     * Loading one count per employee would issue N separate queries for a page of N employees.
     * This single aggregate query replaces that with one round-trip, same pattern as
     * {@link com.cubeworks.hr.employee.EmployeeRepository#countEmployeesGroupedByDepartment()}.</p>
     *
     * @param employeeIds the employee IDs whose asset counts to return
     * @return one {@code Object[]} per employee that has at least one active asset:
     *         index 0 is the employee id ({@code Long}), index 1 is the count ({@code Long})
     */
    @Query("""
            SELECT a.employee.id, COUNT(a) FROM Asset a
            WHERE a.employee.id IN :employeeIds
              AND a.status IN ('ISSUED', 'RETURN_DUE', 'NOT_RETURNED')
            GROUP BY a.employee.id
            """)
    List<Object[]> countIssuedByEmployeeIds(@Param("employeeIds") java.util.List<Long> employeeIds);
}
