package io.github.pojotools.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class ValueTransformer {
  ObjectMapper objectMapper;
  Map<String, MappingConfig.PrimitiveSplitRule> splitRulesCache;
  boolean blanksAsNulls;

  public ValueTransformer(final ObjectMapper objectMapper, final MappingConfig config) {
    this.objectMapper = objectMapper;
    this.blanksAsNulls = config.nullPolicy() != null && config.nullPolicy().blanksAsNulls();
    this.splitRulesCache = buildSplitRulesCache(config);
  }

  private static Map<String, MappingConfig.PrimitiveSplitRule> buildSplitRulesCache(
      final MappingConfig config) {
    final Map<String, MappingConfig.PrimitiveSplitRule> cache = new HashMap<>();
    for (final MappingConfig.PrimitiveSplitRule rule : config.primitives()) {
      cache.put(rule.path(), rule);
    }
    return cache;
  }

  /**
   * Transforms flat row values directly to JsonNode map without building intermediate tree. More
   * efficient than build-then-flatten approach for list processing.
   */
  public Map<String, JsonNode> transformRowValuesToJsonNodes(final Map<String, ?> row) {
    final Map<String, JsonNode> result = new LinkedHashMap<>(row.size());
    for (final var entry : row.entrySet()) {
      transformEntry(entry, result);
    }
    return result;
  }

  private void transformEntry(
      final Map.Entry<String, ?> entry, final Map<String, JsonNode> result) {
    final String key = entry.getKey();
    final Object normalized = normalizeBlankValue(entry.getValue());
    final JsonNode valueNode = createValueNode(key, normalized);
    result.put(key, valueNode);
  }

  private Object normalizeBlankValue(final Object rawValue) {
    if (rawValue instanceof String stringValue && blanksAsNulls && stringValue.isBlank()) {
      return null;
    }
    return rawValue;
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
    final String[] parts =
        stringValue.split(java.util.regex.Pattern.quote(splitRule.delimiter()), -1);
    final ArrayNode arrayNode = objectMapper.createArrayNode();
    for (final String part : parts) {
      arrayNode.add(createArrayElement(part, splitRule.trim()));
    }
    return arrayNode;
  }

  private JsonNode createArrayElement(final String part, final boolean shouldTrim) {
    final String processed = shouldTrim ? part.trim() : part;
    if (blanksAsNulls && processed.isBlank()) {
      return NullNode.getInstance();
    }
    return TextNode.valueOf(processed);
  }

  private JsonNode createLeafNode(final Object rawValue) {
    return switch (rawValue) {
      case null -> NullNode.getInstance();
      case String stringValue -> createStringNode(stringValue);
      case Integer intValue -> IntNode.valueOf(intValue);
      case Long longValue -> LongNode.valueOf(longValue);
      case Double doubleValue -> DoubleNode.valueOf(doubleValue);
      case Boolean boolValue -> BooleanNode.valueOf(boolValue);
      default -> objectMapper.valueToTree(rawValue);
    };
  }

  private JsonNode createStringNode(final String stringValue) {
    if (blanksAsNulls && stringValue.isBlank()) {
      return NullNode.getInstance();
    }
    return TextNode.valueOf(stringValue);
  }
}
