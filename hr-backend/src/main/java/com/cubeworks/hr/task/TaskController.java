package com.cubeworks.hr.task;

import com.cubeworks.hr.employee.Employee;
import com.cubeworks.hr.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for the My Tasks screen (A36) and undo-window undo action (A37).
 *
 * <h2>Covered endpoints</h2>
 * <ul>
 *   <li>A36 {@code GET  /api/tasks}        — paginated task list; all roles allowed</li>
 *   <li>A36 {@code GET  /api/tasks/count}  — task count for the bell badge; all roles allowed</li>
 *   <li>A37 {@code POST /api/tasks/{id}/undo} — undo a pending task completion within window</li>
 * </ul>
 *
 * <p>Controllers stay thin (R-CODE-1): all business logic lives in {@link TaskService}.</p>
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final CurrentUser currentUser;

    /**
     * Constructor injection (R-CODE-1).
     *
     * @param taskService service holding all task business logic
     * @param currentUser resolves the authenticated employee from the JWT
     */
    public TaskController(final TaskService taskService,
                           final CurrentUser currentUser) {
        this.taskService = taskService;
        this.currentUser = currentUser;
    }

    /**
     * A36 — Returns the caller's task list, filtered and paginated.
     *
     * <p>All roles are allowed; the service filters tasks by the caller's role. IT admin sees
     * checklist items + IT engine tasks. HR sees "Onboarding checklist" engine tasks. Employee
     * sees their own EMPLOYEE-owned checklist items.</p>
     *
     * @param module optional module filter (e.g. "ONBOARDING")
     * @param q      optional free-text search on task title
     * @param page   zero-based page index (default 0)
     * @return 200 with task page DTO
     */
    @GetMapping
    public ResponseEntity<TaskService.TaskPageDto> getTasks(
            @RequestParam(required = false) final String module,
            @RequestParam(required = false) final String q,
            @RequestParam(defaultValue = "0") final int page) {
        final Employee caller = currentUser.require();
        return ResponseEntity.ok(taskService.getMyTasks(caller, module, q, page));
    }

    /**
     * A36 — Returns the count of open tasks for the calling user (badge in top bar).
     *
     * @return 200 with {@code {"count": N}}
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> countTasks() {
        final Employee caller = currentUser.require();
        final long count = taskService.countMyTasks(caller);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * A37 — Cancels a pending task completion within the undo window (R-API-6).
     *
     * <p>Returns 200 on success. Returns 409 UNDO_EXPIRED if the window has already closed.
     * Returns 403 FORBIDDEN if the caller did not submit the pending completion (G-13 ownership).</p>
     *
     * @param taskId the engine_task surrogate PK
     * @return 200 on success
     */
    @PostMapping("/{taskId}/undo")
    public ResponseEntity<Void> undoTask(@PathVariable final Long taskId) {
        final Employee caller = currentUser.require();
        taskService.undoTask(taskId, caller);
        return ResponseEntity.ok().build();
    }
}
