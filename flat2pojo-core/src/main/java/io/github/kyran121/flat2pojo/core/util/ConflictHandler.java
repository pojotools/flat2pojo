package io.github.kyran121.flat2pojo.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.kyran121.flat2pojo.core.config.MappingConfig.ConflictPolicy;
import io.github.kyran121.flat2pojo.spi.Reporter;
import java.util.Iterator;
import java.util.Optional;

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
      final String absolutePath,
      final Optional<Reporter> reporter) {
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
          final String message = "Conflict at '" + absolutePath + "': existing=" + existing + ", incoming=" + incoming;
          reporter.ifPresent(r -> r.warn(message));
          throw new RuntimeException(message);
        }
      }
      case firstWriteWins -> {
        if (existing.isValueNode()
            && incoming.isValueNode()
            && !existing.equals(incoming)) {
          reporter.ifPresent(r -> r.warn("Field conflict resolved using firstWriteWins policy at '" + absolutePath + "': kept existing=" + existing + ", ignored incoming=" + incoming));
        }
        return;
      }
      case merge -> {
        if (existing instanceof ObjectNode existingObject
            && incoming instanceof ObjectNode incomingObject) {
          deepMerge(existingObject, incomingObject);
          return;
        } else if (!existing.equals(incoming)) {
          reporter.ifPresent(r -> r.warn("Cannot merge non-object values at '" + absolutePath + "': existing=" + existing + ", incoming=" + incoming + ". Using lastWriteWins."));
        }
      }
      case lastWriteWins -> {
        if (existing.isValueNode()
            && incoming.isValueNode()
            && !existing.equals(incoming)) {
          reporter.ifPresent(r -> r.warn("Field conflict resolved using lastWriteWins policy at '" + absolutePath + "': replaced existing=" + existing + " with incoming=" + incoming));
        }
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