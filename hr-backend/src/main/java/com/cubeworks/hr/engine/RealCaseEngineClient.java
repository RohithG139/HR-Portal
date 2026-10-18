package com.cubeworks.hr.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Real HTTP implementation of {@link CaseEngineClient}, calling the live CaseFabric engine's
 * REST API (added 2026-10-16, Wave 1 — "real CaseFabric engine connection").
 *
 * <h2>Authentication</h2>
 * <p>hr-backend calls the engine as its own fixed technical identity — a Keycloak
 * client-credentials client ({@code hr-backend}, {@code infra/keycloak/cubeworks-hr-realm.json})
 * — never a pass-through of an end user's own token. This is simpler (one credential, not one
 * per employee) and is the only thing that works for scheduled/background calls (the undo-window
 * executor, the overdue-reminder scheduler) that have no end-user request to borrow a token
 * from. The engine trusts this realm additively alongside its original Dex identity provider
 * (see {@code casefabric/getting-started/src/conf/local.conf}'s second {@code oidc} entry).</p>
 *
 * <h2>Why the Keycloak token URL and the engine base URL are separately configurable</h2>
 * <p>Both must resolve correctly from wherever hr-backend is actually running — the host (dev,
 * {@code ./mvnw spring-boot:run}) or the {@code hr-backend} dev container
 * (repo-root {@code docker-compose.yml}). From the host both are {@code localhost}-published
 * ports; from the container, Keycloak is a same-network Docker service name
 * ({@code http://keycloak:8080/...}) and the engine (a different, never-merged compose project)
 * is reached via {@code host.docker.internal} (confirmed resolvable without any
 * {@code extra_hosts} entry on Docker Desktop). <strong>The Keycloak token URL used here must be
 * the exact same one hr-backend's own JWT validation would see as the issuer</strong> — using
 * {@code keycloak:8080} from the container produces tokens whose {@code iss} claim is
 * {@code http://keycloak:8080/realms/cubeworks-hr}, which is exactly what the engine's new OIDC
 * entry's {@code issuer} is configured to (verified live, 2026-10-16 — a real token obtained this
 * way was accepted by {@code GET /repository/list} and by {@code POST /cases}, not just
 * theoretically compatible). Getting this mismatched (e.g. using the host-published
 * {@code localhost:8180} address from inside the container) would still validate on hr-backend's
 * own side but be rejected by the engine as a different issuer. Overridden via
 * {@code CUBEWORKS_ENGINE_KEYCLOAK_TOKEN_URL}/{@code CUBEWORKS_ENGINE_BASE_URL} in
 * {@code docker-compose.yml} for the containerized case; the field defaults below are the
 * host-mode dev values.</p>
 *
 * <h2>Case team (simplification, documented, not hidden)</h2>
 * <p>{@code 03-case-file.md} §3 describes per-employee Employee/Manager case-team members and
 * tenant-role HR/ITAdmin members. There is no real mechanism to map an arbitrary employee's
 * Keycloak identity to a CaseFabric-registered engine user (the engine only knows the one
 * {@code hr-backend} service-account identity — see
 * {@code casefabric/getting-started/src/bootstrap/world.tenant.conf}), and since Spring is the
 * only caller and already enforces 100% of real authorization (R-API-2) before ever reaching
 * here, this is a bookkeeping/auditability concern inside the engine, not a functional gate.
 * Every case's team therefore has one real member — the {@code hr-backend} service account,
 * holding every case role directly — plus the two tenant-role entries the architecture already
 * calls for, which currently resolve to that same one registered user.</p>
 */
@Component
@Profile("!test")
public class RealCaseEngineClient implements CaseEngineClient {

    private static final Logger log = LoggerFactory.getLogger(RealCaseEngineClient.class);

    /** All case roles {@code employeecase.caseteam} declares — see the class doc's case-team note. */
    private static final List<String> ALL_CASE_ROLES =
            List.of("Employee", "Manager", "HR", "ITAdmin", "Peer", "ProjectManager");

    private final RestClient restClient;
    private final Clock clock;

    private final String engineBaseUrl;
    private final String caseDefinition;
    private final String keycloakTokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String serviceAccountUserId;

    /** Guards {@link #cachedToken} — token refresh is rare (every few minutes) and must not race. */
    private final Object tokenLock = new Object();
    private CachedToken cachedToken;

    private record CachedToken(String accessToken, Instant expiresAt) {}

    /**
     * Constructor injection (R-CODE-1). Field defaults match host-mode dev
     * ({@code ./mvnw spring-boot:run}); the containerized {@code hr-backend} service overrides
     * {@code engineBaseUrl} and {@code keycloakTokenUrl} — see the class doc.
     *
     * @param restClientBuilder     Spring-managed builder (no fixed base URL — this client calls
     *                              two different hosts, Keycloak and the engine)
     * @param clock                 application-wide clock bean, used for token-expiry bookkeeping
     * @param engineBaseUrl         CaseFabric engine REST base URL
     * @param caseDefinition        the deployed case definition file name ({@code employeecase.xml})
     * @param keycloakTokenUrl      Keycloak token endpoint for the client-credentials grant
     * @param clientId              hr-backend's own Keycloak client id
     * @param clientSecret          hr-backend's own Keycloak client secret (dev-only value;
     *                              overridden via env var in any real deployment)
     * @param serviceAccountUserId  the client-credentials token's {@code sub} claim — the engine
     *                              tenant user id {@code hr-backend} always acts as
     */
    public RealCaseEngineClient(final RestClient.Builder restClientBuilder,
                                 final Clock clock,
                                 @Value("${cubeworks.engine.base-url:http://localhost:2027}") final String engineBaseUrl,
                                 @Value("${cubeworks.engine.definition:employeecase.xml}") final String caseDefinition,
                                 @Value("${cubeworks.engine.keycloak-token-url:http://localhost:8180/realms/cubeworks-hr/protocol/openid-connect/token}") final String keycloakTokenUrl,
                                 @Value("${cubeworks.engine.client-id:hr-backend}") final String clientId,
                                 @Value("${cubeworks.engine.client-secret:hr-backend-dev-secret-change-for-prod}") final String clientSecret,
                                 @Value("${cubeworks.engine.service-account-user-id:b9d462ff-4185-44be-83fa-7beae8250602}") final String serviceAccountUserId) {
        this.restClient = restClientBuilder.build();
        this.clock = clock;
        this.engineBaseUrl = engineBaseUrl;
        this.caseDefinition = caseDefinition;
        this.keycloakTokenUrl = keycloakTokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.serviceAccountUserId = serviceAccountUserId;
    }

    @Override
    public void startCase(final String caseId, final Map<String, Object> initialCaseFile) {
        final Map<String, Object> caseTeam = Map.of(
                "users", List.of(Map.of(
                        "userId", serviceAccountUserId,
                        "caseRoles", ALL_CASE_ROLES,
                        "isOwner", true)),
                "groups", List.of(),
                // HR/ITAdmin tenant roles, per 03-case-file.md §3. Both currently resolve to the
                // same one registered tenant user as the direct "users" entry above (see class
                // doc) — kept anyway so the case team's shape matches the documented design, not
                // just a convenient shortcut.
                "tenantRoles", List.of(
                        Map.of("tenantRole", "HR", "caseRoles", List.of("HR")),
                        Map.of("tenantRole", "ITAdmin", "caseRoles", List.of("ITAdmin"))));

        final Map<String, Object> body = Map.of(
                "definition", caseDefinition,
                "caseInstanceId", caseId,
                "inputs", initialCaseFile,
                "caseTeam", caseTeam);

        restClient.post()
                .uri(engineBaseUrl + "/cases")
                .header("Authorization", "Bearer " + getAccessToken())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        log.info("Started engine case caseId={} definition={}", caseId, caseDefinition);
    }

    @Override
    public void updateCaseFile(final String caseId, final String item, final Map<String, Object> fields) {
        restClient.put()
                .uri(engineBaseUrl + "/cases/{caseId}/casefile/update/{item}", caseId, item)
                .header("Authorization", "Bearer " + getAccessToken())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(fields)
                .retrieve()
                .toBodilessEntity();

        log.info("Updated engine case file caseId={} item={} fields={}", caseId, item, fields.keySet());
    }

    @Override
    public void completeTask(final String engineTaskId, final Map<String, Object> output) {
        restClient.post()
                .uri(engineBaseUrl + "/tasks/{taskId}/complete", engineTaskId)
                .header("Authorization", "Bearer " + getAccessToken())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(output)
                .retrieve()
                .toBodilessEntity();

        log.info("Completed engine task engineTaskId={} output={}", engineTaskId, output);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void startPlanItem(final String caseId, final String planItemName) {
        final Map<String, Object> caseDetail = restClient.get()
                .uri(engineBaseUrl + "/cases/{caseId}", caseId)
                .header("Authorization", "Bearer " + getAccessToken())
                .retrieve()
                .body(Map.class);
        final List<Map<String, Object>> planItems =
                (List<Map<String, Object>>) caseDetail.get("planitems");
        final String planItemId = planItems.stream()
                .filter(p -> planItemName.equals(p.get("name")))
                .map(p -> (String) p.get("id"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No plan item named '" + planItemName + "' on case " + caseId));

        restClient.post()
                .uri(engineBaseUrl + "/cases/{caseId}/planitems/{planItemId}/start", caseId, planItemId)
                .header("Authorization", "Bearer " + getAccessToken())
                .retrieve()
                .toBodilessEntity();

        log.info("Started plan item '{}' (id={}) on caseId={}", planItemName, planItemId, caseId);
    }

    /** {@code taskState} values that mean a task is no longer open, per the engine's own vocabulary. */
    private static final List<String> CLOSED_TASK_STATES = List.of("Completed", "Terminated");

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> findOpenTasks(final String caseId) {
        final List<Map<String, Object>> tasks = restClient.get()
                .uri(engineBaseUrl + "/tasks/case/{caseId}", caseId)
                .header("Authorization", "Bearer " + getAccessToken())
                .retrieve()
                .body(List.class);
        if (tasks == null) {
            return List.of();
        }
        // GET /tasks/case/{caseId} returns the case's FULL task history, not just open tasks
        // (confirmed live, 2026-10-16 — a completed "Onboarding checklist" task was still
        // returned with taskState="Completed"). This method's contract promises open tasks
        // only, so the filter belongs here, not in every caller.
        return tasks.stream()
                .filter(t -> !CLOSED_TASK_STATES.contains(t.get("taskState")))
                .toList();
    }

    // ── Token acquisition ────────────────────────────────────────────────────

    /**
     * Returns a valid client-credentials bearer token, refreshing it if the cached one is
     * missing or within 10 seconds of expiry. Keycloak client-credentials tokens default to a
     * 300-second lifetime (confirmed live, 2026-10-16), so this refreshes roughly every 5
     * minutes under steady traffic, never per-request.
     *
     * @return a valid bearer token (without the "Bearer " prefix)
     */
    private String getAccessToken() {
        synchronized (tokenLock) {
            final Instant now = Instant.now(clock);
            if (cachedToken != null && cachedToken.expiresAt().isAfter(now.plusSeconds(10))) {
                return cachedToken.accessToken();
            }

            @SuppressWarnings("unchecked")
            final Map<String, Object> response = restClient.post()
                    .uri(keycloakTokenUrl)
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret)
                    .retrieve()
                    .body(Map.class);

            final String accessToken = (String) response.get("access_token");
            final int expiresInSeconds = ((Number) response.get("expires_in")).intValue();
            cachedToken = new CachedToken(accessToken, now.plusSeconds(expiresInSeconds));
            return accessToken;
        }
    }
}
