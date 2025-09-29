package io.github.pojotools.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import io.github.pojotools.flat2pojo.core.util.PathOps;
import java.util.*;

/** Manages list/grouping state while building the JSON tree. */
public final class GroupingEngine {
  private final ObjectMapper om;
  private final String separator;
  private final char separatorChar;

  // Side-car state for arrays encountered during building
  private final IdentityHashMap<ArrayNode, ArrayBucket> buckets = new IdentityHashMap<>();
  private final IdentityHashMap<ArrayNode, List<Comparator<ObjectNode>>> comparators =
      new IdentityHashMap<>();

  // Cache for pre-built comparators by rule path
  private final Map<String, List<Comparator<ObjectNode>>> comparatorCache = new HashMap<>();

  public GroupingEngine(final ObjectMapper om, final MappingConfig cfg) {
    this.om = om;
    this.separator = cfg.separator();
    this.separatorChar = separator.length() == 1 ? separator.charAt(0) : '/';
    precomputeComparators(cfg);
  }

  private void precomputeComparators(final MappingConfig cfg) {
    for (final var rule : cfg.lists()) {
      final List<Comparator<ObjectNode>> ruleComparators = buildComparatorsForRule(rule);
      comparatorCache.put(rule.path(), ruleComparators);
    }
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
    // Use PathOps for consistent path traversal
    final ObjectNode parentNode =
        PathOps.traverseAndEnsurePath(
            base, relativeListPath, separatorChar, GroupingEngine::ensureObject);
    final String arrayField = PathOps.getFinalSegment(relativeListPath, separatorChar);
    final ArrayNode arr = parentNode.withArray(arrayField);

    // Per-array state
    final ArrayBucket bucket = buckets.computeIfAbsent(arr, k -> new ArrayBucket());
    comparators.computeIfAbsent(arr, k -> comparatorCache.get(rule.path()));

    // Composite key (keyPaths are ABSOLUTE)
    final List<Object> keyVals = new ArrayList<>(rule.keyPaths().size());
    for (final String keyPath : rule.keyPaths()) {
      final JsonNode v = rowValues.get(keyPath);
      if (v == null || v.isNull()) {
        // Skip list entry creation entirely if any keyPath is null/missing
        return null;
      }
      keyVals.add(v);
    }
    final CompositeKey key = new CompositeKey(keyVals);

    final ObjectNode candidate = om.createObjectNode();
    return bucket.upsert(key, candidate, rule.onConflict());
  }

  public void finalizeArrays(final ObjectNode node) {
    final Deque<JsonNode> stack = new ArrayDeque<>();
    stack.push(node);
    while (!stack.isEmpty()) {
      final JsonNode cur = stack.pop();
      if (cur instanceof ObjectNode on) {
        final Iterator<String> f = on.fieldNames();
        final List<String> names = new ArrayList<>();
        f.forEachRemaining(names::add);
        for (final String n : names) {
          final JsonNode v = on.get(n);
          if (v instanceof ObjectNode || v instanceof ArrayNode) {
            stack.push(v);
          }
        }
      } else if (cur instanceof ArrayNode an) {
        final ArrayBucket bucket = buckets.get(an);
        if (bucket != null) {
          final List<Comparator<ObjectNode>> comps = comparators.getOrDefault(an, List.of());
          an.removeAll();
          for (final ObjectNode e : bucket.ordered(comps)) {
            an.add(e);
          }
        }
        for (final JsonNode child : an) {
          stack.push(child);
        }
      }
    }
  }

  private List<Comparator<ObjectNode>> buildComparatorsForRule(final MappingConfig.ListRule rule) {
    final String rulePathPrefix = rule.path() + separator;
    final List<Comparator<ObjectNode>> comparatorList = new ArrayList<>();

    for (final var orderBy : rule.orderBy()) {
      final String orderPath = orderBy.path();
      final String relativePath =
          orderPath.startsWith(rulePathPrefix)
              ? orderPath.substring(rulePathPrefix.length())
              : orderPath;

      final boolean isAscending = orderBy.direction() == MappingConfig.Direction.asc;
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

      final boolean isANull = (valueA == null || valueA.isNull());
      final boolean isBNull = (valueB == null || valueB.isNull());

      if (isANull != isBNull) {
        return nullsFirst ? (isANull ? -1 : 1) : (isANull ? 1 : -1);
      }

      if (isANull && isBNull) {
        return 0;
      }

      final int comparison = compareJsonValues(valueA, valueB);
      return isAscending ? comparison : -comparison;
    };
  }

  private static int compareJsonValues(final JsonNode a, final JsonNode b) {
    if (a.isNumber() && b.isNumber()) {
      return Double.compare(a.asDouble(), b.asDouble());
    }
    return a.asText().compareTo(b.asText());
  }

  private JsonNode findValueAtPath(final ObjectNode base, final String relativePath) {
    if (relativePath.isEmpty()) {
      return base;
    }

    JsonNode currentNode = base;
    int start = 0;
    int sepIndex;

    while ((sepIndex = PathOps.nextSep(relativePath, start, separatorChar)) >= 0) {
      if (!(currentNode instanceof ObjectNode objectNode)) {
        return NullNode.getInstance();
      }

      final String segment = relativePath.substring(start, sepIndex);
      currentNode = objectNode.get(segment);
      if (currentNode == null) {
        return NullNode.getInstance();
      }
      start = sepIndex + 1;
    }

    if (start < relativePath.length()) {
      if (!(currentNode instanceof ObjectNode objectNode)) {
        return NullNode.getInstance();
      }
      final String lastSegment = relativePath.substring(start);
      currentNode = objectNode.get(lastSegment);
    }

    return currentNode == null ? NullNode.getInstance() : currentNode;
  }

  private static ObjectNode ensureObject(final ObjectNode parent, final String field) {
    final JsonNode n = parent.get(field);
    if (n instanceof ObjectNode existing) {
      return existing;
    }
    final ObjectNode created = parent.objectNode();
    parent.set(field, created);
    return created;
  }
}
