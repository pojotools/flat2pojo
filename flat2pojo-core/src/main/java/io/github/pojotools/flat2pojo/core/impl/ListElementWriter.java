package io.github.pojotools.flat2pojo.core.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import io.github.pojotools.flat2pojo.core.engine.Path;
import io.github.pojotools.flat2pojo.core.engine.PrimitiveArrayManager;
import io.github.pojotools.flat2pojo.core.util.ConflictContext;
import io.github.pojotools.flat2pojo.core.util.ConflictHandler;

/**
 * Writes values into list elements with conflict policy handling.
 * Single Responsibility: Conflict-aware value writing for list elements.
 */
final class ListElementWriter {
  private final ProcessingContext context;
  private final PrimitiveArrayManager primitiveArrayManager;

  ListElementWriter(final ProcessingContext context, final PrimitiveArrayManager manager) {
    this.context = context;
    this.primitiveArrayManager = manager;
  }

  void writeWithConflictPolicy(
      final ObjectNode target,
      final Path path,
      final JsonNode value,
      final MappingConfig.ConflictPolicy policy) {
    if (path.relativePath().isEmpty()) {
      return;
    }

    if (primitiveArrayManager.isPrimitiveListPath(path.absolutePath())) {
      writeToPrimitiveList(target, path, value);
    } else {
      writeWithPolicy(target, path, value, policy);
    }
  }

  private void writeToPrimitiveList(
      final ObjectNode target,
      final Path path,
      final JsonNode value) {
    final String scope = buildScopeKey(target);
    primitiveArrayManager.addValue(scope, path, value, target);
  }

  private void writeWithPolicy(
      final ObjectNode target,
      final Path path,
      final JsonNode value,
      final MappingConfig.ConflictPolicy policy) {
    final ObjectNode parent = context.pathResolver().traverseAndEnsurePath(target, path.relativePath());
    final String lastSegment = context.pathResolver().getFinalSegment(path.relativePath());
    final ConflictContext conflictContext =
        new ConflictContext(policy, path.absolutePath(), context.config().reporter().orElse(null));
    ConflictHandler.writeScalarWithPolicy(parent, lastSegment, value, conflictContext);
  }

  private String buildScopeKey(final ObjectNode target) {
    return String.valueOf(System.identityHashCode(target));
  }
}
