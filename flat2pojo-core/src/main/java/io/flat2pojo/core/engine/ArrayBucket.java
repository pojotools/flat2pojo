package io.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.flat2pojo.core.config.MappingConfig;
import java.util.*;

public final class ArrayBucket {
  private final Map<CompositeKey, ObjectNode> byKey = new LinkedHashMap<>();
  private final List<ObjectNode> insertionOrder = new ArrayList<>();

  public ObjectNode upsert(CompositeKey key, ObjectNode candidate,
                           MappingConfig.ConflictPolicy policy) {
    ObjectNode existing = byKey.get(key);
    if (existing == null) {
      byKey.put(key, candidate);
      insertionOrder.add(candidate);
      return candidate;
    }
    // Merge/resolve conflicts
    switch (policy) {
      case error -> {
        // Compare field-by-field and error if different values
        Iterator<String> fn = candidate.fieldNames();
        while (fn.hasNext()) {
          String f = fn.next();
          JsonNode vNew = candidate.get(f);
          JsonNode vOld = existing.get(f);
          if (vOld != null && !vOld.equals(vNew)) {
            throw new IllegalStateException("Conflict on field '"+f+"' with values ["+vOld+", "+vNew+"]");
          }
          if (vOld == null) existing.set(f, vNew);
        }
      }
      case lastWriteWins -> {
        // overwrite candidate fields
        Iterator<String> fn = candidate.fieldNames();
        while (fn.hasNext()) {
          String f = fn.next();
          existing.set(f, candidate.get(f));
        }
      }
      case firstWriteWins -> {
        // do nothing
      }
      case merge -> {
        // set fields only if absent
        Iterator<String> fn = candidate.fieldNames();
        while (fn.hasNext()) {
          String f = fn.next();
          if (!existing.has(f)) existing.set(f, candidate.get(f));
        }
      }
    }
    return existing;
  }

  public List<ObjectNode> ordered(List<Comparator<ObjectNode>> comparators) {
    List<ObjectNode> list = new ArrayList<>(byKey.values());
    if (!comparators.isEmpty()) {
      list.sort(comparators.stream().reduce(Comparator::thenComparing).orElse((a,b)->0));
    }
    return list;
  }

  public ArrayNode asArray(com.fasterxml.jackson.databind.ObjectMapper om, List<Comparator<ObjectNode>> comps) {
    ArrayNode arr = om.createArrayNode();
    for (ObjectNode n : ordered(comps)) arr.add(n);
    return arr;
  }
}
