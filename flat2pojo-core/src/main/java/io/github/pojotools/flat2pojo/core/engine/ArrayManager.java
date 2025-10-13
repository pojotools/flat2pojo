package io.github.pojotools.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages list array lifecycle with grouping and sorting.
 * Single Responsibility: Coordinates array operations.
 */
public final class ArrayManager {
  private final ObjectMapper objectMapper;
  private final ArrayNodeResolver arrayResolver;
  private final CompositeKeyExtractor keyExtractor;
  private final ComparatorBuilder comparatorBuilder;
  private final IdentityHashMap<ArrayNode, ArrayBucket> buckets;
  private final IdentityHashMap<ArrayNode, List<Comparator<ObjectNode>>> comparators;

  public ArrayManager(final ObjectMapper objectMapper, final MappingConfig config) {
    this.objectMapper = objectMapper;
    this.arrayResolver = new ArrayNodeResolver(config.separator());
    this.keyExtractor = new CompositeKeyExtractor(config.separator());
    this.comparatorBuilder = new ComparatorBuilder(config.separator());
    this.buckets = new IdentityHashMap<>();
    this.comparators = new IdentityHashMap<>();
    comparatorBuilder.precomputeComparators(config);
  }

  public ObjectNode upsertListElement(
      final ObjectNode base,
      final String relativeListPath,
      final Map<String, JsonNode> rowValues,
      final MappingConfig.ListRule rule) {
    final ArrayNode arrayNode = arrayResolver.resolveArrayNode(base, relativeListPath);
    final ArrayBucket bucket = ensureBucket(arrayNode, rule);
    final CompositeKey key = keyExtractor.extractFrom(rowValues, rule);
    return upsertElement(bucket, key);
  }

  public void finalizeArrays(final ObjectNode root) {
    final ArrayFinalizer finalizer = new ArrayFinalizer(buckets, comparators);
    finalizer.finalizeArrays(root);
    clearState();
  }

  private ArrayBucket ensureBucket(final ArrayNode arrayNode, final MappingConfig.ListRule rule) {
    buckets.computeIfAbsent(arrayNode, k -> new ArrayBucket());
    comparators.computeIfAbsent(arrayNode, k -> comparatorBuilder.getComparatorsForPath(rule.path()));
    return buckets.get(arrayNode);
  }

  private ObjectNode upsertElement(final ArrayBucket bucket, final CompositeKey key) {
    return key == null ? null : bucket.upsert(key, objectMapper.createObjectNode());
  }

  private void clearState() {
    buckets.clear();
    comparators.clear();
  }
}
