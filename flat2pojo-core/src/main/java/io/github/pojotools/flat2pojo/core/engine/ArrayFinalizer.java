package io.github.pojotools.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Finalizes array nodes by applying sorting and deduplication. Single Responsibility: Array
 * finalization logic only.
 */
final class ArrayFinalizer {
  private final IdentityHashMap<ArrayNode, ArrayBucket> buckets;
  private final IdentityHashMap<ArrayNode, List<Comparator<ObjectNode>>> comparators;

  ArrayFinalizer(
      final IdentityHashMap<ArrayNode, ArrayBucket> buckets,
      final IdentityHashMap<ArrayNode, List<Comparator<ObjectNode>>> comparators) {
    this.buckets = buckets;
    this.comparators = comparators;
  }

  void finalizeArrays(final ObjectNode root) {
    final Deque<JsonNode> stack = new ArrayDeque<>();
    stack.push(root);
    while (!stack.isEmpty()) {
      final JsonNode current = stack.pop();
      if (current instanceof ObjectNode objectNode) {
        processObjectNode(objectNode, stack);
      } else if (current instanceof ArrayNode arrayNode) {
        finalizeArrayNode(arrayNode, stack);
      }
    }
  }

  private void processObjectNode(final ObjectNode objectNode, final Deque<JsonNode> stack) {
    final Iterator<String> fieldIterator = objectNode.fieldNames();
    final List<String> fieldNames = new ArrayList<>();
    fieldIterator.forEachRemaining(fieldNames::add);
    for (final String fieldName : fieldNames) {
      final JsonNode value = objectNode.get(fieldName);
      if (value instanceof ObjectNode || value instanceof ArrayNode) {
        stack.push(value);
      }
    }
  }

  private void finalizeArrayNode(final ArrayNode arrayNode, final Deque<JsonNode> stack) {
    applyBucketOrdering(arrayNode);
    pushChildrenToStack(arrayNode, stack);
  }

  private void applyBucketOrdering(final ArrayNode arrayNode) {
    final ArrayBucket bucket = buckets.get(arrayNode);
    if (bucket == null) {
      return;
    }
    final List<Comparator<ObjectNode>> nodeComparators =
        comparators.getOrDefault(arrayNode, List.of());
    arrayNode.removeAll();
    for (final ObjectNode element : bucket.ordered(nodeComparators)) {
      arrayNode.add(element);
    }
  }

  private void pushChildrenToStack(final ArrayNode arrayNode, final Deque<JsonNode> stack) {
    for (final JsonNode child : arrayNode) {
      stack.push(child);
    }
  }
}
