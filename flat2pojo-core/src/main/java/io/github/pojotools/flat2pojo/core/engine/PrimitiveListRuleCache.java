package io.github.pojotools.flat2pojo.core.engine;

import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import java.util.HashMap;
import java.util.Map;

/**
 * Caches primitive list rules for fast path lookup.
 * Single Responsibility: Rule caching and lookup.
 */
final class PrimitiveListRuleCache {
  private final Map<String, MappingConfig.PrimitiveListRule> rulesByPath;

  PrimitiveListRuleCache(final MappingConfig config) {
    this.rulesByPath = buildCache(config);
  }

  private static Map<String, MappingConfig.PrimitiveListRule> buildCache(
      final MappingConfig config) {
    final Map<String, MappingConfig.PrimitiveListRule> cache = new HashMap<>();
    for (final MappingConfig.PrimitiveListRule rule : config.primitiveLists()) {
      cache.put(rule.path(), rule);
    }
    return cache;
  }

  boolean isPrimitiveListPath(final String path) {
    return rulesByPath.containsKey(path);
  }

  MappingConfig.PrimitiveListRule getRuleFor(final String path) {
    return rulesByPath.get(path);
  }
}

