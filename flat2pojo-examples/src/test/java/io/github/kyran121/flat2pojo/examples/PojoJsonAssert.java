package io.github.kyran121.flat2pojo.examples;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class PojoJsonAssert {
  private PojoJsonAssert() {}

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
