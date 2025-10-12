package io.github.pojotools.flat2pojo.core.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import io.github.pojotools.flat2pojo.core.engine.PrimitiveAccumulator;
import io.github.pojotools.flat2pojo.core.util.ConflictContext;
import io.github.pojotools.flat2pojo.core.util.ConflictHandler;

/**
 * Writes values into list elements with conflict policy handling.
 * Delegates direct value writing to DirectValueWriter and accumulation to PrimitiveAccumulator.
 *
 * <p>Single Responsibility: Conflict-aware value writing for list elements.
 */
final class ListElementWriter {
  private final ProcessingContext context;
  private final PrimitiveAccumulator accumulator;

  ListElementWriter(final ProcessingContext context, final PrimitiveAccumulator accumulator) {
    this.context = context;
    this.accumulator = accumulator;
  }

  void writeWithConflictPolicy(
      final ObjectNode target,
      final String path,
      final JsonNode value,
      final MappingConfig.ConflictPolicy policy,
      final String absolutePath) {
    if (path.isEmpty()) {
      return;
    }

    if (accumulator.isAggregationPath(absolutePath)) {
      final String scope = buildScopeKey(target);
      // Store with a relative path for correct writing later
      accumulator.accumulate(scope, path, value);
    } else {
      final ObjectNode parent = context.pathResolver().traverseAndEnsurePath(target, path);
      final String lastSegment = context.pathResolver().getFinalSegment(path);
      final ConflictContext conflictContext =
          new ConflictContext(policy, absolutePath, context.config().reporter().orElse(null));

      ConflictHandler.writeScalarWithPolicy(parent, lastSegment, value, conflictContext);
    }
  }

  private String buildScopeKey(final ObjectNode target) {
    // Use target identity hash as a scope - each ObjectNode represents a unique scope
    return String.valueOf(System.identityHashCode(target));
  }
}
