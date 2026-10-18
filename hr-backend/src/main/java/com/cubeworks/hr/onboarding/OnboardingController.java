package com.cubeworks.hr.onboarding;

import com.cubeworks.hr.employee.DocumentStorageService;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * REST controller for the F01 onboarding flow.
 *
 * <h2>Covered endpoints (spec §6)</h2>
 * <ul>
 *   <li>A06  {@code GET  /api/checklist-templates/{id}/preview}   — template preview (HR)</li>
 *   <li>A08  {@code POST /api/onboarding-drafts}                  — create draft (HR)</li>
 *   <li>A09  {@code PUT  /api/onboarding-drafts/{id}}             — update draft (HR)</li>
 *   <li>A10  {@code POST /api/onboardings}                        — create employee (HR)</li>
 *   <li>A11  {@code GET  /api/onboardings/{employeeId}}           — get onboarding (HR)</li>
 *   <li>A12  {@code PUT  /api/onboardings/{employeeId}/details}   — update details (HR)</li>
 *   <li>A13  {@code PUT  /api/onboardings/{employeeId}/it-request}  — save IT request (HR)</li>
 *   <li>A14  {@code POST /api/onboardings/{employeeId}/it-request/send}  — send IT request (HR)</li>
 *   <li>A15  {@code GET  /api/onboardings/{employeeId}/checklist} — get checklist (HR/IT/EMPLOYEE)</li>
 *   <li>A16  {@code POST /api/onboardings/{employeeId}/checklist-items} — add item (HR)</li>
 *   <li>A17  {@code POST /api/checklist-items/{id}/done}          — tick item (HR/IT/EMPLOYEE)</li>
 *   <li>A18  {@code DELETE /api/checklist-items/{id}/done}        — untick item (HR/IT/EMPLOYEE)</li>
 *   <li>A19  {@code POST /api/onboardings/{employeeId}/complete}  — complete (HR, undo window)</li>
 *   <li>A20  {@code GET  /api/it/equipment-requests/{employeeId}} — equipment view (HR/IT)</li>
 *   <li>A23  {@code POST /api/onboardings/{employeeId}/it-request/complete} — IT complete (IT)</li>
 *   <li>A24  {@code POST /api/onboardings/{employeeId}/it-setup/complete}  — IT setup done (IT)</li>
 *   <li>A25  {@code GET  /api/me/onboarding}                      — employee's own view (EMPLOYEE)</li>
 *   <li>A26  {@code GET  /api/checklist-items/{id}/policy}        — policy PDF (EMPLOYEE)</li>
 *   <li>A27  {@code POST /api/checklist-items/{id}/acknowledge}   — policy ack (EMPLOYEE)</li>
 *   <li>A28  {@code POST /api/checklist-items/{id}/confirm-equipment} — confirm equipment (EMPLOYEE)</li>
 * </ul>
 *
 * <p>A07 ({@code POST /api/documents}) lives in {@link com.cubeworks.hr.employee.DocumentController}
 * — it is not an onboarding-specific endpoint.</p>
 *
 * <p>Controllers stay thin (R-CODE-1): all business logic lives in {@link OnboardingService}.</p>
 */
@RestController
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final CurrentUser currentUser;
    private final DocumentStorageService documentStorageService;
    private final ObjectMapper objectMapper;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param onboardingService      service holding all onboarding business logic
     * @param currentUser            resolves the authenticated employee from the JWT
     * @param documentStorageService retrieves the policy PDF bytes for A26
     * @param objectMapper           serialises draft form bodies to real JSON for storage
     */
    public OnboardingController(final OnboardingService onboardingService,
                                 final CurrentUser currentUser,
                                 final DocumentStorageService documentStorageService,
                                 final ObjectMapper objectMapper) {
        this.onboardingService = onboardingService;
        this.currentUser = currentUser;
        this.documentStorageService = documentStorageService;
        this.objectMapper = objectMapper;
    }

    // ── A08 Create draft ─────────────────────────────────────────────────────

    /**
     * A08 / AC-21 — Creates an onboarding draft without creating an employee row,
     * starting a case or queueing any e-mail (D-13).
     *
     * @param body JSON with firstName, lastName (required) and optional privateEmail
     * @return 201 with draftId
     */
    @PostMapping("/api/onboarding-drafts")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<Map<String, Object>> createDraft(
            @RequestBody final Map<String, Object> body) {
        final Employee caller = currentUser.require();
        final String firstName = (String) body.get("firstName");
        final String lastName  = (String) body.get("lastName");
        final String form = toJson(body.containsKey("privateEmail")
                ? Map.of("privateEmail", body.get("privateEmail"))
                : Map.of());
        final long draftId = onboardingService.createDraft(firstName, lastName, form, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("draftId", draftId));
    }

    /**
     * A09 — Updates an existing draft.
     *
     * @param id   the draft ID
     * @param body updated form data
     * @return 200 accepted
     */
    @PutMapping("/api/onboarding-drafts/{id}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<Void> updateDraft(
            @PathVariable final long id,
            @RequestBody final Map<String, Object> body) {
        final Employee caller = currentUser.require();
        onboardingService.updateDraft(id,
                (String) body.getOrDefault("firstName", ""),
                (String) body.getOrDefault("lastName", ""),
                toJson(body), caller);
        return ResponseEntity.ok().build();
    }

    /**
     * Serialises a map to real JSON for storage in {@code onboarding_draft.form}.
     *
     * <p>{@code Map.toString()} produces Java's {@code {key=value}} syntax, not JSON — using
     * it directly here previously wrote invalid data into a column documented as JSON.</p>
     *
     * @param value the map to serialise
     * @return the JSON string
     */
    private String toJson(final Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (final com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise draft form to JSON", e);
        }
    }

    // ── A06 Template preview ──────────────────────────────────────────────────

    /**
     * A06 — Template preview.
     *
     * @param id the template ID
     * @return 200 with template item summary
     */
    @GetMapping("/api/checklist-templates/{id}/preview")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<OnboardingService.TemplatePreviewDto> getTemplatePreview(
            @PathVariable final Long id) {
        return ResponseEntity.ok(onboardingService.getTemplatePreview(id));
    }

    // ── A10 Create onboarding ─────────────────────────────────────────────────

    /**
     * A10 — Creates a new employee from the full step-1 form.
     *
     * <p>{@code @Valid} fires Bean Validation on the request record before the method body
     * runs. Cross-field constraints (age, endDate, verification) are validated in the service
     * and throw {@link com.cubeworks.hr.common.error.ApiException} with field errors.</p>
     *
     * @param req the step-1 form data
     * @return 201 with employeeId and employeeCode
     */
    @PostMapping("/api/onboardings")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<OnboardingService.CreateOnboardingResult> createOnboarding(
            @Valid @RequestBody final OnboardingService.CreateOnboardingRequest req) {
        final Employee caller = currentUser.require();
        final OnboardingService.CreateOnboardingResult result =
                onboardingService.createOnboarding(req, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // ── A11 Get onboarding ────────────────────────────────────────────────────

    /**
     * A11 — Returns the onboarding summary for one employee.
     *
     * @param employeeId the employee
     * @return 200 with onboarding summary
     */
    @GetMapping("/api/onboardings/{employeeId}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<OnboardingService.OnboardingSummaryDto> getOnboarding(
            @PathVariable final Long employeeId) {
        final Employee caller = currentUser.require();
        return ResponseEntity.ok(onboardingService.getOnboarding(employeeId, caller));
    }

    // ── A12 Update onboarding details ─────────────────────────────────────────

    /**
     * A12 — Updates the details (employee + contract fields) after creation.
     *
     * @param employeeId the employee
     * @param req        updated details including the optimistic-lock version
     * @return 200 OK
     */
    @PutMapping("/api/onboardings/{employeeId}/details")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<Void> updateOnboardingDetails(
            @PathVariable final Long employeeId,
            @RequestBody final OnboardingService.UpdateDetailsRequest req) {
        final Employee caller = currentUser.require();
        onboardingService.updateOnboardingDetails(employeeId, req, caller);
        return ResponseEntity.ok().build();
    }

    // ── A13 Save IT request ───────────────────────────────────────────────────

    /**
     * A13 — Saves the IT request without sending it.
     *
     * @param employeeId the employee
     * @param req        IT request data
     * @return 200 OK
     */
    @PutMapping("/api/onboardings/{employeeId}/it-request")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<Void> saveItRequest(
            @PathVariable final Long employeeId,
            @RequestBody final OnboardingService.ItRequestDto req) {
        final Employee caller = currentUser.require();
        onboardingService.saveItRequest(employeeId, req, caller);
        return ResponseEntity.ok().build();
    }

    // ── A14 Send IT request ───────────────────────────────────────────────────

    /**
     * A14 — Sends the IT request to IT admin.
     *
     * @param employeeId the employee
     * @param req        IT request data (may be empty body or update)
     * @return 200 with status and sentAt
     */
    @PostMapping("/api/onboardings/{employeeId}/it-request/send")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<OnboardingService.ItSendResultDto> sendItRequest(
            @PathVariable final Long employeeId,
            @RequestBody(required = false) final OnboardingService.ItRequestDto req) {
        final Employee caller = currentUser.require();
        final OnboardingService.ItRequestDto safeReq = req != null
                ? req
                : new OnboardingService.ItRequestDto(null, null, null, null, null, null, null);
        return ResponseEntity.ok(onboardingService.sendItRequest(employeeId, safeReq, caller));
    }

    // ── A15 Get checklist ─────────────────────────────────────────────────────

    /**
     * A15 — Returns the onboarding checklist with progress.
     *
     * @param employeeId   the employee
     * @param ownerFilter  optional owner role filter
     * @param statusFilter optional status filter
     * @return 200 with checklist DTO
     */
    @GetMapping("/api/onboardings/{employeeId}/checklist")
    public ResponseEntity<OnboardingService.ChecklistDto> getChecklist(
            @PathVariable final Long employeeId,
            @RequestParam(required = false) final String ownerFilter,
            @RequestParam(required = false) final String statusFilter) {
        final Employee caller = currentUser.require();
        return ResponseEntity.ok(
                onboardingService.getChecklist(employeeId, ownerFilter, statusFilter, caller));
    }

    // ── A16 Add checklist item ────────────────────────────────────────────────

    /**
     * A16 — Adds a manual checklist item (HR "+Add item").
     *
     * @param employeeId the employee
     * @param req        new item details
     * @return 201 with the created item
     */
    @PostMapping("/api/onboardings/{employeeId}/checklist-items")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<OnboardingService.ChecklistItemDto> addChecklistItem(
            @PathVariable final Long employeeId,
            @Valid @RequestBody final OnboardingService.AddChecklistItemRequest req) {
        final Employee caller = currentUser.require();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(onboardingService.addChecklistItem(employeeId, req, caller));
    }

    // ── A17 Tick item ─────────────────────────────────────────────────────────

    /**
     * A17 — Marks a checklist item as done.
     *
     * @param id the checklist item ID
     * @return 200 with tick result
     */
    @PostMapping("/api/checklist-items/{id}/done")
    public ResponseEntity<OnboardingService.TickResultDto> tickItem(
            @PathVariable final Long id) {
        final Employee caller = currentUser.require();
        return ResponseEntity.ok(onboardingService.tickItem(id, caller));
    }

    // ── A18 Untick item ───────────────────────────────────────────────────────

    /**
     * A18 — Removes the done mark from a checklist item.
     *
     * @param id the checklist item ID
     * @return 200 with tick result
     */
    @DeleteMapping("/api/checklist-items/{id}/done")
    public ResponseEntity<OnboardingService.TickResultDto> untickItem(
            @PathVariable final Long id) {
        final Employee caller = currentUser.require();
        return ResponseEntity.ok(onboardingService.untickItem(id, caller));
    }

    // ── A19 Complete onboarding ───────────────────────────────────────────────

    /**
     * A19 — Initiates onboarding completion with undo window.
     *
     * <p>Returns 202 Accepted with {@code undoUntil} (R-API-6). The engine task is completed
     * after the undo window by the {@link UndoWindowScheduler}.</p>
     *
     * @param employeeId the employee
     * @return 202 with undoUntil instant
     */
    @PostMapping("/api/onboardings/{employeeId}/complete")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<OnboardingService.UndoWindowResult> completeOnboarding(
            @PathVariable final Long employeeId) {
        final Employee caller = currentUser.require();
        final OnboardingService.UndoWindowResult result =
                onboardingService.completeOnboarding(employeeId, caller);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    // ── A20 Equipment request view ────────────────────────────────────────────

    /**
     * A20 — Returns the equipment request details and issued assets.
     *
     * @param employeeId the employee
     * @return 200 with equipment request DTO
     */
    @GetMapping("/api/it/equipment-requests/{employeeId}")
    public ResponseEntity<OnboardingService.EquipmentRequestDto> getEquipmentRequest(
            @PathVariable final Long employeeId) {
        final Employee caller = currentUser.require();
        return ResponseEntity.ok(onboardingService.getEquipmentRequest(employeeId, caller));
    }

    // ── A23 Complete equipment request (IT admin) ─────────────────────────────

    /**
     * A23 — IT admin completes the "Equipment request" task.
     *
     * @param employeeId the employee
     * @return 202 with undoUntil instant
     */
    @PostMapping("/api/onboardings/{employeeId}/it-request/complete")
    @PreAuthorize("hasRole('IT_ADMIN')")
    public ResponseEntity<OnboardingService.UndoWindowResult> completeEquipmentRequest(
            @PathVariable final Long employeeId) {
        final Employee caller = currentUser.require();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(onboardingService.completeEquipmentRequest(employeeId, caller));
    }

    // ── A24 IT setup complete ─────────────────────────────────────────────────

    /**
     * A24 — IT admin completes the "Remaining setup" task.
     *
     * @param employeeId the employee
     * @return 202 with undoUntil instant
     */
    @PostMapping("/api/onboardings/{employeeId}/it-setup/complete")
    @PreAuthorize("hasRole('IT_ADMIN')")
    public ResponseEntity<OnboardingService.UndoWindowResult> completeItSetup(
            @PathVariable final Long employeeId) {
        final Employee caller = currentUser.require();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(onboardingService.completeItSetup(employeeId, caller));
    }

    // ── A25 Employee's own onboarding ─────────────────────────────────────────

    /**
     * A25 — Returns the employee's own onboarding checklist and issued assets.
     *
     * @return 200 with employee's items and assets
     */
    @GetMapping("/api/me/onboarding")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<OnboardingService.EmployeeOnboardingDto> getMyOnboarding() {
        final Employee caller = currentUser.require();
        return ResponseEntity.ok(onboardingService.getMyOnboarding(caller));
    }

    // ── A26 Get policy document ────────────────────────────────────────────────

    /**
     * A26 — Returns the policy PDF for a POLICY_ACK checklist item (D-07).
     *
     * @param id the POLICY_ACK checklist item ID
     * @return 200 with the PDF bytes
     */
    @GetMapping("/api/checklist-items/{id}/policy")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<Resource> getPolicyDocument(@PathVariable final Long id) throws IOException {
        final Employee caller = currentUser.require();
        final OnboardingService.PolicyDocumentDto doc = onboardingService.getPolicyDocument(id, caller);
        final Resource resource = documentStorageService.retrieve(doc.storageKey());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.fileName() + "\"")
                .body(resource);
    }

    // ── A27 Policy acknowledgement ────────────────────────────────────────────

    /**
     * A27 — Employee acknowledges a policy (must tick the acceptance checkbox).
     *
     * @param id   the POLICY_ACK checklist item ID
     * @param body {@code {"accepted": true|false}}
     * @return 200 OK
     */
    @PostMapping("/api/checklist-items/{id}/acknowledge")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<Void> acknowledgePolicy(
            @PathVariable final Long id,
            @RequestBody final Map<String, Object> body) {
        final Employee caller = currentUser.require();
        final boolean accepted = Boolean.TRUE.equals(body.get("accepted"));
        onboardingService.acknowledgePolicy(id, accepted, caller);
        return ResponseEntity.ok().build();
    }

    // ── A28 Confirm equipment ─────────────────────────────────────────────────

    /**
     * A28 — Employee confirms received equipment (and optionally adds remarks).
     *
     * @param id   the EQUIPMENT_CONFIRM checklist item ID
     * @param body {@code {"receivedAssetIds": [...], "remarks": "..."}}
     * @return 200 OK
     */
    @PostMapping("/api/checklist-items/{id}/confirm-equipment")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<Void> confirmEquipment(
            @PathVariable final Long id,
            @RequestBody final Map<String, Object> body) {
        final Employee caller = currentUser.require();
        @SuppressWarnings("unchecked")
        final List<Long> assetIds = body.containsKey("receivedAssetIds")
                ? ((List<Object>) body.get("receivedAssetIds")).stream()
                        .map(o -> ((Number) o).longValue()).toList()
                : List.of();
        final String remarks = (String) body.get("remarks");
        onboardingService.confirmEquipment(id, assetIds, remarks, caller);
        return ResponseEntity.ok().build();
    }
}
