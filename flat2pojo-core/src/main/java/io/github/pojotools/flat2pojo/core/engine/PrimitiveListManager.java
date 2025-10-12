package io.github.pojotools.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import io.github.pojotools.flat2pojo.core.util.PathOps;
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
  private final Map<String, MappingConfig.PrimitiveAggregationRule> rulesCache;
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

    final ArrayNode arrayNode = getOrCreateArrayNode(scope, path.relativePath(), targetRoot);
    appendValue(arrayNode, value, path.absolutePath());
  }

  private static Map<String, MappingConfig.PrimitiveAggregationRule> buildRulesCache(
      final MappingConfig config) {
    final Map<String, MappingConfig.PrimitiveAggregationRule> cache = new HashMap<>();
    for (final MappingConfig.PrimitiveAggregationRule rule : config.primitiveAggregation()) {
      cache.put(rule.path(), rule);
    }
    return cache;
  }

  private boolean isNullValue(final JsonNode value) {
    return value == null || value.isNull();
  }

  private ArrayNode getOrCreateArrayNode(
      final String scope, final String path, final ObjectNode targetRoot) {
    final String cacheKey = buildCacheKey(scope, path);
    return arrayNodeCache.computeIfAbsent(cacheKey, k -> createAndAttachArray(targetRoot, path));
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
    if (shouldDeduplicate(absolutePath) && containsValue(arrayNode, value)) {
      return;
    }
    arrayNode.add(value);
  }

  private boolean shouldDeduplicate(final String path) {
    final MappingConfig.PrimitiveAggregationRule rule = rulesCache.get(path);
    return rule != null && rule.unique();
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
