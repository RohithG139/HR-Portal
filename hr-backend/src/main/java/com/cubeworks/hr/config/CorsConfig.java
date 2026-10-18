package com.cubeworks.hr.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS configuration that restricts cross-origin access to the React development server only.
 *
 * <p>In development, Vite serves the frontend at {@code http://localhost:5173}. In production,
 * the frontend is served from the same origin as the backend (no CORS issue). Only this single
 * dev origin is whitelisted to prevent cross-site request forgery from any other origin
 * (supports R-API-2 defence-in-depth; the real enforcement is server-side JWT + role checks).</p>
 *
 * <p>This bean is consumed by {@link com.cubeworks.hr.security.SecurityConfig}
 * which passes it to Spring Security's CORS filter so that pre-flight OPTIONS requests are
 * handled before the JWT filter runs.</p>
 */
@Configuration
public class CorsConfig {

    /**
     * Produces the CORS policy applied to every request path.
     *
     * @return a source that allows the React dev server with credentials
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration config = new CorsConfiguration();

        // Only the Vite dev server is allowed as a cross-origin caller
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Allow all headers so the Authorization Bearer token passes through
        config.setAllowedHeaders(List.of("*"));
        // CORS "credentials" means browser-managed cookies/HTTP-auth/TLS certs sent via
        // `fetch(..., {credentials: 'include'})` — it does NOT govern a Bearer token in a
        // normal Authorization header, which is just a regular header already covered by
        // allowedHeaders above. This API is stateless (JWT-only, SecurityConfig sets
        // SessionCreationPolicy.STATELESS) and the frontend never sends `credentials:
        // 'include'`, so there is nothing for this flag to legitimately allow — leaving it
        // true only widens what a future, less careful origin/header change could expose.
        config.setAllowCredentials(false);
        // Expose no extra response headers — keep it tight
        config.setMaxAge(3600L);

        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
