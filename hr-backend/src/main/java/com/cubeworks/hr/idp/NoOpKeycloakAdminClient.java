package com.cubeworks.hr.idp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Test-profile stub for {@link KeycloakAdminClient}: logs what it would do and returns a random
 * id, so tests never need a live Keycloak (mirrors {@code NoOpCaseEngineClient}).
 */
@Component
@Profile("test")
public class NoOpKeycloakAdminClient implements KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(NoOpKeycloakAdminClient.class);

    @Override
    public String createUserWithRole(final String username, final String email,
                                     final String firstName, final String lastName,
                                     final String realmRole) {
        final String fakeId = UUID.randomUUID().toString();
        log.info("[NoOpKeycloakAdminClient] createUserWithRole username={} role={} -> id={}",
                username, realmRole, fakeId);
        return fakeId;
    }

    @Override
    public void sendSetPasswordEmail(final String userId, final int lifespanSeconds) {
        log.info("[NoOpKeycloakAdminClient] sendSetPasswordEmail userId={} lifespanSeconds={}",
                userId, lifespanSeconds);
    }
}
