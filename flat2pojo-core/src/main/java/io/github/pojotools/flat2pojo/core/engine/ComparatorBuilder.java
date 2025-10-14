package io.github.pojotools.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and caches comparators for list ordering. Single Responsibility: Comparator construction
 * logic only.
 */
final class ComparatorBuilder {
  private final String separator;
  private final Map<String, List<Comparator<ObjectNode>>> comparatorCache = new HashMap<>();

  ComparatorBuilder(final String separator) {
    this.separator = separator;
  }

  void precomputeComparators(final MappingConfig config) {
    for (final var rule : config.lists()) {
      final List<Comparator<ObjectNode>> ruleComparators = buildComparators(rule);
      comparatorCache.put(rule.path(), ruleComparators);
    }
  }

  List<Comparator<ObjectNode>> getComparatorsForPath(final String path) {
    return comparatorCache.getOrDefault(path, List.of());
  }

  private List<Comparator<ObjectNode>> buildComparators(final MappingConfig.ListRule rule) {
    final List<Comparator<ObjectNode>> comparatorList = new ArrayList<>();

    for (final var orderBy : rule.orderBy()) {
      final String relativePath = orderBy.path();
      final boolean isAscending = orderBy.direction() == MappingConfig.OrderDirection.asc;
      final boolean nullsFirst = orderBy.nulls() == MappingConfig.Nulls.first;

      comparatorList.add(createFieldComparator(relativePath, isAscending, nullsFirst));
    }

    return comparatorList;
  }

  private Comparator<ObjectNode> createFieldComparator(
      final String relativePath, final boolean isAscending, final boolean nullsFirst) {
    return (nodeA, nodeB) -> {
      final JsonNode valueA = findValueAtPath(nodeA, relativePath);
      final JsonNode valueB = findValueAtPath(nodeB, relativePath);
      return compareWithNulls(valueA, valueB, isAscending, nullsFirst);
    };
  }

  private int compareWithNulls(
      final JsonNode valueA,
      final JsonNode valueB,
      final boolean isAscending,
      final boolean nullsFirst) {
    final boolean isANull = isNullOrMissing(valueA);
    final boolean isBNull = isNullOrMissing(valueB);

    if (isANull && isBNull) {
      return 0;
    }
    if (isANull) {
      return nullsFirst ? -1 : 1;
    }
    if (isBNull) {
      return nullsFirst ? 1 : -1;
    }
    return compareValues(valueA, valueB, isAscending);
  }

  private int compareValues(
      final JsonNode valueA, final JsonNode valueB, final boolean isAscending) {
    final int comparison = compareJsonValues(valueA, valueB);
    return isAscending ? comparison : -comparison;
  }

  private JsonNode findValueAtPath(final ObjectNode base, final String relativePath) {
    if (relativePath.isEmpty()) {
      return base;
    }
    return traversePath(base, relativePath);
  }

  private JsonNode traversePath(final ObjectNode base, final String path) {
    JsonNode current = base;
    int start = 0;
    while (start < path.length()) {
      current = navigateToNextSegment(current, path, start);
      if (isNullOrMissing(current)) {
        return NullNode.getInstance();
      }
      start = calculateNextStart(path, start);
    }
    return current;
  }

  private boolean isNullOrMissing(final JsonNode node) {
    return node == null || node.isNull();
  }

  private JsonNode navigateToNextSegment(
      final JsonNode current, final String path, final int start) {
    if (!(current instanceof ObjectNode objectNode)) {
      return null;
    }
    final String segment = extractSegment(path, start);
    return objectNode.get(segment);
  }

  private int calculateNextStart(final String path, final int start) {
    final int separatorIndex = path.indexOf(separator, start);
    return separatorIndex >= 0 ? separatorIndex + separator.length() : path.length();
  }

  private String extractSegment(final String path, final int start) {
    final int separatorIndex = path.indexOf(separator, start);
    return separatorIndex >= 0 ? path.substring(start, separatorIndex) : path.substring(start);
  }

  private static int compareJsonValues(final JsonNode a, final JsonNode b) {
    if (a.isNumber() && b.isNumber()) {
      return Double.compare(a.asDouble(), b.asDouble());
    }
    return a.asText().compareTo(b.asText());
  }
}
