package io.github.pojotools.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.flat2pojo.core.util.PathOps;

/**
 * Creates and attaches array nodes to the object tree.
 * Single Responsibility: Array node creation and attachment.
 */
final class PrimitiveArrayNodeFactory {
  private final ObjectMapper objectMapper;
  private final String separator;

  PrimitiveArrayNodeFactory(final ObjectMapper objectMapper, final String separator) {
    this.objectMapper = objectMapper;
    this.separator = separator;
  }

  ArrayNode createAndAttach(final ObjectNode targetRoot, final String path) {
    final ObjectNode parent = traverseToParent(targetRoot, path);
    final String fieldName = extractFieldName(path);
    return attachNewArray(parent, fieldName);
  }

  private ObjectNode traverseToParent(final ObjectNode targetRoot, final String path) {
    return PathOps.traverseAndEnsurePath(targetRoot, path, separator, PathOps::ensureObject);
  }

  private String extractFieldName(final String path) {
    return PathOps.getFinalSegment(path, separator);
  }

  private ArrayNode attachNewArray(final ObjectNode parent, final String fieldName) {
    final ArrayNode array = objectMapper.createArrayNode();
    parent.set(fieldName, array);
    return array;
  }
}

