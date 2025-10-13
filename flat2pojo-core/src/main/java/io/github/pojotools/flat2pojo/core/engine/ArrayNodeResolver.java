package io.github.pojotools.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.flat2pojo.core.util.PathOps;

/**
 * Resolves and creates array nodes within the object tree.
 * Single Responsibility: Array node resolution and path traversal.
 */
final class ArrayNodeResolver {
  private final String separator;

  ArrayNodeResolver(final String separator) {
    this.separator = separator;
  }

  ArrayNode resolveArrayNode(final ObjectNode base, final String relativeListPath) {
    final ObjectNode parentNode = traverseToParent(base, relativeListPath);
    final String arrayField = extractArrayFieldName(relativeListPath);
    return parentNode.withArray(arrayField);
  }

  private ObjectNode traverseToParent(final ObjectNode base, final String relativeListPath) {
    return PathOps.traverseAndEnsurePath(base, relativeListPath, separator, PathOps::ensureObject);
  }

  private String extractArrayFieldName(final String relativeListPath) {
    return PathOps.getFinalSegment(relativeListPath, separator);
  }
}
