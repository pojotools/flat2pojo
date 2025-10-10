package io.github.pojotools.flat2pojo.examples;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class PojoJsonAssert {

  // Utility class: no instances
  private PojoJsonAssert() {
    throw new AssertionError("No PojoJsonAssert instances");
  }

  static void assertPojoJsonEquals(ObjectMapper om, String expectedJson, Object pojo) {
    try {
      JsonNode expected = om.readTree(expectedJson);
      JsonNode actual = om.valueToTree(pojo);
      assertThat(actual).isEqualTo(expected);
    } catch (Exception e) {
      throw new AssertionError("Failed to compare as JSON", e);
    }
  }
}
