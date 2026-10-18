package com.cubeworks.hr.asset;

import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the asset register endpoints.
 *
 * <h2>Covered endpoints</h2>
 * <ul>
 *   <li>A21 {@code POST /api/employees/{id}/assets}  — IT admin issues an asset to an employee</li>
 *   <li>A22 {@code POST /api/assets/{id}/unissue}    — IT admin marks an asset as returned</li>
 * </ul>
 *
 * <p>Controllers stay thin (R-CODE-1): all business logic lives in {@link AssetService}.</p>
 */
@RestController
public class AssetController {

    private final AssetService assetService;
    private final CurrentUser currentUser;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param assetService service holding all asset business logic
     * @param currentUser  resolves the authenticated employee from the JWT
     */
    public AssetController(final AssetService assetService,
                            final CurrentUser currentUser) {
        this.assetService = assetService;
        this.currentUser = currentUser;
    }

    /**
     * A21 — IT admin issues a new asset to an employee (S5, ONB-06).
     *
     * <p>Bean Validation fires before the service call. A blank model returns
     * {@code 400 VALIDATION_FAILED}. A duplicate asset number returns
     * {@code 409 ASSET_NUMBER_IN_USE} (AC-22).</p>
     *
     * @param employeeId the employee to issue the asset to
     * @param request    asset details (type, model, assetNumber, issueDate)
     * @return 201 with the created asset including {@code status: "ISSUED"}
     */
    @PostMapping("/api/employees/{employeeId}/assets")
    @PreAuthorize("hasRole('IT_ADMIN')")
    public ResponseEntity<AssetService.AssetDto> issueAsset(
            @PathVariable final Long employeeId,
            @Valid @RequestBody final AssetService.IssueAssetRequest request) {
        final Employee caller = currentUser.require();
        final AssetService.AssetDto result = assetService.issueAsset(employeeId, request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * A22 — IT admin marks an asset as returned (IN_STORAGE).
     *
     * @param assetId the asset to un-issue
     * @return 200 OK
     */
    @PostMapping("/api/assets/{assetId}/unissue")
    @PreAuthorize("hasRole('IT_ADMIN')")
    public ResponseEntity<Void> unissueAsset(@PathVariable final Long assetId) {
        final Employee caller = currentUser.require();
        assetService.unissueAsset(assetId, caller);
        return ResponseEntity.ok().build();
    }
}
