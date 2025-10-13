package io.github.pojotools.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import io.github.pojotools.flat2pojo.core.util.PathOps;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages primitive list arrays with eager ArrayNode creation during processing. Single
 * Responsibility: Direct primitive array creation and value appending.
 *
 * <p>Memory-efficient design: Creates ArrayNodes immediately and appends values directly,
 * eliminating intermediate storage and tree traversal overhead.
 */
public final class PrimitiveListManager {
  private final ObjectMapper objectMapper;
  private final String separator;
  private final Map<String, MappingConfig.PrimitiveListRule> rulesCache;
  private final Map<String, ArrayNode> arrayNodeCache;

  public PrimitiveListManager(final ObjectMapper objectMapper, final MappingConfig config) {
    this.objectMapper = objectMapper;
    this.separator = config.separator();
    this.rulesCache = buildRulesCache(config);
    this.arrayNodeCache = new HashMap<>();
  }

  public boolean isPrimitiveListPath(final String path) {
    return rulesCache.containsKey(path);
  }

  public void addValue(
    final String scope, final Path path, final JsonNode value, final ObjectNode targetRoot) {
    if (isNullValue(value)) {
      return;
    }

    final ArrayNode arrayNode = getOrCreateArrayNode(scope, path, targetRoot);
    appendValue(arrayNode, value, path.absolutePath());
  }

  private static Map<String, MappingConfig.PrimitiveListRule> buildRulesCache(
      final MappingConfig config) {
    final Map<String, MappingConfig.PrimitiveListRule> cache = new HashMap<>();
    for (final MappingConfig.PrimitiveListRule rule : config.primitiveLists()) {
      cache.put(rule.path(), rule);
    }
    return cache;
  }

  private boolean isNullValue(final JsonNode value) {
    return value == null || value.isNull();
  }

  private ArrayNode getOrCreateArrayNode(
      final String scope, final Path path, final ObjectNode targetRoot) {
    final String cacheKey = buildCacheKey(scope, path.absolutePath());
    return arrayNodeCache.computeIfAbsent(
        cacheKey, k -> createAndAttachArray(targetRoot, path.relativePath()));
  }

  private String buildCacheKey(final String scope, final String path) {
    return scope + "|" + path;
  }

  private ArrayNode createAndAttachArray(final ObjectNode targetRoot, final String path) {
    final ObjectNode parent =
        PathOps.traverseAndEnsurePath(targetRoot, path, separator, PathOps::ensureObject);
    final String fieldName = PathOps.getFinalSegment(path, separator);
    final ArrayNode array = objectMapper.createArrayNode();
    parent.set(fieldName, array);
    return array;
  }

  private void appendValue(
      final ArrayNode arrayNode, final JsonNode value, final String absolutePath) {
    final MappingConfig.PrimitiveListRule rule = rulesCache.get(absolutePath);
    if (rule.dedup() && containsValue(arrayNode, value)) {
      return;
    }
    addValueToArray(arrayNode, value, rule);
  }

  private void addValueToArray(
      final ArrayNode arrayNode, final JsonNode value, final MappingConfig.PrimitiveListRule rule) {
    switch (rule.orderDirection()) {
      case insertion -> arrayNode.add(value);
      case asc -> insertSorted(arrayNode, value, new JsonNodeComparator());
      case desc -> insertSorted(arrayNode, value, new JsonNodeComparator().reversed());
      default -> throw new IllegalStateException("Unknown order direction: " + rule.orderDirection());
    }
  }

  private void insertSorted(
      final ArrayNode arrayNode, final JsonNode value, final Comparator<JsonNode> comparator) {
    final int position = findInsertPosition(arrayNode, value, comparator);
    arrayNode.insert(position, value);
  }

  private int findInsertPosition(
      final ArrayNode arrayNode, final JsonNode value, final Comparator<JsonNode> comparator) {
    int left = 0;
    int right = arrayNode.size();
    while (left < right) {
      final int mid = (left + right) / 2;
      if (comparator.compare(arrayNode.get(mid), value) < 0) {
        left = mid + 1;
      } else {
        right = mid;
      }
    }
    return left;
  }

  private boolean containsValue(final ArrayNode arrayNode, final JsonNode value) {
    for (final JsonNode element : arrayNode) {
      if (element.equals(value)) {
        return true;
      }
    }
    return false;
  }
}
