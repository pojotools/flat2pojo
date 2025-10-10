package io.github.pojotools.flat2pojo.core.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.flat2pojo.core.api.Flat2PojoException;

/**
 * Converts JSON tree structure to typed POJOs. Single Responsibility: JSON-to-POJO materialization
 * only.
 */
final class ResultMaterializer {
  private final ObjectMapper objectMapper;

  ResultMaterializer(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  <T> T materialize(final ObjectNode root, final Class<T> type) {
    try {
      if (JsonNode.class.isAssignableFrom(type)) {
        @SuppressWarnings("unchecked")
        final T cast = (T) root;
        return cast;
      } else {
        return objectMapper.treeToValue(root, type);
      }
    } catch (final Exception exception) {
      throw new Flat2PojoException("Failed to map result to " + type.getName(), exception);
    }
  }
}
