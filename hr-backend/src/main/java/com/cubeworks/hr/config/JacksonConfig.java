package com.cubeworks.hr.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson {@link ObjectMapper} configuration.
 *
 * <h2>Date serialisation</h2>
 * <p>All {@code LocalDate} values are serialised as ISO strings ({@code "yyyy-MM-dd"}) rather
 * than timestamp longs (R-API-1). {@code OffsetDateTime} values are serialised as ISO-8601 UTC
 * strings. Both require {@link JavaTimeModule} and disabling
 * {@link SerializationFeature#WRITE_DATES_AS_TIMESTAMPS}.</p>
 */
@Configuration
public class JacksonConfig {

    /**
     * Configures the application-wide {@link ObjectMapper} with Java time support and
     * ISO date serialisation.
     *
     * @return configured ObjectMapper
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        // Register JSR-310 module so LocalDate / OffsetDateTime serialise as strings.
        mapper.registerModule(new JavaTimeModule());
        // Dates as ISO strings, not epoch millis (R-API-1).
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Ignore unknown JSON fields so that request bodies containing fields the DTO
        // does not declare (e.g. "version" in UpdateProfileRequest, or fields silently
        // ignored by WS-03 in UpdateMyProfileRequest) do not cause 500 parse errors.
        // A new ObjectMapper() has FAIL_ON_UNKNOWN_PROPERTIES = true by default in Jackson 2;
        // Spring Boot's autoconfiguration disables it, but since we create a custom @Primary
        // bean we must set this explicitly.
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}
