package com.cubeworks.hr.engine;

import java.util.Map;

/**
 * Port interface for all outgoing calls to the CaseFabric engine REST API.
 *
 * <h2>Gate 2 intentional stub</h2>
 * <p>The real HTTP implementation is gate 3 work. This gate wires only
 * {@link NoOpCaseEngineClient} — it logs every call at INFO level so gate 3 can
 * replace it without touching any service that already depends on this interface.
 * All services must inject this interface, never the concrete class.</p>
 *
 * <h2>Call contract (R-ARC-5)</h2>
 * <ul>
 *   <li>Methods are called <em>after</em> the DB transaction has committed — never inside
 *       the business {@code @Transactional} scope.</li>
 *   <li>A failed call must be retried; it must never silently swallow the failure or roll
 *       back the already-committed business data.</li>
 *   <li>All case-file values passed here must conform to {@code 03-case-file.md} (R-CM-2,
 *       R-ARC-2, R-ARC-3): IDs, dates, booleans and enums only — no personal data.</li>
 * </ul>
 */
public interface CaseEngineClient {

    /**
     * Instructs the engine to start a new case for the given employee.
     *
     * <p>Called once, immediately after {@code POST /api/onboardings} commits the
     * {@code employee} row. The {@code initialCaseFile} must contain all fields listed in
     * {@code 03-case-file.md} §4 (Employee and Onboarding items).</p>
     *
     * @param caseId          the engine case identifier — equals {@code employee.employee_code}
     * @param initialCaseFile JSON-safe map of case-file items; no personal data (R-ARC-3)
     */
    void startCase(String caseId, Map<String, Object> initialCaseFile);

    /**
     * Updates one item in an existing case's case file.
     *
     * <p>Used to reflect DB-side state changes in the engine — for example, setting
     * {@code Onboarding.itRequestSent = true} after HR sends the IT request (A14).</p>
     *
     * @param caseId the engine case identifier
     * @param item   the case-file item name (e.g. {@code "Onboarding"}), from {@code 03-case-file.md}
     * @param fields field-value pairs to merge into the item; must not contain personal data
     */
    void updateCaseFile(String caseId, String item, Map<String, Object> fields);

    /**
     * Completes an engine human task with the given output.
     *
     * <p>Called by the undo-window scheduler ({@link com.cubeworks.hr.onboarding.UndoWindowScheduler})
     * after the window has expired, or immediately if no undo window applies. The
     * {@code output} map becomes the task's completion payload in the case file.</p>
     *
     * @param engineTaskId the CaseFabric engine task ID (the PK of {@code engine_task})
     * @param output       task completion output; must not contain personal data
     */
    void completeTask(String engineTaskId, Map<String, Object> output);

    /**
     * Explicitly starts a plan item (Available -&gt; Active), bypassing its own entry criterion.
     *
     * <p>Added 2026-10-16, Wave 1: the "Equipment request" human task's entry criterion
     * ({@code employeecase.case}'s {@code ec_equipment_request}) is a {@code caseFileItemOnPart}
     * listening for an "update" transition on the long-lived "Onboarding" case-file item, gated
     * by an {@code ifPart} checking {@code itRequestSent == true}. Confirmed live, 2026-10-16
     * (direct inspection of {@code GET /cases/{id}/history/events}, reproduced across a full
     * engine process restart): this onPart fires — and is evaluated — only on the FIRST-ever
     * "Update" transition of "Onboarding", which happens long before {@code itRequestSent}
     * becomes true (checklist ticking updates "Onboarding.mandatoryItemsDone" first). Every
     * later "Onboarding" update, including the real {@code itRequestSent=true} one, is logged by
     * the engine as "Adding delayed event Update to myself" and never actually re-evaluated — not
     * a transient hiccup, since it survives an engine restart. This is specific to
     * {@code caseFileItemOnPart} sentries on a frequently-updated item; plan-item-to-plan-item
     * sentries (e.g. "Remaining setup"'s, keyed off "Equipment request" completing) don't share
     * this failure mode, since a human task's own "complete" transition only ever happens once.
     * {@code OnboardingService.sendItRequest} calls this immediately after
     * {@link #updateCaseFile} to directly drive the one plan item the model already designates as
     * next — not a Spring workflow decision (R-ARC-1), just a reliable substitute for a sentry
     * mechanism confirmed broken in this engine version for this one case-file-item shape.</p>
     *
     * @param caseId        the engine case identifier
     * @param planItemName  the plan item's {@code name} (e.g. {@code "Equipment request"}), as
     *                      shown in {@code GET /cases/{id}}'s {@code planitems[].name}
     */
    void startPlanItem(String caseId, String planItemName);

    /**
     * Lists the open (not yet completed) human tasks on a case.
     *
     * <p>Used by {@link EngineTaskWatcher} to (a) resolve the engine's real task id for a task
     * Spring itself created and mirrored into {@code engine_task} with a placeholder id (the
     * engine assigns the real id only once the case plan item actually activates, which Spring
     * cannot know synchronously at case-start time — see {@link EngineTaskWatcher}'s class doc),
     * and (b) discover tasks the engine raises on its own via a sentry (e.g. "Remaining setup")
     * that Spring never directly creates a row for.</p>
     *
     * @param caseId the engine case identifier
     * @return each open task as a map with at least {@code "id"} and {@code "taskName"} keys,
     *         matching the engine's own {@code GET /tasks/case/{caseId}} response shape
     */
    java.util.List<Map<String, Object>> findOpenTasks(String caseId);
}
