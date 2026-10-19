package com.cubeworks.hr.idp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Live implementation of {@link KeycloakAdminClient}, calling Keycloak's admin REST API so a new
 * employee gets a login account and a set-password e-mail (added 2026-10-19, F01 "new-hire login").
 *
 * <h2>Authentication</h2>
 * <p>Uses the same {@code hr-backend} client-credentials client already used for the engine. Its
 * service account is granted the {@code realm-management} roles {@code manage-users} and
 * {@code view-users} in {@code infra/keycloak/cubeworks-hr-realm.json}, which is what lets these
 * admin calls succeed.</p>
 *
 * <h2>Host vs container addressing</h2>
 * <p>{@code admin-base-url} and {@code token-url} both default to the host-published Keycloak
 * ({@code localhost:8180}); the containerised hr-backend overrides them to the Docker service name
 * ({@code keycloak:8080}) via {@code CUBEWORKS_KEYCLOAK_ADMIN_BASE_URL} /
 * {@code CUBEWORKS_KEYCLOAK_TOKEN_URL} — same pattern as the engine client.</p>
 */
@Component
@Profile("!test")
public class RealKeycloakAdminClient implements KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(RealKeycloakAdminClient.class);

    private final RestClient restClient;
    private final Clock clock;

    private final String adminBaseUrl;
    private final String realm;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;

    private final Object tokenLock = new Object();
    private CachedToken cachedToken;

    private record CachedToken(String accessToken, Instant expiresAt) {}

    public RealKeycloakAdminClient(
            final RestClient.Builder restClientBuilder,
            final Clock clock,
            @Value("${cubeworks.keycloak.admin-base-url:http://localhost:8180}") final String adminBaseUrl,
            @Value("${cubeworks.keycloak.realm:cubeworks-hr}") final String realm,
            @Value("${cubeworks.keycloak.token-url:http://localhost:8180/realms/cubeworks-hr/protocol/openid-connect/token}") final String tokenUrl,
            @Value("${cubeworks.keycloak.client-id:hr-backend}") final String clientId,
            @Value("${cubeworks.keycloak.client-secret:hr-backend-dev-secret-change-for-prod}") final String clientSecret) {
        this.restClient = restClientBuilder.build();
        this.clock = clock;
        this.adminBaseUrl = adminBaseUrl;
        this.realm = realm;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String createUserWithRole(final String username, final String email,
                                     final String firstName, final String lastName,
                                     final String realmRole) {
        final String usersUri = adminBaseUrl + "/admin/realms/" + realm + "/users";

        // 1. Create the user. Keycloak returns 201 with the new user's URL in the Location header.
        final Map<String, Object> userBody = Map.of(
                "username", username,
                "email", email,
                "firstName", firstName == null ? "" : firstName,
                "lastName", lastName == null ? "" : lastName,
                "enabled", true,
                "emailVerified", false,
                "requiredActions", List.of("UPDATE_PASSWORD"));

        final var created = restClient.post()
                .uri(usersUri)
                .header("Authorization", "Bearer " + getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(userBody)
                .retrieve()
                .toBodilessEntity();

        final var location = created.getHeaders().getLocation();
        if (location == null) {
            throw new IllegalStateException(
                    "Keycloak did not return a Location header for the created user " + username);
        }
        final String path = location.getPath();
        final String userId = path.substring(path.lastIndexOf('/') + 1);

        // 2. Look up the realm role representation (needs both id and name to assign it).
        final Map<String, Object> role = restClient.get()
                .uri(adminBaseUrl + "/admin/realms/" + realm + "/roles/" + realmRole)
                .header("Authorization", "Bearer " + getAccessToken())
                .retrieve()
                .body(Map.class);

        // 3. Grant the realm role to the new user.
        restClient.post()
                .uri(adminBaseUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm")
                .header("Authorization", "Bearer " + getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(Map.of("id", role.get("id"), "name", role.get("name"))))
                .retrieve()
                .toBodilessEntity();

        log.info("Created Keycloak user username={} role={} id={}", username, realmRole, userId);
        return userId;
    }

    @Override
    public void sendSetPasswordEmail(final String userId, final int lifespanSeconds) {
        // PUT execute-actions-email with the UPDATE_PASSWORD action. Keycloak e-mails the link
        // via its own SMTP (realm smtpServer -> Mailcatcher in dev); nothing goes through the
        // app's email_outbox.
        restClient.put()
                .uri(adminBaseUrl + "/admin/realms/" + realm + "/users/" + userId
                        + "/execute-actions-email?lifespan=" + lifespanSeconds)
                .header("Authorization", "Bearer " + getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of("UPDATE_PASSWORD"))
                .retrieve()
                .toBodilessEntity();

        log.info("Sent set-password e-mail for Keycloak userId={} lifespanSeconds={}",
                userId, lifespanSeconds);
    }

    private String getAccessToken() {
        synchronized (tokenLock) {
            final Instant now = Instant.now(clock);
            if (cachedToken != null && cachedToken.expiresAt().isAfter(now.plusSeconds(10))) {
                return cachedToken.accessToken();
            }
            @SuppressWarnings("unchecked")
            final Map<String, Object> response = restClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=client_credentials&client_id=" + clientId
                            + "&client_secret=" + clientSecret)
                    .retrieve()
                    .body(Map.class);
            final String accessToken = (String) response.get("access_token");
            final int expiresInSeconds = ((Number) response.get("expires_in")).intValue();
            cachedToken = new CachedToken(accessToken, now.plusSeconds(expiresInSeconds));
            return accessToken;
        }
    }
}
