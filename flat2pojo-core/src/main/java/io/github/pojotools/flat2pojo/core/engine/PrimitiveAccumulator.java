package io.github.pojotools.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import io.github.pojotools.flat2pojo.core.util.PathOps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

/**
 * Accumulates primitive values across multiple rows for later array materialization. Handles both
 * root-level and list-scoped aggregation.
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class PrimitiveAccumulator {
  ObjectMapper objectMapper;
  String separator;
  Map<String, MappingConfig.PrimitiveAggregationRule> aggregationRulesCache;

  // Scoped accumulators: key = scope identifier, value = path -> values
  Map<String, Map<String, Collection<JsonNode>>> scopedAccumulators = new HashMap<>();

  public PrimitiveAccumulator(final ObjectMapper objectMapper, final MappingConfig config) {
    this.objectMapper = objectMapper;
    this.separator = config.separator();
    this.aggregationRulesCache = buildAggregationRulesCache(config);
  }

  private static Map<String, MappingConfig.PrimitiveAggregationRule> buildAggregationRulesCache(
      final MappingConfig config) {
    final Map<String, MappingConfig.PrimitiveAggregationRule> cache = new HashMap<>();
    for (final MappingConfig.PrimitiveAggregationRule rule : config.primitiveAggregation()) {
      cache.put(rule.path(), rule);
    }
    return cache;
  }

  public boolean isAggregationPath(final String path) {
    return aggregationRulesCache.containsKey(path);
  }

  public void accumulate(final String scope, final String path, final JsonNode value) {
    if (value == null || value.isNull()) {
      return;
    }

    final MappingConfig.PrimitiveAggregationRule rule = aggregationRulesCache.get(path);
    final Collection<JsonNode> collection = scopedAccumulators
        .computeIfAbsent(scope, k -> new HashMap<>())
        .computeIfAbsent(path, k -> createCollection(rule));

    collection.add(value);
  }

  private Collection<JsonNode> createCollection(final MappingConfig.PrimitiveAggregationRule rule) {
    return (rule != null && rule.unique()) ? new LinkedHashSet<>() : new ArrayList<>();
  }

  public void writeAllAccumulatedArrays(final ObjectNode root) {
    // Build a map of ObjectNode -> accumulated paths
    // by traversing the tree and matching scopes
    final Map<ObjectNode, Map<String, Collection<JsonNode>>> nodeAccumulators =
        buildNodeAccumulatorMap(root);

    // Write arrays to each node
    for (final var entry : nodeAccumulators.entrySet()) {
      final ObjectNode node = entry.getKey();
      final Map<String, Collection<JsonNode>> pathMap = entry.getValue();
      for (final var pathEntry : pathMap.entrySet()) {
        writeArray(node, pathEntry.getKey(), pathEntry.getValue());
      }
    }

    // Clean up after writing
    scopedAccumulators.clear();
  }

  private Map<ObjectNode, Map<String, Collection<JsonNode>>> buildNodeAccumulatorMap(
      final ObjectNode root) {
    final Map<ObjectNode, Map<String, Collection<JsonNode>>> result = new java.util.IdentityHashMap<>();
    final java.util.Deque<ObjectNode> stack = new java.util.ArrayDeque<>();
    stack.push(root);

    while (!stack.isEmpty()) {
      final ObjectNode current = stack.pop();
      final String scope = String.valueOf(System.identityHashCode(current));
      final Map<String, Collection<JsonNode>> accumulators = scopedAccumulators.get(scope);

      if (accumulators != null && !accumulators.isEmpty()) {
        result.put(current, accumulators);
      }

      // Push child ObjectNodes to stack
      final java.util.Iterator<JsonNode> elements = current.elements();
      while (elements.hasNext()) {
        final JsonNode child = elements.next();
        if (child instanceof ObjectNode childObject) {
          stack.push(childObject);
        } else if (child instanceof ArrayNode arrayNode) {
          // Push ObjectNode children of arrays
          for (final JsonNode arrayElement : arrayNode) {
            if (arrayElement instanceof ObjectNode arrayObject) {
              stack.push(arrayObject);
            }
          }
        }
      }
    }

    return result;
  }

  private void writeArray(
      final ObjectNode root, final String path, final Collection<JsonNode> accumulatedValues) {
    if (accumulatedValues.isEmpty()) {
      return;
    }

    final ObjectNode parent =
        PathOps.traverseAndEnsurePath(root, path, separator, PathOps::ensureObject);
    final String fieldName = PathOps.getFinalSegment(path, separator);
    final ArrayNode arrayNode = objectMapper.createArrayNode();
    accumulatedValues.forEach(arrayNode::add);
    parent.set(fieldName, arrayNode);
  }
}
