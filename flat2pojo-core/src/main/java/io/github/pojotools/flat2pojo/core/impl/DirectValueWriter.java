package io.github.pojotools.flat2pojo.core.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.flat2pojo.core.engine.PrimitiveAccumulator;

/**
 * Writes values directly to object nodes without conflict handling or policy checks.
 * Used for non-list paths that don't require special conflict resolution.
 *
 * <p>Single Responsibility: Direct value writing to JSON object nodes.
 */
final class DirectValueWriter {
  private final ProcessingContext context;
  private final PrimitiveAccumulator accumulator;

  DirectValueWriter(final ProcessingContext context, PrimitiveAccumulator accumulator) {
    this.context = context;
    this.accumulator = accumulator;
  }

  /**
   * Writes a value directly to the target node at the specified path.
   *
   * @param target the object node to write to
   * @param path the dot-separated path where the value should be written
   * @param value the JSON value to write
   */
  void writeDirectly(final ObjectNode target, final String path, final JsonNode value) {
    if (path.isEmpty()) {
      return;
    }

    if (accumulator.isAggregationPath(path)) {
      final String scope = buildScopeKey(target);
      // Store with a relative path for correct writing later
      accumulator.accumulate(scope, path, value);
    } else {
      final ObjectNode parent = context.pathResolver().traverseAndEnsurePath(target, path);
      final String lastSegment = context.pathResolver().getFinalSegment(path);
      parent.set(lastSegment, value);
    }
  }

  private String buildScopeKey(final ObjectNode target) {
    // Use target identity hash as a scope - each ObjectNode represents a unique scope
    return String.valueOf(System.identityHashCode(target));
  }
}
