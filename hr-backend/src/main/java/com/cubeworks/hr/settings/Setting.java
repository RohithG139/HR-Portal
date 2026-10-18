package com.cubeworks.hr.settings;

import com.cubeworks.hr.common.AuditableEntity;
import jakarta.persistence.*;

/**
 * A configurable system setting — either a background-scheduler timer or a per-task due-date
 * period (R-ARC-7, G-08).
 *
 * <p>Maps to {@code hr.setting}. The primary key is the {@code code} string (e.g.
 * {@code BENCH_CHECK}, {@code Approve PIP}), not a generated bigint, so this entity extends
 * {@link AuditableEntity} rather than {@link com.cubeworks.hr.common.BaseEntity}.</p>
 *
 * <p>DB CHECKs enforce the semantic constraints:</p>
 * <ul>
 *   <li>{@code TASK_DUE} rows always have {@code value_int IS NOT NULL} and
 *       {@code value_unit = 'DAYS'}.</li>
 *   <li>Only {@code code = 'KPI_CYCLE'} may have a non-null {@code value_text}.</li>
 *   <li>{@code can_disable = false} implies {@code enabled = true} (always-on settings).</li>
 * </ul>
 *
 * <p>Values used by a running cycle (e.g. PIP duration) are copied into the case file
 * or domain row when that cycle starts, so that later changes to the setting do not
 * retroactively affect in-flight cases (ADM-02).</p>
 */
@Entity
@Table(name = "setting")
public class Setting extends AuditableEntity {

    /**
     * The setting identifier — e.g. {@code BENCH_CHECK} or the engine task name
     * {@code "Approve PIP"}. Used as the foreign key in {@code email_outbox} lookup logic.
     */
    @Id
    @Column(name = "code", length = 60, nullable = false, updatable = false)
    private String code;

    /** Whether this setting controls a timer ({@link SettingCategory#TIMER}) or a task
     *  due-date window ({@link SettingCategory#TASK_DUE}). */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 10, nullable = false)
    private SettingCategory category;

    /** The feature module this setting belongs to (e.g. {@code ONBOARDING}, {@code PIP}). */
    @Column(name = "module", length = 30, nullable = false)
    private String module;

    /** Human-readable label shown in the Settings UI. */
    @Column(name = "label", length = 100, nullable = false)
    private String label;

    /**
     * Numeric part of the timer value or the due-date day count.
     * Required for {@link SettingCategory#TASK_DUE}; optional for
     * {@link SettingCategory#TIMER} rows that use {@code value_text} instead.
     */
    @Column(name = "value_int")
    private Integer valueInt;

    /**
     * Unit for {@link #valueInt}.
     * For {@link SettingCategory#TASK_DUE} this is always {@link SettingValueUnit#DAYS}
     * (enforced by a DB CHECK).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "value_unit", length = 12)
    private SettingValueUnit valueUnit;

    /**
     * Textual cycle value — only non-null for {@code code = 'KPI_CYCLE'} (DB CHECK).
     * Represents how often KPI performance is measured and reset.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "value_text", length = 20)
    private SettingValueText valueText;

    /**
     * Role or group that is notified when this timer or task event fires.
     * Null means no automatic notification is sent for this setting.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_role", length = 15)
    private RecipientRole recipientRole;

    /**
     * Whether HR is allowed to toggle {@link #enabled} for this setting.
     * If {@code false} the setting is always on and the toggle is hidden in the UI.
     */
    @Column(name = "can_disable", nullable = false)
    private boolean canDisable;

    /**
     * Whether this setting is currently active. A DB CHECK prevents disabling a setting
     * that has {@code can_disable = false}.
     */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** Required no-arg constructor for JPA. */
    protected Setting() {}

    // ── Accessors ────────────────────────────────────────────────────────────

    public String getCode() { return code; }
    public void setCode(final String code) { this.code = code; }

    public SettingCategory getCategory() { return category; }
    public void setCategory(final SettingCategory category) { this.category = category; }

    public String getModule() { return module; }
    public void setModule(final String module) { this.module = module; }

    public String getLabel() { return label; }
    public void setLabel(final String label) { this.label = label; }

    public Integer getValueInt() { return valueInt; }
    public void setValueInt(final Integer valueInt) { this.valueInt = valueInt; }

    public SettingValueUnit getValueUnit() { return valueUnit; }
    public void setValueUnit(final SettingValueUnit valueUnit) { this.valueUnit = valueUnit; }

    public SettingValueText getValueText() { return valueText; }
    public void setValueText(final SettingValueText valueText) { this.valueText = valueText; }

    public RecipientRole getRecipientRole() { return recipientRole; }
    public void setRecipientRole(final RecipientRole recipientRole) { this.recipientRole = recipientRole; }

    public boolean isCanDisable() { return canDisable; }
    public void setCanDisable(final boolean canDisable) { this.canDisable = canDisable; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(final boolean enabled) { this.enabled = enabled; }
}
