package io.github.pojotools.flat2pojo.core.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import io.github.pojotools.flat2pojo.core.util.ConflictContext;
import io.github.pojotools.flat2pojo.core.util.ConflictHandler;

/**
 * Writes values into list elements with appropriate conflict handling. Single Responsibility: Value
 * writing logic only.
 */
final class ListElementWriter {
  private final ProcessingContext context;

  ListElementWriter(final ProcessingContext context) {
    this.context = context;
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

    final ObjectNode parent = context.pathResolver().traverseAndEnsurePath(target, path);
    final String lastSegment = context.pathResolver().getFinalSegment(path);
    final ConflictContext conflictContext =
        new ConflictContext(policy, absolutePath, context.config().reporter().orElse(null));

    ConflictHandler.writeScalarWithPolicy(parent, lastSegment, value, conflictContext);
  }

  void writeDirectly(final ObjectNode target, final String path, final JsonNode value) {
    if (path.isEmpty()) {
      return;
    }

    final ObjectNode parent = context.pathResolver().traverseAndEnsurePath(target, path);
    final String lastSegment = context.pathResolver().getFinalSegment(path);
    parent.set(lastSegment, value);
  }
}
