package io.github.pojotools.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Accumulates primitive values with deduplication support.
 * Single Responsibility: Primitive value accumulation and deduplication.
 */
final class PrimitiveArrayBucket {
  private final List<JsonNode> values = new ArrayList<>();
  private final Set<JsonNode> dedupSet = new HashSet<>();
  private final boolean dedup;

  PrimitiveArrayBucket(final boolean dedup) {
    this.dedup = dedup;
  }

  void add(final JsonNode value) {
    if (shouldAdd(value)) {
      values.add(value);
    }
  }

  boolean shouldAdd(final JsonNode value) {
    return !dedup || dedupSet.add(value);
  }

  void writeToArray(final ArrayNode array, final MappingConfig.OrderDirection direction) {
    sortIfNeeded(direction);
    addAllToArray(array);
  }

  private void sortIfNeeded(final MappingConfig.OrderDirection direction) {
    if (direction == MappingConfig.OrderDirection.asc) {
      values.sort(new JsonNodeComparator());
    } else if (direction == MappingConfig.OrderDirection.desc) {
      values.sort(new JsonNodeComparator().reversed());
    }
  }

  private void addAllToArray(final ArrayNode array) {
    for (final JsonNode value : values) {
      array.add(value);
    }
  }
}
