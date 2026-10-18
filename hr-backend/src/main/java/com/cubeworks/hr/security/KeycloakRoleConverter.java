package com.cubeworks.hr.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts Keycloak realm roles from the JWT {@code realm_access.roles} claim into
 * Spring Security {@link GrantedAuthority} objects.
 *
 * <h2>Keycloak JWT structure</h2>
 * <p>Keycloak embeds realm-level roles in the {@code realm_access} claim as a list under the
 * {@code roles} key:</p>
 * <pre>{@code
 * "realm_access": {
 *   "roles": ["HR", "offline_access", "uma_authorization"]
 * }
 * }</pre>
 *
 * <h2>Mapping</h2>
 * <p>Each role string {@code R} becomes {@code ROLE_R} so Spring Security's
 * {@code hasRole("HR")} expressions work without prefixing. Only the four application
 * roles defined in the HR system are meaningful; other Keycloak default roles (e.g.
 * {@code offline_access}) are harmlessly included but never checked by any
 * {@code @PreAuthorize} expression.</p>
 *
 * <h2>Usage</h2>
 * <p>Wired into {@link SecurityConfig}'s JWT authentication converter so Spring Security
 * populates the authorities on every authenticated request.</p>
 */
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    /**
     * Extracts realm roles from the JWT and returns them as {@link GrantedAuthority} objects.
     *
     * @param jwt the decoded Keycloak JWT
     * @return a collection of granted authorities; empty if the claim is absent
     */
    @Override
    public Collection<GrantedAuthority> convert(final Jwt jwt) {
        // realm_access is a nested map; getClaimAsMap is safe for a JSON object claim.
        final Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !realmAccess.containsKey("roles")) {
            return Collections.emptyList();
        }
        @SuppressWarnings("unchecked")
        final List<String> roles = (List<String>) realmAccess.getOrDefault("roles", List.of());
        // Prefix each role with ROLE_ so Spring's hasRole("HR") works without explicit prefix.
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toUnmodifiableList());
    }
}
