package io.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.flat2pojo.core.config.MappingConfig;
import java.util.*;

public final class ArrayBucket {
  private final Map<CompositeKey, ObjectNode> byKey = new LinkedHashMap<>();
  private final List<ObjectNode> insertionOrder = new ArrayList<>();
  private List<ObjectNode> cachedSortedElements;
  private List<Comparator<ObjectNode>> lastComparators;

  public ObjectNode upsert(
      CompositeKey key, ObjectNode candidate, MappingConfig.ConflictPolicy policy) {
    ObjectNode existing = byKey.get(key);
    if (existing == null) {
      byKey.put(key, candidate);
      insertionOrder.add(candidate);
      invalidateCache();
      return candidate;
    }

    applyConflictPolicy(existing, candidate, policy);
    invalidateCache();
    return existing;
  }

  private void applyConflictPolicy(
      ObjectNode existing, ObjectNode candidate, MappingConfig.ConflictPolicy policy) {
    switch (policy) {
      case error -> {
        validateNoConflicts(existing, candidate);
        mergeNonConflictingFields(existing, candidate);
      }
      case lastWriteWins -> {
        overwriteAllFields(existing, candidate);
      }
      case firstWriteWins -> {
        // Keep existing values, do nothing
      }
      case merge -> {
        mergeOnlyAbsentFields(existing, candidate);
      }
    }
  }

  private void validateNoConflicts(ObjectNode existing, ObjectNode candidate) {
    Iterator<String> fieldNames = candidate.fieldNames();
    while (fieldNames.hasNext()) {
      String fieldName = fieldNames.next();
      JsonNode newValue = candidate.get(fieldName);
      JsonNode oldValue = existing.get(fieldName);
      if (oldValue != null && !oldValue.equals(newValue)) {
        throw new IllegalStateException(
            "Conflict on field '"
                + fieldName
                + "' with values ["
                + oldValue
                + ", "
                + newValue
                + "]");
      }
    }
  }

  private void mergeNonConflictingFields(ObjectNode existing, ObjectNode candidate) {
    Iterator<String> fieldNames = candidate.fieldNames();
    while (fieldNames.hasNext()) {
      String fieldName = fieldNames.next();
      if (!existing.has(fieldName)) {
        existing.set(fieldName, candidate.get(fieldName));
      }
    }
  }

  private void overwriteAllFields(ObjectNode existing, ObjectNode candidate) {
    Iterator<String> fieldNames = candidate.fieldNames();
    while (fieldNames.hasNext()) {
      String fieldName = fieldNames.next();
      existing.set(fieldName, candidate.get(fieldName));
    }
  }

  private void mergeOnlyAbsentFields(ObjectNode existing, ObjectNode candidate) {
    Iterator<String> fieldNames = candidate.fieldNames();
    while (fieldNames.hasNext()) {
      String fieldName = fieldNames.next();
      if (!existing.has(fieldName)) {
        existing.set(fieldName, candidate.get(fieldName));
      }
    }
  }

  public List<ObjectNode> ordered(List<Comparator<ObjectNode>> comparators) {
    if (cachedSortedElements != null && Objects.equals(lastComparators, comparators)) {
      return cachedSortedElements;
    }

    List<ObjectNode> elements = new ArrayList<>(byKey.values());
    if (!comparators.isEmpty()) {
      Comparator<ObjectNode> combinedComparator =
          comparators.stream().reduce(Comparator::thenComparing).orElse((a, b) -> 0);
      elements.sort(combinedComparator);
    }

    cachedSortedElements = elements;
    lastComparators = new ArrayList<>(comparators);
    return elements;
  }

  private void invalidateCache() {
    cachedSortedElements = null;
    lastComparators = null;
  }

  public ArrayNode asArray(
      com.fasterxml.jackson.databind.ObjectMapper om, List<Comparator<ObjectNode>> comps) {
    ArrayNode arr = om.createArrayNode();
    for (ObjectNode n : ordered(comps)) arr.add(n);
    return arr;
  }
}
