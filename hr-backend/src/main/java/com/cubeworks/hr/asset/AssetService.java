package com.cubeworks.hr.asset;

import com.cubeworks.hr.common.audit.AuditLogService;
import com.cubeworks.hr.common.error.ApiException;
import com.cubeworks.hr.common.error.ErrorCodes;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.employee.EmployeeRepository;
import com.cubeworks.hr.onboarding.ChecklistItem;
import com.cubeworks.hr.onboarding.ChecklistItemRepository;
import com.cubeworks.hr.onboarding.ChecklistItemStatus;
import com.cubeworks.hr.onboarding.ChecklistPhase;
import com.cubeworks.hr.onboarding.OnboardingRepository;
import com.cubeworks.hr.onboarding.OnboardingService;
import com.cubeworks.hr.onboarding.OnboardingStatus;
import com.cubeworks.hr.settings.ChecklistSection;
import com.cubeworks.hr.settings.ItemKind;
import com.cubeworks.hr.settings.OwnerRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Business logic for the asset register: issuing and un-issuing physical assets (A21, A22).
 *
 * <h2>Transaction boundary</h2>
 * <p>Each write method is {@code @Transactional}. The transaction covers: the {@code asset} row
 * insert/update, any {@code checklist_item} status update (ONB-06), and the {@code audit_log}
 * row. A failure in any step rolls back the whole operation so the asset register never shows
 * a "ghost" issued asset without its corresponding checklist tick.</p>
 */
@Service
public class AssetService {

    private final AssetRepository assetRepository;
    private final EmployeeRepository employeeRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final com.cubeworks.hr.security.SelfActionGuard selfActionGuard;
    private final OnboardingRepository onboardingRepository;
    private final OnboardingService onboardingService;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param assetRepository         repository for asset rows
     * @param employeeRepository      repository for looking up the target employee
     * @param checklistItemRepository repository for ticking the matching checklist item (ONB-06)
     * @param auditLogService         records the asset-issue event in the audit log
     * @param clock                   application-wide clock bean
     * @param selfActionGuard         blocks an IT admin issuing/unissuing their own assets (G-13)
     */
    public AssetService(final AssetRepository assetRepository,
                        final EmployeeRepository employeeRepository,
                        final ChecklistItemRepository checklistItemRepository,
                        final AuditLogService auditLogService,
                        final Clock clock,
                        final com.cubeworks.hr.security.SelfActionGuard selfActionGuard,
                        final OnboardingRepository onboardingRepository,
                        final OnboardingService onboardingService) {
        this.assetRepository = assetRepository;
        this.employeeRepository = employeeRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.selfActionGuard = selfActionGuard;
        this.onboardingRepository = onboardingRepository;
        this.onboardingService = onboardingService;
    }

    // ── A21 Issue asset ────────────────────────────────────────────────────────

    /**
     * Issues a new asset to an employee and ticks the matching EQUIPMENT_ISSUED checklist item
     * (A21, ONB-06).
     *
     * <p>Atomicity: the asset row insert, the checklist_item status change (if applicable), and
     * the audit_log entry are all written in a single transaction. A duplicate asset number is
     * detected before the DB constraint fires and returned as 409 ASSET_NUMBER_IN_USE.</p>
     *
     * @param employeeId the employee to issue the asset to
     * @param request    the new asset details (type, model, asset number, issue date)
     * @param caller     the IT admin performing the issue
     * @return the created asset DTO with status ISSUED
     * @throws ApiException with {@code 409 ASSET_NUMBER_IN_USE} if the asset number already exists
     */
    @Transactional
    @PreAuthorize("hasRole('IT_ADMIN')")
    public AssetDto issueAsset(final Long employeeId,
                                final IssueAssetRequest request,
                                final Employee caller) {
        selfActionGuard.forbidSelf(caller, employeeId);
        // Duplicate asset-number check (AC-22, S5)
        assetRepository.findByAssetNumber(request.assetNumber()).ifPresent(existing -> {
            throw new ApiException(ErrorCodes.ASSET_NUMBER_IN_USE, HttpStatus.CONFLICT,
                    "This asset number is already in use.");
        });

        final Employee emp = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Employee " + employeeId + " not found"));

        final OffsetDateTime now = OffsetDateTime.now(clock);
        final LocalDate issueDate = request.issueDate() != null
                ? LocalDate.parse(request.issueDate())
                : LocalDate.now(clock);

        final Asset asset = new Asset();
        asset.setAssetNumber(request.assetNumber());
        asset.setAssetType(AssetType.valueOf(request.assetType()));
        asset.setModel(request.model());
        asset.setEmployee(emp);
        asset.setStatus(AssetStatus.ISSUED);
        asset.setIssuedOn(issueDate);
        asset.setCreatedAt(now);
        asset.setCreatedBy(caller.getId());
        asset.setUpdatedAt(now);
        asset.setUpdatedBy(caller.getId());
        final Asset saved = assetRepository.save(asset);

        // ONB-06 / FRD-02 §4.4: link the asset to the matching open equipment item and tick it
        // (checklist_item.asset_id, DONE) — matched by equipment kind/name, never "first open item"
        tickMatchingChecklistItem(emp, saved, caller, now);

        // Audit log
        auditLogService.record(emp, "ASSET", saved.getId(), "ASSET_ISSUED", caller, null,
                "{\"assetNumber\":\"" + request.assetNumber() + "\",\"status\":\"ISSUED\"}");

        return new AssetDto(saved.getId(), saved.getAssetNumber(), saved.getAssetType().name(),
                saved.getModel(), saved.getStatus().name());
    }

    // ── A22 Un-issue asset ─────────────────────────────────────────────────────

    /**
     * Removes an asset from an employee and marks it as IN_STORAGE (A22).
     *
     * <p>Atomicity: the asset status update and audit_log entry are written in a single
     * transaction. If the audit write fails, the status change rolls back with it.</p>
     *
     * @param assetId the asset to un-issue
     * @param caller  the IT admin performing the un-issue
     */
    @Transactional
    @PreAuthorize("hasRole('IT_ADMIN')")
    public void unissueAsset(final Long assetId, final Employee caller) {
        final Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Asset " + assetId + " not found"));
        if (asset.getStatus() != AssetStatus.ISSUED && asset.getStatus() != AssetStatus.RETURN_DUE) {
            throw new ApiException(ErrorCodes.CONFLICT, HttpStatus.CONFLICT,
                    "Asset is not currently issued.");
        }
        if (asset.getEmployee() != null) {
            selfActionGuard.forbidSelf(caller, asset.getEmployee().getId());
        }

        final Employee emp = asset.getEmployee();
        final OffsetDateTime now = OffsetDateTime.now(clock);
        final String before = "{\"status\":\"" + asset.getStatus().name() + "\"}";

        asset.setEmployee(null);
        asset.setStatus(AssetStatus.IN_STORAGE);
        asset.setUpdatedAt(now);
        asset.setUpdatedBy(caller.getId());
        assetRepository.save(asset);

        // The equipment item this asset ticked is no longer fulfilled: reopen it while
        // onboarding is still in progress (once COMPLETED, the checklist is frozen — D-15)
        if (emp != null) {
            reopenItemsLinkedTo(emp, asset, caller, now);
        }

        auditLogService.record(emp, "ASSET", assetId, "ASSET_RETURNED", caller, before,
                "{\"status\":\"IN_STORAGE\"}");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Ticks the first open EQUIPMENT_ISSUED checklist item that matches the given asset type
     * (ONB-06). If no matching item exists, does nothing — the asset is still recorded.
     *
     * <p>The match looks for an item in the EQUIPMENT_ISSUED section that is either GENERIC
     * or has a name corresponding to the asset type (e.g. "Laptop" for LAPTOP). If none match
     * by name, falls back to the first open EQUIPMENT_ISSUED item.</p>
     *
     * @param emp       the employee whose checklist to search
     * @param assetType the type of asset being issued
     * @param caller    the user performing the issue (set as done_by)
     * @param now       current timestamp
     */
    /**
     * Words that identify a checklist item for each asset type, matched against the item name
     * (template names such as "Laptop (MacBook Pro 14\")", "Test phone", and the A03 accessory
     * values such as "KEYBOARD_MOUSE" / "Keyboard & mouse"). {@link AssetType#OTHER} matches
     * nothing: there is no checklist item an "Other" asset could be said to fulfil.
     */
    private static List<String> keywordsFor(final AssetType type) {
        return switch (type) {
            case LAPTOP -> List.of("laptop");
            case MONITOR -> List.of("monitor");
            case PHONE -> List.of("phone");
            case ACCESS_BADGE -> List.of("badge");
            case DOCKING_STATION -> List.of("docking");
            case HEADSET -> List.of("headset");
            case KEYBOARD_MOUSE -> List.of("keyboard", "mouse");
            case OTHER -> List.of();
        };
    }

    private static boolean matches(final ChecklistItem item, final AssetType type) {
        if (item.getName() == null) return false;
        final String name = item.getName().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        if (name.equals(type.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '))) return true;
        return keywordsFor(type).stream().anyMatch(name::contains);
    }

    private boolean onboardingInProgress(final Employee emp) {
        return onboardingRepository.findByEmployeeId(emp.getId())
                .map(o -> o.getStatus() != OnboardingStatus.COMPLETED)
                .orElse(false);
    }

    private void tickMatchingChecklistItem(final Employee emp, final Asset asset,
                                            final Employee caller, final OffsetDateTime now) {
        if (!onboardingInProgress(emp)) return;
        // Open, not-yet-linked IT equipment items (kind EQUIPMENT, or in the Equipment issued section)
        final ChecklistItem target = checklistItemRepository
                .findOpenByOwnerRole(emp.getId(), ChecklistPhase.ONBOARDING, OwnerRole.IT_ADMIN)
                .stream()
                .filter(i -> i.getItemKind() == ItemKind.EQUIPMENT
                        || i.getSection() == ChecklistSection.EQUIPMENT_ISSUED)
                .filter(i -> i.getAsset() == null)
                .filter(i -> matches(i, asset.getAssetType()))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return; // nothing on the checklist for this kind of equipment — the asset is still issued
        }

        target.setAsset(asset);
        target.setStatus(ChecklistItemStatus.DONE);
        target.setDoneAt(now);
        target.setDoneBy(caller);
        target.setUpdatedAt(now);
        target.setUpdatedBy(caller.getId());
        checklistItemRepository.save(target);
        auditLogService.record(emp, "checklist_item", target.getId(), "TICK_ITEM", caller,
                "{\"status\":\"OPEN\"}", "{\"status\":\"DONE\",\"assetId\":" + asset.getId() + "}",
                "asset recorded");
        onboardingService.refreshMandatoryItemsDone(emp);
    }

    private void reopenItemsLinkedTo(final Employee emp, final Asset asset,
                                      final Employee caller, final OffsetDateTime now) {
        final List<ChecklistItem> linked = checklistItemRepository.findByAssetId(asset.getId());
        if (linked.isEmpty()) return;
        final boolean inProgress = onboardingInProgress(emp);
        for (final ChecklistItem item : linked) {
            item.setAsset(null);
            if (inProgress && item.getStatus() == ChecklistItemStatus.DONE) {
                item.setStatus(ChecklistItemStatus.OPEN);
                item.setDoneAt(null);
                item.setDoneBy(null);
                auditLogService.record(emp, "checklist_item", item.getId(), "UNTICK_ITEM", caller,
                        "{\"status\":\"DONE\",\"assetId\":" + asset.getId() + "}",
                        "{\"status\":\"OPEN\"}", "asset removed");
            }
            item.setUpdatedAt(now);
            item.setUpdatedBy(caller.getId());
            checklistItemRepository.save(item);
        }
        if (inProgress) {
            onboardingService.refreshMandatoryItemsDone(emp);
        }
    }

    // ── DTOs ───────────────────────────────────────────────────────────────────

    /**
     * Request to issue a new asset to an employee (A21, S5).
     *
     * @param assetType   the asset category (e.g. "LAPTOP", "MONITOR")
     * @param model       the model name (e.g. "MacBook Pro 14\""); must not be blank
     * @param assetNumber the unique human-readable asset number (e.g. "CW-LT-0042")
     * @param issueDate   the issue date (ISO format, e.g. "2027-01-05"); defaults to today if null
     */
    public record IssueAssetRequest(
            @NotBlank(message = "This field is required.") String assetType,
            @NotBlank(message = "This field is required.") String model,
            @NotBlank(message = "This field is required.") String assetNumber,
            String issueDate) {}

    /**
     * Response DTO for an asset (A21 response, equipment request view).
     *
     * @param id          surrogate PK
     * @param assetNumber unique asset number
     * @param assetType   asset type code
     * @param model       model name
     * @param status      current status code
     */
    public record AssetDto(Long id, String assetNumber, String assetType,
                            String model, String status) {}
}
