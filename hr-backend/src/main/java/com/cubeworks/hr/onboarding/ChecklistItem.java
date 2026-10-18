package com.cubeworks.hr.onboarding;

import com.cubeworks.hr.asset.Asset;
import com.cubeworks.hr.common.BaseEntity;
import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.settings.ChecklistSection;
import com.cubeworks.hr.settings.ChecklistTemplateItem;
import com.cubeworks.hr.settings.ItemKind;
import com.cubeworks.hr.settings.OwnerRole;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One actionable item in an employee's onboarding or offboarding checklist.
 *
 * <p>Maps to {@code hr.checklist_item}. Items are created in two ways:</p>
 * <ol>
 *   <li>Instantiated from a {@link ChecklistTemplateItem} when the onboarding starts —
 *       key fields ({@link #name}, {@link #section}, {@link #ownerRole}, {@link #mandatory})
 *       are snapshotted so that later template edits do not affect this checklist (ADM-04).</li>
 *   <li>Added manually by HR ("+ Add item") — {@link #templateItem} is {@code null} in this case.</li>
 * </ol>
 *
 * <p>The same table holds onboarding ({@link ChecklistPhase#ONBOARDING}) and offboarding
 * ({@link ChecklistPhase#OFFBOARDING}) items.</p>
 *
 * <p>Offboarding "remove access" items link back to the corresponding onboarding
 * {@link ItemKind#ACCOUNT} item via {@link #removesItem} (FK to self). This models the
 * relationship between "account provisioned" and "account removed" without a separate table.</p>
 *
 * <p>DB CHECKs enforce:</p>
 * <ul>
 *   <li>{@code (status = 'DONE') = (done_at IS NOT NULL AND done_by IS NOT NULL)}</li>
 *   <li>{@link #removesItem} is only non-null for {@link ChecklistPhase#OFFBOARDING} items.</li>
 * </ul>
 */
@Entity
@Table(name = "checklist_item")
public class ChecklistItem extends BaseEntity {

    /**
     * The employee this checklist item belongs to.
     * Lazy: avoid N+1 when loading a batch of checklist items — the employee data is
     * rarely needed alongside the item details.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** Whether this is an onboarding or offboarding checklist item. */
    @Enumerated(EnumType.STRING)
    @Column(name = "phase", length = 12, nullable = false)
    private ChecklistPhase phase;

    /**
     * The template item this was instantiated from, or {@code null} for manually added items.
     * Lazy: the template source is only needed in the Settings/template management screens.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_item_id")
    private ChecklistTemplateItem templateItem;

    /**
     * Snapshot of the template item's name at instantiation time.
     * Stored so that renaming or archiving the template item does not affect this item (ADM-04).
     */
    @Column(name = "name", length = 150, nullable = false)
    private String name;

    /**
     * Snapshot of the UI section at instantiation time.
     * Same rationale as {@link #name} — ADM-04 snapshot.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "section", length = 30, nullable = false)
    private ChecklistSection section;

    /**
     * Snapshot of the owner role at instantiation time (ADM-04).
     * Stored as a string column rather than a hard FK so the snapshot survives role renames.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "owner_role", length = 10, nullable = false)
    private OwnerRole ownerRole;

    /** Snapshot of the item kind at instantiation time (ADM-04). */
    @Enumerated(EnumType.STRING)
    @Column(name = "item_kind", length = 20, nullable = false)
    private ItemKind itemKind = ItemKind.GENERIC;

    /** Snapshot of the mandatory flag at instantiation time (ADM-04). */
    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    /**
     * Date by which this item must be completed. Computed from the template item's
     * {@code due_offset_days} relative to the employee's start date at instantiation time.
     */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** Current completion status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private ChecklistItemStatus status = ChecklistItemStatus.OPEN;

    /**
     * Timestamp when this item was completed.
     * Non-null if and only if {@link #status} is {@link ChecklistItemStatus#DONE} (DB CHECK).
     */
    @Column(name = "done_at")
    private OffsetDateTime doneAt;

    /**
     * The employee who completed this item.
     * Non-null if and only if {@link #status} is {@link ChecklistItemStatus#DONE} (DB CHECK).
     * Lazy: only needed when showing who completed an item in the audit trail.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "done_by")
    private Employee doneBy;

    /**
     * The physical asset linked to this {@link ItemKind#EQUIPMENT} item.
     * Null for all other item kinds.
     * Lazy: the asset detail is only needed on the equipment detail screen.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    /**
     * For offboarding "remove access" items ({@link ItemKind#ACCOUNT}): the onboarding
     * {@link ItemKind#ACCOUNT} item whose provisioning this item revokes.
     * Null for all onboarding items and offboarding items not of type ACCOUNT.
     * A DB CHECK prevents this from being set on onboarding-phase items.
     * Lazy: the linked onboarding item is only needed in the offboarding detail view.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "removes_item_id")
    private ChecklistItem removesItem;

    /**
     * Timestamp when the overdue reminder e-mail was sent for this item (G-05).
     * Null means either the item is not yet overdue or the reminder has not fired yet;
     * once set, {@link com.cubeworks.hr.task.OverdueReminderScheduler} never reminds again
     * for this item.
     */
    @Column(name = "overdue_reminder_at")
    private OffsetDateTime overdueReminderAt;

    /** Required no-arg constructor for JPA. */
    protected ChecklistItem() {}

    // ── Accessors ────────────────────────────────────────────────────────────

    public Employee getEmployee() { return employee; }
    public void setEmployee(final Employee employee) { this.employee = employee; }

    public ChecklistPhase getPhase() { return phase; }
    public void setPhase(final ChecklistPhase phase) { this.phase = phase; }

    public ChecklistTemplateItem getTemplateItem() { return templateItem; }
    public void setTemplateItem(final ChecklistTemplateItem templateItem) { this.templateItem = templateItem; }

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

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(final LocalDate dueDate) { this.dueDate = dueDate; }

    public ChecklistItemStatus getStatus() { return status; }
    public void setStatus(final ChecklistItemStatus status) { this.status = status; }

    public OffsetDateTime getDoneAt() { return doneAt; }
    public void setDoneAt(final OffsetDateTime doneAt) { this.doneAt = doneAt; }

    public Employee getDoneBy() { return doneBy; }
    public void setDoneBy(final Employee doneBy) { this.doneBy = doneBy; }

    public Asset getAsset() { return asset; }
    public void setAsset(final Asset asset) { this.asset = asset; }

    public ChecklistItem getRemovesItem() { return removesItem; }
    public void setRemovesItem(final ChecklistItem removesItem) { this.removesItem = removesItem; }

    public OffsetDateTime getOverdueReminderAt() { return overdueReminderAt; }
    public void setOverdueReminderAt(final OffsetDateTime overdueReminderAt) { this.overdueReminderAt = overdueReminderAt; }
}
