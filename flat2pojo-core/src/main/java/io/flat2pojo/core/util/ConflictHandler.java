package io.flat2pojo.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.flat2pojo.core.config.MappingConfig.ConflictPolicy;
import java.util.Iterator;

/**
 * Utilities for handling field conflicts during flat-to-POJO conversion.
 *
 * <p>This class provides methods to resolve conflicts when multiple values
 * are assigned to the same field path according to different conflict policies.
 */
public final class ConflictHandler {
  private ConflictHandler() {}

  public static void writeScalarWithPolicy(
      final ObjectNode target,
      final String fieldName,
      final JsonNode incoming,
      final ConflictPolicy policy,
      final String absolutePath) {
    final JsonNode existing = target.get(fieldName);

    if (existing == null || existing.isNull()) {
      target.set(fieldName, incoming);
      return;
    }

    switch (policy) {
      case error -> {
        if (existing.isValueNode()
            && incoming.isValueNode()
            && !existing.equals(incoming)) {
          throw new RuntimeException(
              "Conflict at '" + absolutePath + "': existing=" + existing + ", incoming=" + incoming);
        }
      }
      case firstWriteWins -> {
        return;
      }
      case merge -> {
        if (existing instanceof ObjectNode existingObject
            && incoming instanceof ObjectNode incomingObject) {
          deepMerge(existingObject, incomingObject);
          return;
        }
      }
      case lastWriteWins -> {
        // Fall through to overwrite
      }
    }

    target.set(fieldName, incoming);
  }

  public static void deepMerge(final ObjectNode target, final ObjectNode source) {
    final Iterator<String> fieldNames = source.fieldNames();
    while (fieldNames.hasNext()) {
      final String fieldName = fieldNames.next();
      final JsonNode sourceValue = source.get(fieldName);
      final JsonNode targetValue = target.get(fieldName);

      if (targetValue instanceof ObjectNode targetObject
          && sourceValue instanceof ObjectNode sourceObject) {
        deepMerge(targetObject, sourceObject);
      } else {
        target.set(fieldName, sourceValue);
      }
    }
  }
}