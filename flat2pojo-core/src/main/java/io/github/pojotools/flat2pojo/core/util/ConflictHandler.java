package io.github.pojotools.flat2pojo.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;

/**
 * Utilities for handling field conflicts during flat-to-POJO conversion.
 *
 * <p>This class provides methods to resolve conflicts when multiple values are assigned to the same
 * field path according to different conflict policies.
 */
public final class ConflictHandler {
  private ConflictHandler() {}

  public static void writeScalarWithPolicy(
      final ObjectNode target,
      final String fieldName,
      final JsonNode incoming,
      final ConflictContext context) {
    final JsonNode existing = target.get(fieldName);

    if (existing == null || existing.isNull()) {
      target.set(fieldName, incoming);
      return;
    }

    final boolean shouldWrite = applyPolicy(existing, incoming, context);
    if (shouldWrite) {
      target.set(fieldName, incoming);
    }
  }

  private static boolean applyPolicy(
      final JsonNode existing,
      final JsonNode incoming,
      final ConflictContext context) {
    return switch (context.policy()) {
      case error -> {
        handleErrorPolicy(existing, incoming, context);
        yield true;
      }
      case firstWriteWins -> {
        handleFirstWriteWinsPolicy(existing, incoming, context);
        yield false;
      }
      case merge -> handleMergePolicy(existing, incoming, context);
      case lastWriteWins -> {
        handleLastWriteWinsPolicy(existing, incoming, context);
        yield true;
      }
    };
  }

  private static void handleErrorPolicy(
      final JsonNode existing,
      final JsonNode incoming,
      final ConflictContext context) {
    if (hasValueConflict(existing, incoming)) {
      final String message =
          "Conflict at '" + context.absolutePath() + "': existing=" + existing + ", incoming=" + incoming;
      context.reporterOptional().ifPresent(r -> r.warn(message));
      throw new RuntimeException(message);
    }
  }

  private static void handleFirstWriteWinsPolicy(
      final JsonNode existing,
      final JsonNode incoming,
      final ConflictContext context) {
    if (hasValueConflict(existing, incoming)) {
      context.reporterOptional().ifPresent(r -> r.warn(
          "Field conflict resolved using firstWriteWins policy at '"
              + context.absolutePath()
              + "': kept existing="
              + existing
              + ", ignored incoming="
              + incoming));
    }
  }

  private static boolean handleMergePolicy(
      final JsonNode existing,
      final JsonNode incoming,
      final ConflictContext context) {
    if (existing instanceof ObjectNode existingObject
        && incoming instanceof ObjectNode incomingObject) {
      deepMerge(existingObject, incomingObject);
      return false; // Don't write, already merged in place
    } else if (!existing.equals(incoming)) {
      context.reporterOptional().ifPresent(r -> r.warn(
          "Cannot merge non-object values at '"
              + context.absolutePath()
              + "': existing="
              + existing
              + ", incoming="
              + incoming
              + ". Using lastWriteWins."));
    }
    return true; // Write for non-objects (fallback to lastWriteWins)
  }

  private static void handleLastWriteWinsPolicy(
      final JsonNode existing,
      final JsonNode incoming,
      final ConflictContext context) {
    if (hasValueConflict(existing, incoming)) {
      context.reporterOptional().ifPresent(r -> r.warn(
          "Field conflict resolved using lastWriteWins policy at '"
              + context.absolutePath()
              + "': replaced existing="
              + existing
              + " with incoming="
              + incoming));
    }
  }

  private static boolean hasValueConflict(final JsonNode existing, final JsonNode incoming) {
    return existing.isValueNode() && incoming.isValueNode() && !existing.equals(incoming);
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
