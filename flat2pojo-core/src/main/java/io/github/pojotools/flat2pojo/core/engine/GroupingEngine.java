package io.github.pojotools.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import io.github.pojotools.flat2pojo.core.util.PathOps;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Manages list/grouping state while building the JSON tree. */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class GroupingEngine {
  ObjectMapper objectMapper;
  String separator;
  ComparatorBuilder comparatorBuilder;

  // Side-car state for arrays encountered during building
  IdentityHashMap<ArrayNode, ArrayBucket> buckets = new IdentityHashMap<>();
  IdentityHashMap<ArrayNode, List<Comparator<ObjectNode>>> comparators = new IdentityHashMap<>();

  public GroupingEngine(final ObjectMapper objectMapper, final MappingConfig config) {
    this.objectMapper = objectMapper;
    this.separator = config.separator();
    this.comparatorBuilder = new ComparatorBuilder(config.separator());
    comparatorBuilder.precomputeComparators(config);
  }

  /**
   * Upsert an element inside a list located by a RELATIVE path from the given base object. Example:
   * base=definitionElement, relativeListPath="tracker/tasks"
   */
  public ObjectNode upsertListElementRelative(
      final ObjectNode base,
      final String relativeListPath,
      final Map<String, JsonNode> rowValues,
      final MappingConfig.ListRule rule) {
    final ArrayNode arrayNode = resolveArrayNode(base, relativeListPath);
    final ArrayBucket bucket = ensureBucketState(arrayNode, rule);
    final CompositeKey key = extractCompositeKey(rowValues, rule);
    return key == null ? null : bucket.upsert(key, objectMapper.createObjectNode());
  }

  private ArrayNode resolveArrayNode(final ObjectNode base, final String relativeListPath) {
    final ObjectNode parentNode = PathOps.traverseAndEnsurePath(
        base, relativeListPath, separator, PathOps::ensureObject);
    final String arrayField = PathOps.getFinalSegment(relativeListPath, separator);
    return parentNode.withArray(arrayField);
  }

  private ArrayBucket ensureBucketState(final ArrayNode arrayNode, final MappingConfig.ListRule rule) {
    buckets.computeIfAbsent(arrayNode, k -> new ArrayBucket());
    comparators.computeIfAbsent(arrayNode, k -> comparatorBuilder.getComparatorsForPath(rule.path()));
    return buckets.get(arrayNode);
  }

  private CompositeKey extractCompositeKey(final Map<String, JsonNode> rowValues, final MappingConfig.ListRule rule) {
    final List<Object> keyValues = collectKeyValues(rowValues, rule);
    return keyValues == null ? null : new CompositeKey(keyValues);
  }

  @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
  private List<Object> collectKeyValues(final Map<String, JsonNode> rowValues, final MappingConfig.ListRule rule) {
    final List<Object> keyValues = new ArrayList<>(rule.keyPaths().size());
    final String absolutePrefix = rule.path() + separator;
    for (final String relativeKeyPath : rule.keyPaths()) {
      final JsonNode value = rowValues.get(absolutePrefix + relativeKeyPath);
      if (value == null || value.isNull()) {
        return null; // Signals missing key path - intentional null return
      }
      keyValues.add(value);
    }
    return keyValues;
  }

  public void finalizeArrays(final ObjectNode node) {
    final ArrayFinalizer finalizer = new ArrayFinalizer(buckets, comparators);
    finalizer.finalizeArrays(node);
    clearBucketState();
  }

  private void clearBucketState() {
    buckets.clear();
    comparators.clear();
  }
}
