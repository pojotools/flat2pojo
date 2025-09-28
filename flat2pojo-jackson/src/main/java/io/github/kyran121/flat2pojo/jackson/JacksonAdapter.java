package io.github.kyran121.flat2pojo.jackson;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Jackson integration utilities for flat2pojo.
 *
 * <p>This class provides pre-configured Jackson ObjectMapper instances and utilities optimized for
 * flat2pojo's conversion process.
 */
public final class JacksonAdapter {

  /**
   * Creates a Jackson ObjectMapper with optimized settings for flat2pojo.
   *
   * <p>The returned mapper is configured with:
   *
   * <ul>
   *   <li>JavaTimeModule for java.time support
   *   <li>ISO-8601 date formatting (not timestamps)
   *   <li>Case-insensitive enum handling
   *   <li>Unknown property tolerance
   * </ul>
   *
   * @return a pre-configured ObjectMapper instance
   */
  public static ObjectMapper defaultObjectMapper() {
    return JsonMapper.builder()
        // Register module for java.time types
        .addModule(new JavaTimeModule())

        // ISO-8601 instead of timestamps for dates
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        // Allow case-insensitive enums
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)

        // Ignore unknown fields instead of failing
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();
  }
}
