package com.cubeworks.hr.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link Notification}.
 *
 * <p>Counting unread notifications uses the partial index {@code ix_notification_unread}
 * for O(1) reads — the index only contains unread rows.</p>
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Whether any notification with this title was already raised about this employee.
     * Used to raise one-off alerts (e.g. "Onboarding overdue") exactly once.
     *
     * @param employeeId the employee the notification is about
     * @param title      the exact notification title
     * @return true if at least one such notification exists
     */
    boolean existsByEmployee_IdAndTitle(Long employeeId, String title);

    /**
     * Returns a page of notifications for the given recipient, most recent first.
     *
     * <p>Optionally filtered to unread-only when {@code unreadOnly = true}.</p>
     *
     * @param recipientId the recipient employee ID
     * @param unreadOnly  when true, return only unread (readAt IS NULL) notifications
     * @param pageable    paging parameters
     * @return a page of notifications
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.recipient.id = :recipientId
              AND (:unreadOnly = false OR n.readAt IS NULL)
            ORDER BY n.createdAt DESC
            """)
    Page<Notification> findForRecipient(
            @Param("recipientId") Long recipientId,
            @Param("unreadOnly") boolean unreadOnly,
            Pageable pageable);

    /**
     * Counts unread notifications for the given recipient.
     *
     * <p>Backed by {@code ix_notification_unread}: this query runs in O(1) regardless of total
     * notification history size.</p>
     *
     * @param recipientId the recipient employee ID
     * @return number of unread notifications
     */
    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.recipient.id = :recipientId
              AND n.readAt IS NULL
            """)
    long countUnread(@Param("recipientId") Long recipientId);

    /**
     * Marks all unread notifications as read for the given recipient.
     *
     * @param recipientId the recipient employee ID
     */
    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.readAt = CURRENT_TIMESTAMP
            WHERE n.recipient.id = :recipientId
              AND n.readAt IS NULL
            """)
    void markAllRead(@Param("recipientId") Long recipientId);
}
