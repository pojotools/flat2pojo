package io.github.pojotools.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.node.ArrayNode;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;

import java.util.Map;

/**
 * Finalizes primitive array nodes by applying sorting.
 * Single Responsibility: Primitive array finalization logic.
 */
final class PrimitiveArrayFinalizer {
  private final Map<String, PrimitiveArrayBucket> buckets;
  private final Map<String, ArrayNode> arrayNodes;
  private final Map<String, MappingConfig.OrderDirection> directions;

  PrimitiveArrayFinalizer(
      final Map<String, PrimitiveArrayBucket> buckets,
      final Map<String, ArrayNode> arrayNodes,
      final Map<String, MappingConfig.OrderDirection> directions) {
    this.buckets = buckets;
    this.arrayNodes = arrayNodes;
    this.directions = directions;
  }

  void finalizeAll() {
    for (final var entry : buckets.entrySet()) {
      finalizeOne(entry.getKey(), entry.getValue());
    }
  }

  private void finalizeOne(final String cacheKey, final PrimitiveArrayBucket bucket) {
    final ArrayNode array = arrayNodes.get(cacheKey);
    final MappingConfig.OrderDirection direction = getDirection(cacheKey);
    bucket.writeToArray(array, direction);
  }

  private MappingConfig.OrderDirection getDirection(final String cacheKey) {
    return directions.getOrDefault(cacheKey, MappingConfig.OrderDirection.insertion);
  }
}

