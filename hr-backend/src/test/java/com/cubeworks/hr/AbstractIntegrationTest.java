package com.cubeworks.hr;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Abstract base class for all Spring Boot integration tests that require a live PostgreSQL
 * database (via Testcontainers).
 *
 * <h2>Why a shared base class</h2>
 * <p>Extracting the Testcontainers bootstrap and security stub into this class means:</p>
 * <ul>
 *   <li>One container is started for the entire Maven Surefire fork (the static initializer
 *       runs once when the JVM loads this class — the singleton pattern). Spring Test's
 *       context cache then sees the same JDBC URL for all subclasses and reuses the same
 *       application context, so Flyway runs exactly once.</li>
 *   <li>The {@link JwtDecoder} stub is registered via {@link MockBean}, which Spring Boot
 *       explicitly supports on abstract superclasses — this is more reliable than a
 *       {@code @TestConfiguration} inner class in the base class, which Spring Boot 3.4's
 *       context bootstrapper does not auto-detect from parent classes.</li>
 * </ul>
 *
 * <h2>Testcontainers singleton pattern rationale</h2>
 * <p>The standard JUnit 5 {@code @Container} annotation manages lifecycle <em>per test
 * class</em>: it stops the container after each class's {@code @AfterAll}. When multiple
 * test classes extend the same abstract base class, the second class would call
 * {@code start()} again on the same (now-stopped) container, receiving a different host
 * port. The cached Spring context still holds the original JDBC URL → connection refused.</p>
 * <p>The singleton pattern avoids this by starting the container <em>once</em> in a static
 * initializer. Testcontainers' Ryuk sidecar stops the Docker container when the JVM exits.
 * No {@code @Testcontainers} or {@code @Container} annotations are needed here.</p>
 *
 * <h2>Security stub rationale</h2>
 * <p>The production {@code application.yml} sets {@code issuer-uri} to the Keycloak discovery
 * URL. Without intervention, Spring Boot's {@code OAuth2ResourceServerJwtConfiguration}
 * would attempt OIDC discovery at context startup and fail because no Keycloak is running in
 * CI. Two things prevent this:</p>
 * <ol>
 *   <li>{@code @TestPropertySource} blanks {@code issuer-uri} so the
 *       {@code IssuerUriCondition} evaluates to false and the auto-configured decoder is
 *       not created.</li>
 *   <li>{@code @MockBean JwtDecoder} registers a Mockito mock that satisfies
 *       {@link com.cubeworks.hr.security.SecurityConfig#securityFilterChain}'s requirement
 *       for a {@code JwtDecoder} bean. No actual JWT decoding occurs in context-load or
 *       constraint tests.</li>
 * </ol>
 *
 * <h2>Engine client stub (2026-10-16, Wave 1)</h2>
 * <p>{@code @ActiveProfiles("test")} activates {@code NoOpCaseEngineClient} (the only
 * {@code @Profile("test")} {@link com.cubeworks.hr.engine.CaseEngineClient} bean) instead of
 * {@code RealCaseEngineClient} (the only {@code @Profile("!test")} one) — there is no live
 * CaseFabric engine reachable during a test run, and the two beans' profile conditions are
 * mutually exclusive so Spring never sees an ambiguous pair of candidates either way.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        /*
         * Blanking out issuer-uri prevents Spring Boot's OAuth2ResourceServerJwtConfiguration
         * from attempting OIDC discovery at context startup, which would fail without a live
         * Keycloak. The @MockBean below supplies the JwtDecoder bean the filter chain needs.
         */
        "spring.security.oauth2.resourceserver.jwt.issuer-uri="
})
abstract class AbstractIntegrationTest {

    /**
     * Shared PostgreSQL 16 container, started once for the entire Maven Surefire JVM fork.
     *
     * <p>Using a static initializer (not {@code @Container}) is the Testcontainers
     * "singleton pattern". The JVM class-loader guarantee means {@code POSTGRES.start()} is
     * called exactly once no matter how many subclasses run in the same fork. The container
     * is stopped by Testcontainers' Ryuk sidecar when the JVM exits.</p>
     *
     * <p>The database name, user and password match {@code application.yml} so that Flyway
     * and Hibernate are exercised with realistic credentials.</p>
     */
    static final PostgreSQLContainer<?> POSTGRES;

    static {
        // Singleton pattern: start the container once and keep it alive for the whole JVM
        // fork. This ensures that all subclasses share the same JDBC URL, which allows
        // Spring Test's context cache to reuse the same ApplicationContext across test
        // classes (Flyway runs exactly once, startup time is minimised).
        POSTGRES = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("hr_app")
                .withUsername("postgres")
                .withPassword("postgres");
        POSTGRES.start();
    }

    /**
     * Wires the Spring DataSource to the Testcontainers-assigned JDBC URL before the
     * application context is created.
     *
     * <p>This must be a {@code static} method so that Spring Test can invoke it before
     * constructing any beans. The dynamic URL is identical for all subclasses (same
     * container), which is what allows the context cache to reuse the same context.</p>
     *
     * @param registry the dynamic property registry provided by Spring Test
     */
    @DynamicPropertySource
    static void postgresProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /**
     * Mockito mock registered as a Spring bean.
     *
     * <p>Satisfies the {@code JwtDecoder} dependency required by
     * {@link com.cubeworks.hr.security.SecurityConfig#securityFilterChain} when the security
     * filter chain is built. No JWT is decoded in context-load or DB-constraint tests so the
     * default Mockito stub behaviour (return {@code null}) is harmless here.</p>
     *
     * <p>{@link MockBean} on an abstract superclass is explicitly supported by Spring Boot and
     * is inherited by all subclasses (unlike {@code @TestConfiguration} inner classes which
     * Spring Boot 3.4's {@code SpringBootTestContextBootstrapper} only auto-detects on the
     * concrete test class, not on parent classes).</p>
     */
    @MockBean
    JwtDecoder jwtDecoder;
}
