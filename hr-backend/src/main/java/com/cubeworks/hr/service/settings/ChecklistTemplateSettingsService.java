package com.cubeworks.hr.service.settings;

import com.cubeworks.hr.common.audit.AuditLogService;
import com.cubeworks.hr.common.error.ApiException;
import com.cubeworks.hr.common.error.ErrorCodes;
import com.cubeworks.hr.dto.settings.*;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.settings.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing checklist templates and their items (FRD-08 §6 C07–C13).
 *
 * <p>Key business rules:</p>
 * <ul>
 *   <li>ADM-04: archiving only sets {@code archived_at}; never touches instantiated
 *       {@code checklist_item} rows. Template edits (C12) similarly never touch
 *       running checklists.</li>
 *   <li>D-02: a template item that has been instantiated into at least one
 *       {@code checklist_item} row cannot be deleted (FK {@code RESTRICT}).</li>
 *   <li>C09 with copiedFromId: all items from the source template are deep-copied into
 *       the new template — no live dependency is created between them.</li>
 *   <li>ADM-05: every write is audited in the same transaction (R-API-5).</li>
 * </ul>
 */
@Service
public class ChecklistTemplateSettingsService {

    private static final Logger log = LoggerFactory.getLogger(ChecklistTemplateSettingsService.class);

    private final ChecklistTemplateRepository templateRepository;
    private final ChecklistTemplateItemRepository itemRepository;
    private final JobTitleRepository jobTitleRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param templateRepository reads/writes {@code hr.checklist_template}
     * @param itemRepository     reads/writes {@code hr.checklist_template_item}
     * @param jobTitleRepository used to resolve optional jobTitleId FK on create
     * @param auditLogService    writes audit entries (ADM-05)
     * @param objectMapper       serialises before/after snapshots for audit
     * @param clock              application-wide clock bean
     */
    public ChecklistTemplateSettingsService(final ChecklistTemplateRepository templateRepository,
                                            final ChecklistTemplateItemRepository itemRepository,
                                            final JobTitleRepository jobTitleRepository,
                                            final AuditLogService auditLogService,
                                            final ObjectMapper objectMapper,
                                            final Clock clock) {
        this.templateRepository = templateRepository;
        this.itemRepository = itemRepository;
        this.jobTitleRepository = jobTitleRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * C07 — Lists templates, optionally filtered by kind and archived flag.
     *
     * @param kind            optional "ONBOARDING" or "OFFBOARDING" filter; null means all kinds
     * @param includeArchived when true, includes archived templates; default false
     * @return matching templates with item counts
     */
    public List<ChecklistTemplateListDto> list(final String kind, final boolean includeArchived) {
        final List<ChecklistTemplate> templates;

        if (kind != null) {
            final TemplateKind kindEnum;
            try {
                kindEnum = TemplateKind.valueOf(kind);
            } catch (IllegalArgumentException e) {
                throw new ApiException(ErrorCodes.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                        "Invalid kind: " + kind);
            }
            templates = includeArchived
                    ? templateRepository.findByKind(kindEnum)
                    : templateRepository.findByKindAndArchivedAtIsNull(kindEnum);
        } else {
            templates = includeArchived
                    ? templateRepository.findAll()
                    : templateRepository.findByArchivedAtIsNull();
        }

        return templates.stream()
                .map(t -> new ChecklistTemplateListDto(
                        t.getId(),
                        t.getName(),
                        t.getKind().name(),
                        (int) itemRepository.countByTemplateId(t.getId()),
                        t.getArchivedAt()))
                .toList();
    }

    /**
     * C08 — Returns the full detail of a template including all items.
     *
     * @param id the template's surrogate PK
     * @return template detail with items ordered by section then sort_order
     * @throws ApiException 404 NOT_FOUND if no template with that id exists
     */
    public ChecklistTemplateDetailDto getDetail(final Long id) {
        final ChecklistTemplate t = templateRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Checklist template not found: " + id));

        final List<ChecklistTemplateItemDto> items = itemRepository
                .findByTemplateIdOrderBySortOrder(id)
                .stream()
                .map(this::toItemDto)
                .toList();

        return new ChecklistTemplateDetailDto(
                t.getId(),
                t.getName(),
                t.getKind().name(),
                t.getJobTitle() != null ? t.getJobTitle().getId() : null,
                t.getCopiedFrom() != null ? t.getCopiedFrom().getId() : null,
                t.getArchivedAt(),
                items);
    }

    /**
     * C09 — Creates a new checklist template, optionally copying items from a source template.
     *
     * <p>Transaction boundary: the template INSERT, optional item INSERTs, and the audit_log
     * INSERT are all committed atomically. Partial failure rolls back everything.</p>
     *
     * @param request the creation request
     * @param actor   the HR user performing the action
     * @return a map with the new template's {@code id}
     * @throws ApiException 409 DUPLICATE_NAME if the name is already in use
     * @throws ApiException 400 VALIDATION_FAILED for invalid kind/jobTitleId/copiedFromId
     */
    @Transactional
    public Map<String, Long> create(final ChecklistTemplateCreateRequest request,
                                    final Employee actor) {
        if (templateRepository.findByName(request.name()).isPresent()) {
            throw new ApiException(ErrorCodes.DUPLICATE_NAME, HttpStatus.CONFLICT,
                    "This name already exists.");
        }

        final TemplateKind kind;
        try {
            kind = TemplateKind.valueOf(request.kind());
        } catch (IllegalArgumentException e) {
            throw ApiException.fieldValidation(List.of(
                    Map.of("field", "kind", "message", "This field is required.")));
        }

        final OffsetDateTime now = OffsetDateTime.now(clock);
        final ChecklistTemplate template = new ChecklistTemplate();
        template.setName(request.name());
        template.setKind(kind);
        template.setCreatedAt(now);
        template.setCreatedBy(actor.getId());
        template.setUpdatedAt(now);
        template.setUpdatedBy(actor.getId());

        if (request.jobTitleId() != null) {
            final JobTitle jt = jobTitleRepository.findById(request.jobTitleId())
                    .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                            "Job title not found: " + request.jobTitleId()));
            template.setJobTitle(jt);
        }

        if (request.copiedFromId() != null) {
            final ChecklistTemplate source = templateRepository.findById(request.copiedFromId())
                    .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                            "Source template not found: " + request.copiedFromId()));
            template.setCopiedFrom(source);
        }

        templateRepository.save(template);

        // Copy items from the source template if requested
        if (request.copiedFromId() != null) {
            final List<ChecklistTemplateItem> sourceItems =
                    itemRepository.findByTemplateIdOrderBySortOrder(request.copiedFromId());
            for (final ChecklistTemplateItem src : sourceItems) {
                final ChecklistTemplateItem copy = new ChecklistTemplateItem();
                copy.setTemplate(template);
                copy.setName(src.getName());
                copy.setSection(src.getSection());
                copy.setOwnerRole(src.getOwnerRole());
                copy.setItemKind(src.getItemKind());
                copy.setMandatory(src.isMandatory());
                copy.setDueOffsetDays(src.getDueOffsetDays());
                copy.setSortOrder(src.getSortOrder());
                copy.setCreatedAt(now);
                copy.setCreatedBy(actor.getId());
                copy.setUpdatedAt(now);
                copy.setUpdatedBy(actor.getId());
                itemRepository.save(copy);

                auditLogService.record(null, "CHECKLIST_TEMPLATE_ITEM", copy.getId(), "CREATE",
                        actor, null, snapshotItem(copy));
            }
        }

        auditLogService.record(null, "CHECKLIST_TEMPLATE", template.getId(), "CREATE",
                actor, null, snapshotTemplate(template));

        // Name is user-entered free text and is not logged raw (log-injection risk via
        // embedded CR/LF); the audit_log row above already captures it faithfully.
        log.info("ChecklistTemplate created: id={} by employee={}",
                template.getId(), actor.getId());
        return Map.of("id", template.getId());
    }

    /**
     * C10 — Archives a checklist template.
     *
     * <p>Transaction boundary: the UPDATE and the audit_log INSERT are committed atomically.</p>
     *
     * <p>ADM-04: only sets {@code archived_at}; never touches instantiated
     * {@code checklist_item} rows. Archived templates stop appearing in new-onboarding
     * template pickers but existing checklists built from them are unaffected.</p>
     *
     * @param id    the template to archive
     * @param actor the HR user performing the action
     * @throws ApiException 404 NOT_FOUND if no template with that id exists
     */
    @Transactional
    public void archive(final Long id, final Employee actor) {
        final ChecklistTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Checklist template not found: " + id));

        final String before = snapshotTemplate(template);
        final OffsetDateTime now = OffsetDateTime.now(clock);
        template.setArchivedAt(now);
        template.setUpdatedAt(now);
        template.setUpdatedBy(actor.getId());
        templateRepository.save(template);

        auditLogService.record(null, "CHECKLIST_TEMPLATE", id, "ARCHIVE",
                actor, before, snapshotTemplate(template));

        log.info("ChecklistTemplate archived: id={} by employee={}", id, actor.getId());
    }

    /**
     * C11 — Adds a new item to a checklist template.
     *
     * <p>Transaction boundary: the item INSERT and the audit_log INSERT are committed
     * atomically (ADM-05).</p>
     *
     * @param templateId the owning template's id
     * @param request    the item to add
     * @param actor      the HR user performing the action
     * @return the newly created item
     * @throws ApiException 404 NOT_FOUND if no template with that id exists
     * @throws ApiException 400 VALIDATION_FAILED for invalid enum values
     */
    @Transactional
    public ChecklistTemplateItemDto addItem(final Long templateId,
                                            final ChecklistTemplateItemRequest request,
                                            final Employee actor) {
        final ChecklistTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Checklist template not found: " + templateId));

        final ChecklistSection section = parseSection(request.section());
        final OwnerRole ownerRole = parseOwnerRole(request.ownerRole());

        final int nextSortOrder = itemRepository.findByTemplateIdOrderBySortOrder(templateId).size();
        final OffsetDateTime now = OffsetDateTime.now(clock);

        final ChecklistTemplateItem item = new ChecklistTemplateItem();
        item.setTemplate(template);
        item.setName(request.name());
        item.setSection(section);
        item.setOwnerRole(ownerRole);
        item.setMandatory(request.mandatory());
        item.setDueOffsetDays(request.dueOffsetDays());
        item.setSortOrder(nextSortOrder);
        item.setCreatedAt(now);
        item.setCreatedBy(actor.getId());
        item.setUpdatedAt(now);
        item.setUpdatedBy(actor.getId());
        itemRepository.save(item);

        auditLogService.record(null, "CHECKLIST_TEMPLATE_ITEM", item.getId(), "CREATE",
                actor, null, snapshotItem(item));

        log.info("ChecklistTemplateItem created: id={} templateId={} by employee={}",
                item.getId(), templateId, actor.getId());
        return toItemDto(item);
    }

    /**
     * C12 — Updates an existing template item.
     *
     * <p>Transaction boundary: the UPDATE and the audit_log INSERT are committed atomically.</p>
     *
     * <p>ADM-04: editing a template item never touches {@code checklist_item} rows (running
     * checklists snapshot field values at instantiation time).</p>
     *
     * @param templateId the owning template's id (used to validate item belongs to template)
     * @param itemId     the item to update
     * @param request    the updated fields
     * @param actor      the HR user performing the action
     * @return the updated item
     * @throws ApiException 404 NOT_FOUND if the item or template does not exist
     */
    @Transactional
    public ChecklistTemplateItemDto updateItem(final Long templateId,
                                               final Long itemId,
                                               final ChecklistTemplateItemRequest request,
                                               final Employee actor) {
        final ChecklistTemplateItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Item not found: " + itemId));

        // Validate the item belongs to the expected template
        if (!item.getTemplate().getId().equals(templateId)) {
            throw new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Item " + itemId + " does not belong to template " + templateId);
        }

        final String before = snapshotItem(item);
        final ChecklistSection section = parseSection(request.section());
        final OwnerRole ownerRole = parseOwnerRole(request.ownerRole());
        final OffsetDateTime now = OffsetDateTime.now(clock);

        item.setName(request.name());
        item.setSection(section);
        item.setOwnerRole(ownerRole);
        item.setMandatory(request.mandatory());
        item.setDueOffsetDays(request.dueOffsetDays());
        item.setUpdatedAt(now);
        item.setUpdatedBy(actor.getId());
        itemRepository.save(item);

        auditLogService.record(null, "CHECKLIST_TEMPLATE_ITEM", itemId, "UPDATE",
                actor, before, snapshotItem(item));

        log.info("ChecklistTemplateItem updated: id={} by employee={}", itemId, actor.getId());
        return toItemDto(item);
    }

    /**
     * C13 — Deletes a template item.
     *
     * <p>Transaction boundary: DELETE and audit_log INSERT are committed atomically.</p>
     *
     * <p>D-02: if the item has been instantiated into any {@code checklist_item} row, the
     * FK {@code RESTRICT} prevents deletion; the resulting
     * {@link DataIntegrityViolationException} is translated to {@code 409 ITEM_IN_USE}.</p>
     *
     * @param templateId the owning template's id
     * @param itemId     the item to delete
     * @param actor      the HR user performing the action
     * @throws ApiException 404 NOT_FOUND if the item does not exist
     * @throws ApiException 409 ITEM_IN_USE if the item has been instantiated
     */
    @Transactional
    public void deleteItem(final Long templateId, final Long itemId, final Employee actor) {
        final ChecklistTemplateItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Item not found: " + itemId));

        if (!item.getTemplate().getId().equals(templateId)) {
            throw new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Item " + itemId + " does not belong to template " + templateId);
        }

        final String before = snapshotItem(item);

        try {
            itemRepository.delete(item);
            itemRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            log.debug("Cannot delete checklist item id={}: instantiated in checklist_item", itemId);
            throw new ApiException(ErrorCodes.ITEM_IN_USE, HttpStatus.CONFLICT,
                    "This item is in use and cannot be removed.");
        }

        auditLogService.record(null, "CHECKLIST_TEMPLATE_ITEM", itemId, "DELETE",
                actor, before, null);
        log.info("ChecklistTemplateItem deleted: id={} by employee={}", itemId, actor.getId());
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private ChecklistTemplateItemDto toItemDto(final ChecklistTemplateItem item) {
        return new ChecklistTemplateItemDto(
                item.getId(),
                item.getName(),
                item.getSection().name(),
                item.getOwnerRole().name(),
                item.isMandatory(),
                item.getDueOffsetDays(),
                item.getSortOrder());
    }

    private ChecklistSection parseSection(final String value) {
        try {
            return ChecklistSection.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.fieldValidation(List.of(
                    Map.of("field", "section", "message", "This field is required.")));
        }
    }

    private OwnerRole parseOwnerRole(final String value) {
        final OwnerRole role;
        try {
            role = OwnerRole.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.fieldValidation(List.of(
                    Map.of("field", "ownerRole", "message", "This field is required.")));
        }
        if (role == OwnerRole.SYSTEM) {
            throw ApiException.fieldValidation(List.of(
                    Map.of("field", "ownerRole", "message", "This field is required.")));
        }
        return role;
    }

    private String snapshotTemplate(final ChecklistTemplate t) {
        try {
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId() != null ? t.getId() : 0L);
            m.put("name", t.getName());
            m.put("kind", t.getKind().name());
            m.put("archivedAt", t.getArchivedAt() != null ? t.getArchivedAt().toString() : null);
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise template snapshot", e);
            return "{}";
        }
    }

    private String snapshotItem(final ChecklistTemplateItem item) {
        try {
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", item.getId() != null ? item.getId() : 0L);
            m.put("name", item.getName());
            m.put("section", item.getSection().name());
            m.put("ownerRole", item.getOwnerRole().name());
            m.put("mandatory", item.isMandatory());
            m.put("dueOffsetDays", item.getDueOffsetDays());
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise item snapshot", e);
            return "{}";
        }
    }
}
