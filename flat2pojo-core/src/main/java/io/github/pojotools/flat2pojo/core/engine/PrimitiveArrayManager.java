package io.github.pojotools.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages primitive list arrays with optimized accumulation and finalization. Single
 * Responsibility: Coordinates primitive array lifecycle.
 *
 * <p>Performance optimization: Uses accumulation + sort-at-end pattern for sorted lists (asc/desc)
 * to achieve O(P + V log V) complexity instead of O(P × V) quadratic insertion. Insertion-order
 * lists still use immediate append for optimal memory efficiency.
 */
public final class PrimitiveArrayManager {
  private static final char CACHE_KEY_SEPARATOR = '|';

  private final PrimitiveArrayRuleCache ruleCache;
  private final PrimitiveArrayNodeFactory arrayFactory;
  private final Map<String, ArrayNode> arrayNodes;
  private final Map<String, PrimitiveArrayBucket> buckets;
  private final Map<String, MappingConfig.OrderDirection> directions;

  public PrimitiveArrayManager(final ObjectMapper objectMapper, final MappingConfig config) {
    this.ruleCache = new PrimitiveArrayRuleCache(config);
    this.arrayFactory = new PrimitiveArrayNodeFactory(objectMapper, config.separator());
    this.arrayNodes = new HashMap<>();
    this.buckets = new HashMap<>();
    this.directions = new HashMap<>();
  }

  private record AddContext(
      String cacheKey,
      Path path,
      JsonNode value,
      ObjectNode targetRoot,
      MappingConfig.PrimitiveListRule rule) {}

  public boolean isPrimitiveListPath(final String path) {
    return ruleCache.isPrimitiveListPath(path);
  }

  public void finalizePrimitiveArrays() {
    final PrimitiveArrayFinalizer finalizer =
        new PrimitiveArrayFinalizer(buckets, arrayNodes, directions);
    finalizer.finalizeAll();
    clearState();
  }

  public void addValue(
      final String scope, final Path path, final JsonNode value, final ObjectNode targetRoot) {
    if (isNullValue(value)) {
      return;
    }
    routeValue(scope, path, value, targetRoot);
  }

  private void routeValue(
      final String scope, final Path path, final JsonNode value, final ObjectNode targetRoot) {
    final String cacheKey = buildCacheKey(scope, path.absolutePath());
    final MappingConfig.PrimitiveListRule rule = ruleCache.getRuleFor(path.absolutePath());
    final AddContext context = new AddContext(cacheKey, path, value, targetRoot, rule);

    if (shouldInsertImmediately(rule)) {
      addImmediately(context);
    } else {
      accumulateForSorting(context);
    }
  }

  private boolean shouldInsertImmediately(final MappingConfig.PrimitiveListRule rule) {
    return rule.orderDirection() == MappingConfig.OrderDirection.insertion;
  }

  private void addImmediately(final AddContext context) {
    final ArrayNode array =
        getOrCreateArrayNode(context.cacheKey(), context.path(), context.targetRoot());
    final PrimitiveArrayBucket bucket = getOrCreateBucket(context.cacheKey(), context.rule());

    if (bucket.shouldAdd(context.value())) {
      array.add(context.value());
    }
  }

  private void accumulateForSorting(final AddContext context) {
    getOrCreateArrayNode(context.cacheKey(), context.path(), context.targetRoot());
    final PrimitiveArrayBucket bucket = getOrCreateBucket(context.cacheKey(), context.rule());
    bucket.add(context.value());
    directions.put(context.cacheKey(), context.rule().orderDirection());
  }

  private PrimitiveArrayBucket getOrCreateBucket(
      final String cacheKey, final MappingConfig.PrimitiveListRule rule) {
    return buckets.computeIfAbsent(cacheKey, k -> new PrimitiveArrayBucket(rule.dedup()));
  }

  private ArrayNode getOrCreateArrayNode(
      final String cacheKey, final Path path, final ObjectNode targetRoot) {
    return arrayNodes.computeIfAbsent(
        cacheKey, k -> arrayFactory.createAndAttach(targetRoot, path.relativePath()));
  }

  private String buildCacheKey(final String scope, final String path) {
    return scope + CACHE_KEY_SEPARATOR + path;
  }

  private void clearState() {
    arrayNodes.clear();
    buckets.clear();
    directions.clear();
  }

  private boolean isNullValue(final JsonNode value) {
    return value == null || value.isNull();
  }
}
