package com.cubeworks.hr.idp;

/**
 * Port interface for the Keycloak admin operations hr-backend needs to give a new employee a
 * login account (added 2026-10-19, F01 "new-hire login" follow-up).
 *
 * <p>Two implementations: {@link RealKeycloakAdminClient} (the live Keycloak admin REST API,
 * {@code @Profile("!test")}) and {@link NoOpKeycloakAdminClient} ({@code @Profile("test")}),
 * which only logs — the same split used for {@code CaseEngineClient}, so tests never need a live
 * Keycloak.</p>
 */
public interface KeycloakAdminClient {

    /**
     * Creates a disabled-password Keycloak user and grants it one realm role.
     *
     * <p>The user is created with {@code UPDATE_PASSWORD} as a required action, so they must set a
     * password at first login even if the invitation e-mail is never opened. The username and the
     * e-mail are both the employee's private e-mail (F01 decision, 2026-10-19).</p>
     *
     * @param username  Keycloak username (the employee's private e-mail)
     * @param email     the e-mail the set-password mail is sent to (same private e-mail)
     * @param firstName employee first name
     * @param lastName  employee last name
     * @param realmRole the realm role matching the employee's {@code app_role}
     *                  (one of {@code HR}, {@code MANAGER}, {@code EMPLOYEE}, {@code IT_ADMIN})
     * @return the created Keycloak user id (the token {@code sub} the app stores in
     *         {@code employee.idp_subject})
     */
    String createUserWithRole(String username, String email, String firstName, String lastName,
                              String realmRole);

    /**
     * Asks Keycloak to e-mail the user an "update password" action link, valid for
     * {@code lifespanSeconds}. Keycloak sends the mail through its own configured SMTP relay
     * (the realm {@code smtpServer} points at the dev Mailcatcher), not through hr-backend's
     * {@code email_outbox}.
     *
     * @param userId          the Keycloak user id returned by {@link #createUserWithRole}
     * @param lifespanSeconds how long the link stays valid (from the {@code SET_PASSWORD_LINK_DAYS}
     *                        setting, converted to seconds)
     */
    void sendSetPasswordEmail(String userId, int lifespanSeconds);
}
