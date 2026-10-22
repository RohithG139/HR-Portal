package com.cubeworks.hr.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * No-operation implementation of {@link CaseEngineClient}, active only under the {@code test}
 * Spring profile.
 *
 * <h2>Why this exists</h2>
 * <p>{@link RealCaseEngineClient} (2026-10-16, Wave 1) is the active implementation everywhere
 * else — a real engine is always available in dev (both local docker-compose projects) and
 * must be in any real deployment. This NoOp implementation only still runs under integration
 * tests (see {@code AbstractIntegrationTest}'s {@code @ActiveProfiles("test")}), which have no
 * live CaseFabric engine to call and must not attempt real HTTP calls during a test run. All
 * engine-bound calls here are logged at INFO level instead, confirming what payload would have
 * been sent.</p>
 *
 * <h2>Thread safety</h2>
 * <p>This bean is stateless — logging is thread-safe by SLF4J convention. Safe to call
 * from multiple threads (e.g. the undo-window scheduler) without synchronisation.</p>
 */
@Component
@Profile("test")
public class NoOpCaseEngineClient implements CaseEngineClient {

    private static final Logger log = LoggerFactory.getLogger(NoOpCaseEngineClient.class);

    /**
     * Logs the intended case-start call without contacting the engine.
     *
     * @param caseId          the case identifier (equals employee_code)
     * @param initialCaseFile the initial case-file fields that would be sent
     */
    @Override
    public void startCase(final String caseId, final Map<String, Object> initialCaseFile) {
        // Gate 2 stub — gate 3 replaces this with a real HTTP POST to the engine.
        log.info("[NoOpCaseEngineClient] startCase caseId={} initialCaseFile={}",
                caseId, initialCaseFile);
    }

    /**
     * Logs the intended case-file update without contacting the engine.
     *
     * @param caseId the case identifier
     * @param item   the case-file item (e.g. "Onboarding")
     * @param fields fields to merge into the item
     */
    @Override
    public void updateCaseFile(final String caseId, final String item,
                               final Map<String, Object> fields) {
        // Gate 2 stub — gate 3 sends a PATCH to the engine case-file API.
        log.info("[NoOpCaseEngineClient] updateCaseFile caseId={} item={} fields={}",
                caseId, item, fields);
    }

    /**
     * Logs the intended task-completion call without contacting the engine.
     *
     * @param engineTaskId the engine task ID
     * @param output       the task completion output payload
     */
    @Override
    public void createCaseFileItem(final String caseId, final String item,
                                   final Map<String, Object> fields) {
        log.info("[NoOpCaseEngineClient] createCaseFileItem caseId={} item={} fields={}",
                caseId, item, fields);
    }

    @Override
    public void completeTask(final String engineTaskId, final Map<String, Object> output) {
        // Gate 2 stub — gate 3 sends a POST to the engine task-complete endpoint.
        log.info("[NoOpCaseEngineClient] completeTask engineTaskId={} output={}",
                engineTaskId, output);
    }

    /**
     * No live engine to query under tests — always empty, so {@link EngineTaskWatcher} finds
     * nothing to reconcile and does nothing, same no-op spirit as every other method here.
     */
    @Override
    public java.util.List<Map<String, Object>> findOpenTasks(final String caseId) {
        log.info("[NoOpCaseEngineClient] findOpenTasks caseId={}", caseId);
        return java.util.List.of();
    }

    /**
     * Logs the intended plan-item start without contacting the engine.
     *
     * @param caseId       the case identifier
     * @param planItemName the plan item's name
     */
    @Override
    public void startPlanItem(final String caseId, final String planItemName) {
        log.info("[NoOpCaseEngineClient] startPlanItem caseId={} planItemName={}", caseId, planItemName);
    }
}
