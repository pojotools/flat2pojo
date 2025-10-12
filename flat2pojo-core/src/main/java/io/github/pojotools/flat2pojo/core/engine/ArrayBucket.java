package io.github.pojotools.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

public final class ArrayBucket {
  private final Map<CompositeKey, ObjectNode> byKey = new LinkedHashMap<>();
  private final List<ObjectNode> insertionOrder = new ArrayList<>();
  private List<ObjectNode> cachedSortedElements;
  private List<Comparator<ObjectNode>> lastComparators;

  /**
   * Upserts an element into the bucket.
   *
   * <p>Production behavior: Always called with an empty candidate node. When a key exists, it returns
   * the existing node unchanged. When the key is new, inserts and returns the candidate.
   *
   * <p>This implements first-write-wins semantics: the first insert establishes the node,
   * later upserts with the same key return the existing node without modification.
   *
   * @param key composite key identifying the element
   * @param candidate node to insert (production: always empty; will be populated by callers)
   * @return the node in the bucket (either newly inserted or pre-existing)
   */
  public ObjectNode upsert(CompositeKey key, ObjectNode candidate) {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(candidate, "candidate must not be null");

    return existsInBucket(key) ? byKey.get(key) : insertNew(key, candidate);
  }

  private boolean existsInBucket(CompositeKey key) {
    return byKey.containsKey(key);
  }

  private ObjectNode insertNew(CompositeKey key, ObjectNode candidate) {
    byKey.put(key, candidate);
    insertionOrder.add(candidate);
    invalidateCache();
    return candidate;
  }

  private void invalidateCache() {
    cachedSortedElements = null;
    lastComparators = null;
  }

  public List<ObjectNode> ordered(List<Comparator<ObjectNode>> comparators) {
    if (isCached(comparators)) {
      return cachedSortedElements;
    }
    List<ObjectNode> elements = sortElements(comparators);
    cacheResults(comparators, elements);
    return elements;
  }

  private boolean isCached(List<Comparator<ObjectNode>> comparators) {
    return cachedSortedElements != null && Objects.equals(lastComparators, comparators);
  }

  private List<ObjectNode> sortElements(List<Comparator<ObjectNode>> comparators) {
    List<ObjectNode> elements = new ArrayList<>(byKey.values());
    if (!comparators.isEmpty()) {
      elements.sort(buildCombinedComparator(comparators));
    }
    return elements;
  }

  private Comparator<ObjectNode> buildCombinedComparator(List<Comparator<ObjectNode>> comparators) {
    return comparators.stream().reduce(Comparator::thenComparing).orElseThrow();
  }

  private void cacheResults(List<Comparator<ObjectNode>> comparators, List<ObjectNode> elements) {
    cachedSortedElements = elements;
    lastComparators = new ArrayList<>(comparators);
  }

  public ArrayNode asArray(
      ObjectMapper objectMapper, List<Comparator<ObjectNode>> nodeComparators) {
    ArrayNode arrayNode = objectMapper.createArrayNode();
    ordered(nodeComparators).forEach(arrayNode::add);
    return arrayNode;
  }
}
