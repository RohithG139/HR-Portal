package com.cubeworks.hr.notification;

import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for in-app notifications (A38, R-UI-3).
 *
 * <h2>Covered endpoints</h2>
 * <ul>
 *   <li>A38 {@code GET  /api/notifications}           — all roles: paginated notification list</li>
 *   <li>A38 {@code GET  /api/notifications/count}     — all roles: unread count for bell badge</li>
 *   <li>A38 {@code POST /api/notifications/{id}/read} — all roles: mark one notification as read</li>
 *   <li>A38 {@code POST /api/notifications/read-all}  — all roles: mark all unread as read</li>
 * </ul>
 *
 * <p>Controllers stay thin (R-CODE-1): all logic, including the ownership guard, lives in
 * {@link NotificationService}.</p>
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param notificationService service holding all notification business logic
     * @param currentUser         resolves the authenticated employee from the JWT
     */
    public NotificationController(final NotificationService notificationService,
                                   final CurrentUser currentUser) {
        this.notificationService = notificationService;
        this.currentUser = currentUser;
    }

    /**
     * A38 — Returns a page of notifications for the calling user.
     *
     * @param unreadOnly when "true", returns only unread notifications (default: false)
     * @param page       zero-based page index (default 0)
     * @param size       page size (default 20)
     * @return 200 with paginated notifications
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getNotifications(
            @RequestParam(defaultValue = "false") final boolean unreadOnly,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        final Employee caller = currentUser.require();
        return ResponseEntity.ok(notificationService.getNotifications(caller, unreadOnly, page, size));
    }

    /**
     * A38 — Returns the unread notification count for the bell badge (R-UI-3).
     *
     * @return 200 with {@code {"count": N}}
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> countUnread() {
        final Employee caller = currentUser.require();
        return ResponseEntity.ok(Map.of("count", notificationService.countUnread(caller)));
    }

    /**
     * A38 — Marks one notification as read.
     *
     * @param id the notification ID
     * @return 200 OK, or 403 FORBIDDEN if the notification does not belong to the caller
     */
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable final Long id) {
        final Employee caller = currentUser.require();
        notificationService.markRead(id, caller);
        return ResponseEntity.ok().build();
    }

    /**
     * A38 — Marks all unread notifications for the calling user as read.
     *
     * @return 200 OK
     */
    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        final Employee caller = currentUser.require();
        notificationService.markAllRead(caller);
        return ResponseEntity.ok().build();
    }
}
