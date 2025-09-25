package io.flat2pojo.core.impl;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import io.flat2pojo.core.api.Flat2Pojo;
import io.flat2pojo.core.api.Flat2PojoException;
import io.flat2pojo.core.config.MappingConfig;
import io.flat2pojo.core.config.MappingConfig.ConflictPolicy;
import io.flat2pojo.core.config.MappingConfigLoader;
import io.flat2pojo.core.engine.FlatTreeBuilder;
import io.flat2pojo.core.engine.GroupingEngine;
import io.flat2pojo.core.util.ConflictHandler;
import io.flat2pojo.core.util.PathOps;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public final class Flat2PojoCore implements Flat2Pojo {
  private final ObjectMapper om;

  public Flat2PojoCore(ObjectMapper om) {
    this.om = om;
  }

  @Override
  public <T> T convert(Map<String, ?> flatRow, Class<T> type, MappingConfig config) {
    List<T> all = convertAll(List.of(flatRow), type, config);
    return all.isEmpty() ? null : all.getFirst();
  }

  @Override
  public <T> List<T> convertAll(
      final List<? extends Map<String, ?>> rows, final Class<T> type, final MappingConfig cfg) {
    MappingConfigLoader.validateHierarchy(cfg);

    final ConfigurationCache configCache = new ConfigurationCache(cfg);
    final Function<Map<String, ?>, String> rootKeyFn = buildRootKeyFunction(cfg);

    final Map<String, List<Map<String, ?>>> buckets = groupRowsByRootKey(rows, rootKeyFn);
    final List<T> results = new ArrayList<>(buckets.size());

    for (final var entry : buckets.entrySet()) {
      final T result = processRootGroup(entry.getValue(), type, cfg, configCache);
      results.add(result);
    }

    return results;
  }

  private Function<Map<String, ?>, String> buildRootKeyFunction(final MappingConfig cfg) {
    if (cfg.rootKeys().isEmpty()) {
      return r -> "__single";
    }

    return r -> {
      final StringBuilder sb = new StringBuilder(64);
      for (final String k : cfg.rootKeys()) {
        sb.append(k).append('=').append(r.get(k)).append('|');
      }
      return sb.toString();
    };
  }

  private Map<String, List<Map<String, ?>>> groupRowsByRootKey(
      final List<? extends Map<String, ?>> rows, final Function<Map<String, ?>, String> rootKeyFn) {
    final Map<String, List<Map<String, ?>>> buckets = new LinkedHashMap<>();
    for (final Map<String, ?> row : rows) {
      final String key = rootKeyFn.apply(row);
      buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
    }
    return buckets;
  }

  private <T> T processRootGroup(
      final List<Map<String, ?>> groupRows,
      final Class<T> type,
      final MappingConfig cfg,
      final ConfigurationCache configCache) {
    final ObjectNode root = om.createObjectNode();
    final GroupingEngine ge = new GroupingEngine(om, cfg);
    final FlatTreeBuilder tb = new FlatTreeBuilder(om, cfg);

    for (final Map<String, ?> row : groupRows) {
      processRowIntoTree(row, root, ge, tb, cfg, configCache);
    }

    ge.finalizeArrays(root);
    return materializeResult(root, type);
  }

  private void processRowIntoTree(
      final Map<String, ?> row,
      final ObjectNode root,
      final GroupingEngine ge,
      final FlatTreeBuilder tb,
      final MappingConfig cfg,
      final ConfigurationCache configCache) {
    final ObjectNode rowNode = tb.buildTreeForRow(row);
    final Map<String, JsonNode> rowValues = flattenObjectNode(rowNode, "");
    final Map<String, ObjectNode> rowElementCache = new LinkedHashMap<>();

    final Set<String> skippedListPaths = processListRules(rowValues, rowElementCache, root, ge, cfg, configCache);
    processNonListValues(rowValues, root, cfg, configCache, skippedListPaths);
  }

  private Set<String> processListRules(
      final Map<String, JsonNode> rowValues,
      final Map<String, ObjectNode> rowElementCache,
      final ObjectNode root,
      final GroupingEngine ge,
      final MappingConfig cfg,
      final ConfigurationCache configCache) {
    final List<MappingConfig.ListRule> listRules = cfg.lists();
    final Set<String> skippedListPaths = new HashSet<>();
    for (final MappingConfig.ListRule rule : listRules) {
      final String path = rule.path();
      // Skip if any ancestor list path was skipped
      if (isUnderAnySkippedPath(path, skippedListPaths, cfg.separator())) {
        skippedListPaths.add(path);
        continue;
      }

      final String nearestAncestor = configCache.getParentListPath(path);
      final ObjectNode base = determineBaseObject(nearestAncestor, rowElementCache, root);
      final String relPath = calculateRelativePath(path, nearestAncestor, cfg.separator());

      final ObjectNode element = ge.upsertListElementRelative(base, relPath, rowValues, rule);
      if (element != null) {
        rowElementCache.put(path, element);
        copyListSubtreeValues(rowValues, element, rule, cfg, configCache);
      } else {
        // Track skipped list paths to prevent processing their subtree values
        skippedListPaths.add(path);
      }
    }
    return skippedListPaths;
  }

  private ObjectNode determineBaseObject(
      final String nearestAncestor, final Map<String, ObjectNode> rowElementCache, final ObjectNode root) {
    if (nearestAncestor != null) {
      final ObjectNode base = rowElementCache.get(nearestAncestor);
      if (base == null) {
        throw new IllegalStateException(
            "Parent list element for ancestor '" + nearestAncestor + "' not initialized");
      }
      return base;
    }
    return root;
  }

  private String calculateRelativePath(final String path, final String nearestAncestor, final String separator) {
    if (nearestAncestor != null) {
      return PathOps.tailAfter(path, nearestAncestor, separator);
    }
    return path;
  }

  private void copyListSubtreeValues(
      final Map<String, JsonNode> rowValues,
      final ObjectNode element,
      final MappingConfig.ListRule rule,
      final MappingConfig cfg,
      final ConfigurationCache configCache) {
    final String rulePath = rule.path();
    final String separator = cfg.separator();
    final Set<String> childListPrefixes = configCache.getChildListPrefixes(rulePath);

    for (final var entry : rowValues.entrySet()) {
      final String valuePath = entry.getKey();

      if (PathOps.isUnder(valuePath, rulePath, separator)) {
        if (!isValueInChildListSubtree(valuePath, childListPrefixes, separator)) {
          final String suffix = PathOps.tailAfter(valuePath, rulePath, separator);
          writeValueWithPolicy(element, suffix, entry.getValue(), separator, rule.onConflict());
        }
      }
    }
  }

  private boolean isValueInChildListSubtree(
      final String valuePath, final Set<String> childListPrefixes, final String separator) {
    for (final String childPrefix : childListPrefixes) {
      final String childPath = childPrefix.substring(0, childPrefix.length() - separator.length());
      if (PathOps.isUnder(valuePath, childPath, separator)) {
        return true;
      }
    }
    return false;
  }

  private void processNonListValues(
      final Map<String, JsonNode> rowValues,
      final ObjectNode root,
      final MappingConfig cfg,
      final ConfigurationCache configCache,
      final Set<String> skippedListPaths) {
    final String separator = cfg.separator();
    for (final var entry : rowValues.entrySet()) {
      final String path = entry.getKey();
      boolean isListPath = configCache.isListPath(path);
      boolean inSkippedSubtree = isUnderAnySkippedPath(path, skippedListPaths, separator);
      if (!isListPath && !inSkippedSubtree) {
        writeValueIntoNode(root, path, entry.getValue(), separator);
      }
    }
  }

  private boolean isUnderAnySkippedPath(
      final String path, final Set<String> skippedListPaths, final String separator) {
    for (final String skippedPath : skippedListPaths) {
      if (PathOps.isUnder(path, skippedPath, separator)) {
        return true;
      }
    }
    return false;
  }

  private <T> T materializeResult(final ObjectNode root, final Class<T> type) {
    try {
      if (JsonNode.class.isAssignableFrom(type)) {
        @SuppressWarnings("unchecked")
        final T cast = (T) root;
        return cast;
      } else {
        return om.treeToValue(root, type);
      }
    } catch (final Exception ex) {
      throw new Flat2PojoException("Failed to map result to " + type.getName(), ex);
    }
  }

  private static final class ConfigurationCache {
    private final Map<String, String> parentListPaths = new HashMap<>();
    private final Map<String, Set<String>> childListPrefixes = new HashMap<>();
    private final Set<String> allListPaths;
    private final String separator;
    private final char separatorChar;

    ConfigurationCache(final MappingConfig cfg) {
      this.separator = cfg.separator();
      this.separatorChar = separator.length() == 1 ? separator.charAt(0) : '/';
      this.allListPaths = buildListPathsSet(cfg);
      precomputeParentRelationships(cfg);
      precomputeChildListPrefixes(cfg);
    }

    private Set<String> buildListPathsSet(final MappingConfig cfg) {
      final Set<String> paths = new HashSet<>();
      for (final var rule : cfg.lists()) {
        final String path = rule.path();
        paths.add(path);

        // Add all prefixes of this path for efficient lookup
        int sepIndex = PathOps.nextSep(path, 0, separatorChar);
        while (sepIndex >= 0) {
          final String prefix = path.substring(0, sepIndex);
          paths.add(prefix);
          sepIndex = PathOps.nextSep(path, sepIndex + 1, separatorChar);
        }
      }
      return paths;
    }

    private void precomputeParentRelationships(final MappingConfig cfg) {
      final List<MappingConfig.ListRule> rules = cfg.lists();

      for (int i = 0; i < rules.size(); i++) {
        final String path = rules.get(i).path();
        String parent = null;

        // Find nearest ancestor among previously declared rules
        for (int j = 0; j < i; j++) {
          final String candidateParent = rules.get(j).path();
          if (PathOps.isUnder(path, candidateParent, separator)) {
            parent = candidateParent;
          }
        }

        if (parent != null) {
          parentListPaths.put(path, parent);
        }
      }
    }

    private void precomputeChildListPrefixes(final MappingConfig cfg) {
      for (final var rule : cfg.lists()) {
        final String rulePath = rule.path();
        final Set<String> childPrefixes = new HashSet<>();

        for (final var otherRule : cfg.lists()) {
          final String otherPath = otherRule.path();
          if (!otherPath.equals(rulePath) && PathOps.isUnder(otherPath, rulePath, separator)) {
            childPrefixes.add(otherPath + separator);
          }
        }

        childListPrefixes.put(rulePath, childPrefixes);
      }
    }

    String getParentListPath(final String listPath) {
      return parentListPaths.get(listPath);
    }

    Set<String> getChildListPrefixes(final String listPath) {
      return childListPrefixes.getOrDefault(listPath, Set.of());
    }

    boolean isListPath(final String path) {
      if (allListPaths.contains(path)) {
        return true;
      }
      for (final String listPath : allListPaths) {
        if (PathOps.isUnder(path, listPath, separator)) {
          return true;
        }
      }
      return false;
    }
  }

  @Override
  public <T> Stream<T> stream(
      final Iterator<? extends Map<String, ?>> rows, final Class<T> type, final MappingConfig config) {
    final List<Map<String, ?>> list = new ArrayList<>();
    rows.forEachRemaining(list::add);
    return convertAll(list, type, config).stream();
  }

  private Map<String, JsonNode> flattenObjectNode(final ObjectNode node, final String prefix) {
    final Map<String, JsonNode> result = new LinkedHashMap<>();
    flattenObjectNodeRecursive(node, prefix, result);
    return result;
  }

  private void flattenObjectNodeRecursive(
      final ObjectNode node, final String prefix, final Map<String, JsonNode> result) {
    final Iterator<String> fieldNames = node.fieldNames();
    while (fieldNames.hasNext()) {
      final String fieldName = fieldNames.next();
      final JsonNode value = node.get(fieldName);
      final String path = prefix.isEmpty() ? fieldName : prefix + "/" + fieldName;

      if (value.isObject()) {
        flattenObjectNodeRecursive((ObjectNode) value, path, result);
      } else {
        result.put(path, value);
      }
    }
  }

  private void writeValueIntoNode(
      final ObjectNode target, final String path, final JsonNode value, final String separator) {
    if (path.isEmpty()) return;

    final ObjectNode parent = navigateToParentNode(target, path, separator);
    final String leafField = getLeafFieldName(path, separator);
    parent.set(leafField, value);
  }

  private ObjectNode navigateToParentNode(final ObjectNode target, final String path, final String separator) {
    final List<String> segments = PathOps.splitPath(path, separator);
    ObjectNode current = target;

    for (int i = 0; i < segments.size() - 1; i++) {
      current = ensureChildObjectNode(current, segments.get(i));
    }

    return current;
  }

  private String getLeafFieldName(final String path, final String separator) {
    final List<String> segments = PathOps.splitPath(path, separator);
    return segments.getLast();
  }

  private ObjectNode ensureChildObjectNode(final ObjectNode parent, final String fieldName) {
    final JsonNode existing = parent.get(fieldName);
    if (existing instanceof ObjectNode) {
      return (ObjectNode) existing;
    }

    final ObjectNode newChild = parent.objectNode();
    parent.set(fieldName, newChild);
    return newChild;
  }


  private void writeValueWithPolicy(
      final ObjectNode target, final String path, final JsonNode value, final String separator, final ConflictPolicy policy) {
    if (path.isEmpty()) return;

    final ObjectNode parent = navigateToParentNode(target, path, separator);
    final String leafField = getLeafFieldName(path, separator);
    ConflictHandler.writeScalarWithPolicy(parent, leafField, value, policy, path);
  }
}
