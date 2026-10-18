package com.cubeworks.hr.controller.settings;

import com.cubeworks.hr.dto.settings.ChecklistTemplateCreateRequest;
import com.cubeworks.hr.dto.settings.ChecklistTemplateDetailDto;
import com.cubeworks.hr.dto.settings.ChecklistTemplateItemDto;
import com.cubeworks.hr.dto.settings.ChecklistTemplateItemRequest;
import com.cubeworks.hr.dto.settings.ChecklistTemplateListDto;
import com.cubeworks.hr.security.CurrentUser;
import com.cubeworks.hr.service.settings.ChecklistTemplateSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing checklist templates and their items (FRD-08 §6 C07–C13).
 *
 * <p>All endpoints require the {@code HR} role (FRD-08 §2). Controllers stay thin
 * (R-CODE-1): all logic lives in {@link ChecklistTemplateSettingsService}.</p>
 */
@RestController
@RequestMapping("/api/settings/checklist-templates")
@PreAuthorize("hasRole('HR')")
public class ChecklistTemplateSettingsController {

    private final ChecklistTemplateSettingsService checklistTemplateSettingsService;
    private final CurrentUser currentUser;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param checklistTemplateSettingsService the service holding all template CRUD logic
     * @param currentUser                      resolves the authenticated employee from the JWT
     */
    public ChecklistTemplateSettingsController(
            final ChecklistTemplateSettingsService checklistTemplateSettingsService,
            final CurrentUser currentUser) {
        this.checklistTemplateSettingsService = checklistTemplateSettingsService;
        this.currentUser = currentUser;
    }

    /**
     * C07 — Lists checklist templates, optionally filtered by kind and archived flag.
     *
     * @param kind            optional kind filter ("ONBOARDING" or "OFFBOARDING")
     * @param includeArchived when true includes archived templates (default false)
     * @return 200 with the list of templates
     */
    @GetMapping
    public ResponseEntity<List<ChecklistTemplateListDto>> listTemplates(
            @RequestParam(required = false) final String kind,
            @RequestParam(defaultValue = "false") final boolean includeArchived) {
        return ResponseEntity.ok(checklistTemplateSettingsService.list(kind, includeArchived));
    }

    /**
     * C08 — Returns full detail of a checklist template including all items.
     *
     * @param id the template's surrogate PK
     * @return 200 with the template detail
     */
    @GetMapping("/{id}")
    public ResponseEntity<ChecklistTemplateDetailDto> getTemplate(@PathVariable final Long id) {
        return ResponseEntity.ok(checklistTemplateSettingsService.getDetail(id));
    }

    /**
     * C09 — Creates a new checklist template, optionally copying items from a source template.
     *
     * @param request the create request
     * @return 201 with {@code {id: ...}}
     */
    @PostMapping
    public ResponseEntity<Map<String, Long>> createTemplate(
            @Valid @RequestBody final ChecklistTemplateCreateRequest request) {
        final var actor = currentUser.require();
        final Map<String, Long> result = checklistTemplateSettingsService.create(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * C10 — Archives a checklist template (ADM-04).
     *
     * <p>Only sets {@code archived_at}; never changes running checklists.</p>
     *
     * @param id the template to archive
     * @return 200 on success
     */
    @PostMapping("/{id}/archive")
    public ResponseEntity<Void> archiveTemplate(@PathVariable final Long id) {
        final var actor = currentUser.require();
        checklistTemplateSettingsService.archive(id, actor);
        return ResponseEntity.ok().build();
    }

    /**
     * C11 — Adds an item to a checklist template.
     *
     * @param id      the owning template's id
     * @param request the new item
     * @return 201 with the created item
     */
    @PostMapping("/{id}/items")
    public ResponseEntity<ChecklistTemplateItemDto> addItem(
            @PathVariable final Long id,
            @Valid @RequestBody final ChecklistTemplateItemRequest request) {
        final var actor = currentUser.require();
        final ChecklistTemplateItemDto item = checklistTemplateSettingsService.addItem(id, request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    /**
     * C12 — Updates a checklist template item.
     *
     * @param id     the owning template's id
     * @param itemId the item to update
     * @param request the updated fields
     * @return 200 with the updated item
     */
    @PutMapping("/{id}/items/{itemId}")
    public ResponseEntity<ChecklistTemplateItemDto> updateItem(
            @PathVariable final Long id,
            @PathVariable final Long itemId,
            @Valid @RequestBody final ChecklistTemplateItemRequest request) {
        final var actor = currentUser.require();
        final ChecklistTemplateItemDto item =
                checklistTemplateSettingsService.updateItem(id, itemId, request, actor);
        return ResponseEntity.ok(item);
    }

    /**
     * C13 — Deletes a checklist template item.
     *
     * <p>Returns {@code 409 ITEM_IN_USE} if the item has been instantiated into
     * any {@code checklist_item} row (FRD-08 §12 D-02).</p>
     *
     * @param id     the owning template's id
     * @param itemId the item to delete
     * @return 204 on success
     */
    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<Void> deleteItem(
            @PathVariable final Long id,
            @PathVariable final Long itemId) {
        final var actor = currentUser.require();
        checklistTemplateSettingsService.deleteItem(id, itemId, actor);
        return ResponseEntity.noContent().build();
    }
}
