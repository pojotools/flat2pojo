package io.github.pojotools.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts composite keys from row values for list element deduplication. Single Responsibility:
 * Key extraction logic.
 */
final class CompositeKeyExtractor {
  private final String separator;

  CompositeKeyExtractor(final String separator) {
    this.separator = separator;
  }

  CompositeKey extractFrom(
      final java.util.Map<String, JsonNode> rowValues, final MappingConfig.ListRule rule) {
    final List<Object> keyValues = collectKeyValues(rowValues, rule);
    return keyValues == null ? null : new CompositeKey(keyValues);
  }

  @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
  private List<Object> collectKeyValues(
      final java.util.Map<String, JsonNode> rowValues, final MappingConfig.ListRule rule) {
    final List<Object> keyValues = new ArrayList<>(rule.keyPaths().size());
    final String absolutePrefix = buildAbsolutePrefix(rule.path());

    for (final String relativeKeyPath : rule.keyPaths()) {
      final JsonNode value = rowValues.get(absolutePrefix + relativeKeyPath);
      if (isNullOrMissing(value)) {
        return null; // Signals missing key path - intentional null return
      }
      keyValues.add(value);
    }
    return keyValues;
  }

  private String buildAbsolutePrefix(final String path) {
    return path + separator;
  }

  private boolean isNullOrMissing(final JsonNode value) {
    return value == null || value.isNull();
  }
}
