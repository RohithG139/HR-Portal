package com.cubeworks.hr.notification;

import com.cubeworks.hr.common.error.ApiException;
import com.cubeworks.hr.common.error.ErrorCodes;
import com.cubeworks.hr.employee.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Business logic for in-app notifications (A38, R-UI-3).
 *
 * <p>Every operation is scoped to the authenticated caller's recipient id — a user can only
 * read or mark their own notifications (R-API-2 ownership check).</p>
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param notificationRepository repository for notification CRUD
     * @param clock                  application-wide clock bean
     */
    public NotificationService(final NotificationRepository notificationRepository,
                                final Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    /**
     * Returns a page of notifications for the calling user, rendered as plain maps
     * (the response shape is simple enough that a dedicated DTO record isn't needed).
     *
     * @param caller     the authenticated employee
     * @param unreadOnly when true, returns only unread notifications
     * @param page       zero-based page index
     * @param size       page size
     * @return the page content plus paging metadata
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getNotifications(final Employee caller, final boolean unreadOnly,
                                                 final int page, final int size) {
        final Page<Notification> notifPage = notificationRepository.findForRecipient(
                caller.getId(), unreadOnly, PageRequest.of(page, size));

        final List<Map<String, Object>> items = notifPage.getContent().stream()
                .map(n -> Map.<String, Object>of(
                        "id", n.getId(),
                        "title", n.getTitle(),
                        "message", n.getMessage() != null ? n.getMessage() : "",
                        "linkRoute", n.getLinkRoute() != null ? n.getLinkRoute() : "",
                        "read", n.getReadAt() != null,
                        "createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : ""
                ))
                .toList();

        return Map.of(
                "content", items,
                "totalElements", notifPage.getTotalElements(),
                "totalPages", notifPage.getTotalPages(),
                "page", page
        );
    }

    /**
     * Returns the unread notification count for the calling user's bell badge (R-UI-3).
     *
     * @param caller the authenticated employee
     * @return the unread count
     */
    @Transactional(readOnly = true)
    public long countUnread(final Employee caller) {
        return notificationRepository.countUnread(caller.getId());
    }

    /**
     * Marks one notification as read.
     *
     * <p>Atomic: the ownership check, the {@code readAt} update and the save must not be
     * split — a concurrent request must see a consistent read/not-read state.</p>
     *
     * @param id     the notification id
     * @param caller the authenticated employee
     * @throws ApiException 404 NOT_FOUND if the notification doesn't exist
     * @throws ApiException 403 FORBIDDEN if it belongs to a different recipient
     */
    @Transactional
    public void markRead(final Long id, final Employee caller) {
        final Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Notification " + id + " not found"));

        // Ownership check: recipient must be the caller (R-API-2)
        if (!n.getRecipient().getId().equals(caller.getId())) {
            throw new ApiException(ErrorCodes.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Not your notification");
        }

        if (n.getReadAt() == null) {
            n.setReadAt(OffsetDateTime.now(clock));
            notificationRepository.save(n);
        }
    }

    /**
     * Marks all unread notifications for the calling user as read.
     *
     * @param caller the authenticated employee
     */
    @Transactional
    public void markAllRead(final Employee caller) {
        notificationRepository.markAllRead(caller.getId());
    }
}
