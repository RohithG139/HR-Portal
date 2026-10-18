package com.cubeworks.hr.notification;

import com.cubeworks.hr.common.BaseEntity;
import com.cubeworks.hr.employee.Employee;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * An in-app notification displayed in the notification bell (R-UI-3).
 *
 * <p>Maps to {@code hr.notification}. Notifications are created by service layer events
 * (e.g. a task being assigned, an overdue reminder firing) and consumed by the frontend
 * via a polling or SSE endpoint. Marking a notification as read sets {@link #readAt}.</p>
 *
 * <p>The {@link #employee} field is the subject of the notification (e.g. the employee
 * whose task is overdue), distinct from {@link #recipient} who receives the notification.
 * This allows "task X for employee Y is overdue" to link directly to Y's profile.</p>
 *
 * <p>The convention {@code created_at} column from {@link BaseEntity} serves as the
 * notification timestamp; the DDL also lists it explicitly with a default for documentation
 * purposes — both refer to the same single column.</p>
 */
@Entity
@Table(name = "notification")
public class Notification extends BaseEntity {

    /**
     * The employee who should see this notification in their bell.
     * Lazy: avoid N+1 when loading a batch of notifications for the bell count.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Employee recipient;

    /** Short title displayed in the notification dropdown (max 200 chars). */
    @Column(name = "title", length = 200, nullable = false)
    private String title;

    /**
     * Frontend route to navigate to when the notification is clicked (e.g.
     * {@code /employees/42/onboarding}). Null means the notification has no clickable link.
     */
    @Column(name = "link_route", length = 200)
    private String linkRoute;

    /**
     * The employee the notification is about (e.g. the employee whose task is overdue).
     * May differ from {@link #recipient} — an HR notification about employee John Smith
     * would have {@code recipient = HR user} and {@code employee = John Smith}.
     * Null for system-wide notifications not tied to a specific employee.
     * Lazy: rarely needed just to display the notification in the bell.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    /**
     * Optional free-text message body for the notification (V4 migration).
     * Used for equipment-remark notifications (AC-13) where IT admin needs the full
     * remark text. System notifications and bell-only alerts leave this null.
     */
    @Column(name = "message", columnDefinition = "text")
    private String message;

    /**
     * Timestamp when the recipient acknowledged (read) this notification.
     * Null means the notification is unread. Used by the partial index
     * {@code ix_notification_unread} to efficiently count/list unread notifications.
     */
    @Column(name = "read_at")
    private OffsetDateTime readAt;

    /**
     * No-arg constructor for JPA, and for application code to construct a new row via the
     * public setters below (this codebase uses plain setters rather than a builder, so the
     * constructor must be reachable from other feature packages, e.g. {@code onboarding}).
     */
    public Notification() {}

    // ── Accessors ────────────────────────────────────────────────────────────

    public Employee getRecipient() { return recipient; }
    public void setRecipient(final Employee recipient) { this.recipient = recipient; }

    public String getTitle() { return title; }
    public void setTitle(final String title) { this.title = title; }

    public String getLinkRoute() { return linkRoute; }
    public void setLinkRoute(final String linkRoute) { this.linkRoute = linkRoute; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(final Employee employee) { this.employee = employee; }

    public String getMessage() { return message; }
    public void setMessage(final String message) { this.message = message; }

    public OffsetDateTime getReadAt() { return readAt; }
    public void setReadAt(final OffsetDateTime readAt) { this.readAt = readAt; }
}
