package io.github.pojotools.flat2pojo.core.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.flat2pojo.core.engine.Path;
import io.github.pojotools.flat2pojo.core.engine.PrimitiveArrayManager;

/**
 * Writes values directly to object nodes without conflict handling or policy checks. Used for
 * non-list paths that don't require special conflict resolution.
 *
 * <p>Single Responsibility: Direct value writing to JSON object nodes.
 */
final class DirectValueWriter {
  private final ProcessingContext context;
  private final PrimitiveArrayManager primitiveArrayManager;

  DirectValueWriter(final ProcessingContext context, final PrimitiveArrayManager manager) {
    this.context = context;
    this.primitiveArrayManager = manager;
  }

  void writeDirectly(final ObjectNode target, final Path path, final JsonNode value) {
    if (path.relativePath().isEmpty()) {
      return;
    }

    if (primitiveArrayManager.isPrimitiveListPath(path.absolutePath())) {
      writeToPrimitiveList(target, path, value);
    } else {
      writeToScalarField(target, path.relativePath(), value);
    }
  }

  private void writeToPrimitiveList(
      final ObjectNode target, final Path path, final JsonNode value) {
    final String scope = buildScopeKey(target);
    primitiveArrayManager.addValue(scope, path, value, target);
  }

  private void writeToScalarField(
      final ObjectNode target, final String path, final JsonNode value) {
    final ObjectNode parent = context.pathResolver().traverseAndEnsurePath(target, path);
    final String lastSegment = context.pathResolver().getFinalSegment(path);
    parent.set(lastSegment, value);
  }

  private String buildScopeKey(final ObjectNode target) {
    return String.valueOf(System.identityHashCode(target));
  }
}
