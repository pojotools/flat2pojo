package io.github.pojotools.flat2pojo.core.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups flat rows by root keys (single or composite).
 * Single Responsibility: Root key-based grouping logic only.
 *
 * <p>Implementation note: Uses LinkedHashMap to preserve insertion order of groups,
 * ensuring deterministic output when processing rows in sequence.
 */
final class RootKeyGrouper {
  private RootKeyGrouper() {}

  /**
   * Groups rows by root keys, preserving the order in which groups are first encountered.
   *
   * @param rows input rows to group
   * @param rootKeys keys to group by (single or composite)
   * @return map of key values to row lists, ordered by first appearance
   */
  static Map<Object, List<Map<String, ?>>> groupByRootKeys(
      final List<? extends Map<String, ?>> rows, final List<String> rootKeys) {
    return rootKeys.size() == 1
        ? groupBySingleKey(rows, rootKeys.getFirst())
        : groupByCompositeKey(rows, rootKeys);
  }

  private static Map<Object, List<Map<String, ?>>> groupBySingleKey(
      final List<? extends Map<String, ?>> rows, final String key) {
    final Map<Object, List<Map<String, ?>>> groups = new LinkedHashMap<>();
    for (final Map<String, ?> row : rows) {
      final Object keyValue = row.get(key);
      groups.computeIfAbsent(keyValue, k -> new ArrayList<>()).add(row);
    }
    return groups;
  }

  private static Map<Object, List<Map<String, ?>>> groupByCompositeKey(
      final List<? extends Map<String, ?>> rows, final List<String> rootKeys) {
    final Map<Object, List<Map<String, ?>>> groups = new LinkedHashMap<>();
    for (final Map<String, ?> row : rows) {
      final List<Object> compositeKey = buildCompositeKey(row, rootKeys);
      groups.computeIfAbsent(compositeKey, k -> new ArrayList<>()).add(row);
    }
    return groups;
  }

  private static List<Object> buildCompositeKey(final Map<String, ?> row, final List<String> rootKeys) {
    final List<Object> compositeKey = new ArrayList<>(rootKeys.size());
    for (final String rootKey : rootKeys) {
      compositeKey.add(row.get(rootKey));
    }
    return compositeKey;
  }
}
