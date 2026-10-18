package com.cubeworks.hr.settings;

import com.cubeworks.hr.common.BaseEntity;
import com.cubeworks.hr.employee.Document;
import jakarta.persistence.*;

/**
 * A single item within a {@link ChecklistTemplate}.
 *
 * <p>Maps to {@code hr.checklist_template_item}. When an onboarding is created, these items
 * are instantiated as {@code checklist_item} rows. All fields relevant to the running checklist
 * are snapshotted at instantiation time so that subsequent edits to the template (or archiving
 * it) do not change in-progress checklists (ADM-04, data-model §5).</p>
 *
 * <p>The {@link #policyDocument} field links to the PDF shown in the policy-acknowledgement
 * pop-up when {@link #itemKind} is {@link ItemKind#POLICY_ACK} (F01 Q7). A DB CHECK
 * prevents {@code policy_document_id} from being set on non-POLICY_ACK items.</p>
 *
 * <p>The parent {@link ChecklistTemplate} uses {@code ON DELETE CASCADE}, so deleting a
 * template removes its items automatically.</p>
 */
@Entity
@Table(name = "checklist_template_item")
public class ChecklistTemplateItem extends BaseEntity {

    /**
     * The template that owns this item.
     * Lazy: listing template items never requires materialising the parent template object.
     * The cascade DELETE in the DB handles item removal when a template is deleted.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ChecklistTemplate template;

    /** Display name of the checklist item. */
    @Column(name = "name", length = 150, nullable = false)
    private String name;

    /** The UI section this item appears under in the checklist screen. */
    @Enumerated(EnumType.STRING)
    @Column(name = "section", length = 30, nullable = false)
    private ChecklistSection section;

    /** The role responsible for completing this item. */
    @Enumerated(EnumType.STRING)
    @Column(name = "owner_role", length = 10, nullable = false)
    private OwnerRole ownerRole;

    /**
     * The behavioural kind of this item, controlling UI rendering and backend side-effects
     * upon completion (e.g. POLICY_ACK opens a PDF; EQUIPMENT links an asset row).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "item_kind", length = 20, nullable = false)
    private ItemKind itemKind = ItemKind.GENERIC;

    /**
     * Whether this item is mandatory. Mandatory items must be completed (or explicitly
     * cancelled by HR) before the checklist can be marked complete.
     */
    @Column(name = "mandatory", nullable = false)
    private boolean mandatory = true;

    /**
     * Number of days after the employee's start date by which this item is due.
     * Used to compute {@code checklist_item.due_date} at instantiation time.
     */
    @Column(name = "due_offset_days", nullable = false)
    private int dueOffsetDays;

    /**
     * The policy PDF document shown in the acknowledgement pop-up for
     * {@link ItemKind#POLICY_ACK} items (F01 Q7).
     * Must be {@code null} for all other item kinds (enforced by a DB CHECK).
     * Lazy: the document object is only needed when rendering the policy pop-up, not
     * when listing the checklist.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_document_id")
    private Document policyDocument;

    /** Display order within the section. Lower values appear first. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Required no-arg constructor for JPA. */
    public ChecklistTemplateItem() {}

    // ── Accessors ────────────────────────────────────────────────────────────

    public ChecklistTemplate getTemplate() { return template; }
    public void setTemplate(final ChecklistTemplate template) { this.template = template; }

    public String getName() { return name; }
    public void setName(final String name) { this.name = name; }

    public ChecklistSection getSection() { return section; }
    public void setSection(final ChecklistSection section) { this.section = section; }

    public OwnerRole getOwnerRole() { return ownerRole; }
    public void setOwnerRole(final OwnerRole ownerRole) { this.ownerRole = ownerRole; }

    public ItemKind getItemKind() { return itemKind; }
    public void setItemKind(final ItemKind itemKind) { this.itemKind = itemKind; }

    public boolean isMandatory() { return mandatory; }
    public void setMandatory(final boolean mandatory) { this.mandatory = mandatory; }

    public int getDueOffsetDays() { return dueOffsetDays; }
    public void setDueOffsetDays(final int dueOffsetDays) { this.dueOffsetDays = dueOffsetDays; }

    public Document getPolicyDocument() { return policyDocument; }
    public void setPolicyDocument(final Document policyDocument) { this.policyDocument = policyDocument; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(final int sortOrder) { this.sortOrder = sortOrder; }
}
