package io.github.pojotools.flat2pojo.examples;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pojotools.flat2pojo.core.api.Flat2Pojo;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import io.github.pojotools.flat2pojo.core.config.MappingConfigLoader;
import io.github.pojotools.flat2pojo.jackson.Flat2PojoFactory;
import io.github.pojotools.flat2pojo.jackson.JacksonAdapter;

import java.util.List;
import java.util.Objects;

final class TestSupport {

  // Utility class: no instances
  private TestSupport() {
    throw new AssertionError("No TestSupport instances");
  }

  static ObjectMapper createObjectMapper() {
    return JacksonAdapter.defaultObjectMapper();
  }

  static Flat2Pojo createConverter(ObjectMapper objectMapper) {
    Objects.requireNonNull(objectMapper, "objectMapper");
    return Flat2PojoFactory.defaultMapper(objectMapper);
  }

  static MappingConfig loadMappingConfigFromYaml(String yaml) {
    Objects.requireNonNull(yaml, "yaml");
    return MappingConfigLoader.fromYaml(yaml);
  }

  /** Explicitly throws if list is empty (use in tests when failure is desired). */
  static <T> T firstElementOrThrow(List<T> list) {
    Objects.requireNonNull(list, "list");
    return list.getFirst(); // Java 21
  }
}
