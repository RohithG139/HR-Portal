package com.cubeworks.hr;

import org.junit.jupiter.api.Test;

/**
 * Context-load integration test: verifies that the full Spring application context starts
 * successfully with a real PostgreSQL 16 database (via Testcontainers) and that Flyway
 * migrates cleanly from an empty schema.
 *
 * <h2>What this test proves</h2>
 * <ul>
 *   <li>All Spring beans wire up without circular-dependency or missing-bean errors.</li>
 *   <li>Flyway creates the {@code hr} schema and applies every migration script (currently
 *       {@code V1__init.sql} and {@code V2__seed_settings.sql} once it is added) against a
 *       brand-new empty database — this is the "clean migration" test required by §2.8 of
 *       {@code CLAUDE.md}. Any migration syntax error or constraint conflict surfaces here
 *       before the single test method is even invoked.</li>
 *   <li>Hibernate validates the entity-to-table mapping with {@code ddl-auto=validate}.</li>
 *   <li>The Spring Security filter chain builds without a live Keycloak instance.</li>
 * </ul>
 *
 * <h2>Infrastructure</h2>
 * <p>All Testcontainers and security-stub setup is inherited from
 * {@link AbstractIntegrationTest}. The container is started once per JVM fork and shared
 * with {@link MigrationConstraintTests} via Spring Test's context cache.</p>
 */
class HrApplicationTests extends AbstractIntegrationTest {

    /**
     * Verifies that the Spring application context loads without any errors.
     *
     * <p>If any bean definition, Flyway migration, Hibernate validation, or security
     * configuration causes a failure, this test reports the exact cause — which makes it
     * the fastest diagnostic for "the app refuses to start" problems.</p>
     *
     * <p>This test body is intentionally empty: if the context did not load, the
     * {@code @SpringBootTest} lifecycle throws before this method is called, and JUnit
     * reports the failure with the root-cause exception.</p>
     */
    @Test
    void contextLoads() {
        // The context-load itself (driven by @SpringBootTest) is the assertion.
        // Flyway migration success is implicit: if V1__init.sql (or any later script)
        // contains a syntax error or violates its own constraints during seeding, the
        // FlywayException propagates and Spring Test reports it here.
    }
}
