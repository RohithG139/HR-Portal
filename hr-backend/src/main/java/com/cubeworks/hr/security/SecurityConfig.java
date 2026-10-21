package com.cubeworks.hr.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security filter chain for the CubeWorks HR backend.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><strong>CSRF disabled:</strong> all API callers send a stateless Bearer token; there is no
 *       session cookie that an attacker could hijack. CSRF protection is meaningless — and
 *       actively harmful — in a stateless REST API (R-ARC-4, CLAUDE.md §2.6).</li>
 *   <li><strong>Stateless sessions:</strong> no server-side session is created; each request is
 *       authenticated independently via the JWT.</li>
 *   <li><strong>{@code /actuator/health} is open:</strong> Docker / Kubernetes health probes run
 *       without a token. All other actuator endpoints are either disabled or require a valid JWT
 *       (see {@code application.yml management.*} settings).</li>
 *   <li><strong>OpenAPI docs open in development:</strong> {@code /swagger-ui/**} and
 *       {@code /v3/api-docs/**} are accessible without a token so developers can browse the spec.
 *       Tighten these in a production profile if needed.</li>
 *   <li><strong>Role checks at service layer:</strong> fine-grained role and ownership checks
 *       (G-02, G-13) live in the service layer, not here, because they require application context
 *       (e.g. checking employee ownership, preventing self-review). This filter chain only ensures
 *       a valid token is present.</li>
 *   <li><strong>Keycloak role extraction:</strong> {@link KeycloakRoleConverter} maps
 *       {@code realm_access.roles} to Spring {@code ROLE_*} authorities so that
 *       {@code @PreAuthorize("hasRole('HR')")} works on service methods.</li>
 *   <li><strong>Method security enabled:</strong> {@code @EnableMethodSecurity} activates
 *       {@code @PreAuthorize} on service methods for role-based access control (G-02, R-API-2).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;

    /**
     * Constructor injection — no {@code @Autowired} field injection (R-CODE-1).
     *
     * @param corsConfigurationSource the CORS policy produced by {@link com.cubeworks.hr.config.CorsConfig}
     */
    public SecurityConfig(final CorsConfigurationSource corsConfigurationSource) {
        this.corsConfigurationSource = corsConfigurationSource;
    }

    /**
     * Assembles the main security filter chain.
     *
     * <p>The JWT resource server is backed by Keycloak; the issuer URI in
     * {@code application.yml} triggers OIDC discovery at startup so the JWKS is fetched once
     * and cached. In the test profile a stub {@code JwtDecoder} bean replaces this discovery
     * to avoid requiring a live Keycloak during CI.</p>
     *
     * @param http the {@link HttpSecurity} builder provided by Spring Security
     * @return the assembled and built {@link SecurityFilterChain}
     * @throws Exception if the filter chain cannot be built (Spring Security internal error)
     */
    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
        return http
                // CSRF disabled: stateless REST API with Bearer token — no session cookie to protect
                .csrf(AbstractHttpConfigurer::disable)
                // CORS policy defined in CorsConfig; applied before the JWT filter so OPTIONS preflight works
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // No HTTP session — every request must carry a valid JWT
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Health probe: must be reachable by Docker / Kubernetes without a token
                        .requestMatchers("/actuator/health").permitAll()
                        // OpenAPI: accessible without a token for developer convenience
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        // Engine-initiated callbacks (added 2026-10-21): the CaseFabric engine's own
                        // ProcessTasks have no Keycloak identity to present a JWT with. Protected
                        // instead by a shared-secret header checked inside EngineCallbackController
                        // itself (R-API-2's spirit — server-side check — just not JWT-based, since
                        // the caller isn't an hr-frontend user). Never reachable from hr-frontend.
                        .requestMatchers("/api/internal/engine-events/**").permitAll()
                        // Every other endpoint requires a valid Keycloak-issued JWT
                        .anyRequest().authenticated())
                // JWT resource server — Spring Security validates against the Keycloak JWKS.
                // The JwtAuthenticationConverter maps realm_access.roles to Spring ROLE_* authorities
                // so that @PreAuthorize("hasRole('HR')") expressions work on service methods.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    /**
     * Builds a {@link JwtAuthenticationConverter} that uses {@link KeycloakRoleConverter} to
     * extract realm roles from the JWT {@code realm_access.roles} claim.
     *
     * <p>Without this converter, Spring Security would not know about the HR application roles
     * ({@code HR}, {@code MANAGER}, {@code EMPLOYEE}, {@code IT_ADMIN}) and every
     * {@code @PreAuthorize("hasRole('HR')")} check would fail.</p>
     *
     * @return configured converter
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        final JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        // KeycloakRoleConverter reads realm_access.roles → ROLE_HR, ROLE_MANAGER, etc.
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return converter;
    }
}
