package io.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import io.flat2pojo.core.config.MappingConfig;
import io.flat2pojo.core.util.PathOps;
import java.util.*;

public final class FlatTreeBuilder {
  private final ObjectMapper om;
  private final Map<String, MappingConfig.PrimitiveSplitRule> splitRulesCache;
  private final char separatorChar;
  private final boolean blanksAsNulls;

  public FlatTreeBuilder(final ObjectMapper om, final MappingConfig cfg) {
    this.om = om;
    this.separatorChar = cfg.separator().length() == 1 ? cfg.separator().charAt(0) : '/';
    this.blanksAsNulls = cfg.nullPolicy() != null && cfg.nullPolicy().blanksAsNulls();
    this.splitRulesCache = buildSplitRulesCache(cfg);
  }

  private static Map<String, MappingConfig.PrimitiveSplitRule> buildSplitRulesCache(
      final MappingConfig cfg) {
    final Map<String, MappingConfig.PrimitiveSplitRule> cache = new HashMap<>();
    for (final MappingConfig.PrimitiveSplitRule rule : cfg.primitives()) {
      cache.put(rule.path(), rule);
    }
    return cache;
  }

  public ObjectNode buildTreeForRow(final Map<String, ?> row) {
    final ObjectNode root = om.createObjectNode();

    for (final var e : row.entrySet()) {
      final String key = e.getKey();
      Object raw = e.getValue();

      // Optional blank→null conversion
      if (raw instanceof String s && blanksAsNulls && s.isBlank()) {
        raw = null;
      }

      // Use PathOps for consistent path traversal
      final ObjectNode parentNode = PathOps.traverseAndEnsurePath(root, key, separatorChar,
        this::ensureObject);
      final String lastSegment = PathOps.getFinalSegment(key, separatorChar);
      final JsonNode valueNode = createValueNode(key, raw);
      parentNode.set(lastSegment, valueNode);
    }

    return root;
  }

  private JsonNode createValueNode(final String key, final Object rawValue) {
    final MappingConfig.PrimitiveSplitRule splitRule = splitRulesCache.get(key);

    if (splitRule != null && rawValue instanceof String stringValue) {
      return createSplitArrayNode(stringValue, splitRule);
    } else {
      return createLeafNode(rawValue);
    }
  }

  private ArrayNode createSplitArrayNode(
      final String stringValue, final MappingConfig.PrimitiveSplitRule splitRule) {
    final String delimiter = splitRule.delimiter();
    final boolean shouldTrim = splitRule.trim();

    final ArrayNode arrayNode = om.createArrayNode();
    final String[] parts = stringValue.split(java.util.regex.Pattern.quote(delimiter), -1);

    for (final String part : parts) {
      final String processedValue = shouldTrim ? part.trim() : part;
      if (blanksAsNulls && processedValue.isEmpty()) {
        arrayNode.add(NullNode.getInstance());
      } else {
        arrayNode.add(TextNode.valueOf(processedValue));
      }
    }

    return arrayNode;
  }

  private JsonNode createLeafNode(final Object rawValue) {
    if (rawValue == null) {
      return NullNode.getInstance();
    }

    if (rawValue instanceof String stringValue) {
      if (blanksAsNulls && stringValue.trim().isEmpty()) {
        return NullNode.getInstance();
      }
      return TextNode.valueOf(stringValue);
    }

    // Optimize for common primitive types
    if (rawValue instanceof Integer intValue) {
      return IntNode.valueOf(intValue);
    }
    if (rawValue instanceof Long longValue) {
      return LongNode.valueOf(longValue);
    }
    if (rawValue instanceof Double doubleValue) {
      return DoubleNode.valueOf(doubleValue);
    }
    if (rawValue instanceof Boolean boolValue) {
      return BooleanNode.valueOf(boolValue);
    }

    return om.valueToTree(rawValue);
  }

  private ObjectNode ensureObject(final ObjectNode parent, final String fieldName) {
    final JsonNode existing = parent.get(fieldName);
    if (existing instanceof ObjectNode objectNode) {
      return objectNode;
    }
    final ObjectNode created = om.createObjectNode();
    parent.set(fieldName, created);
    return created;
  }
}
