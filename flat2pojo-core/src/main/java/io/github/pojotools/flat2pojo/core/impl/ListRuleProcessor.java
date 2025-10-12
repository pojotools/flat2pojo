package io.github.pojotools.flat2pojo.core.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import io.github.pojotools.flat2pojo.core.engine.GroupingEngine;
import io.github.pojotools.flat2pojo.core.engine.Path;

import java.util.Map;
import java.util.Set;

/** Processes a single list rule for a row. Single Responsibility: List rule processing logic. */
final class ListRuleProcessor {
  private final ProcessingContext context;
  private final GroupingEngine groupingEngine;
  private final ListElementWriter writer;
  private final Map<String, ObjectNode> listElementCache; // Shared across rows

  ListRuleProcessor(
      final GroupingEngine groupingEngine,
      final ProcessingContext context,
      final ListElementWriter writer,
      final Map<String, ObjectNode> listElementCache) {
    this.context = context;
    this.groupingEngine = groupingEngine;
    this.writer = writer;
    this.listElementCache = listElementCache;
  }

  void processRule(
      final Map<String, JsonNode> rowValues,
      final Set<String> skippedListPaths,
      final MappingConfig.ListRule rule,
      final ObjectNode root) {
    if (shouldSkipDueToParent(rule.path(), skippedListPaths)) {
      return;
    }
    processListElementCreation(rowValues, skippedListPaths, rule, root);
  }

  private void processListElementCreation(
      final Map<String, JsonNode> rowValues,
      final Set<String> skippedListPaths,
      final MappingConfig.ListRule rule,
      final ObjectNode root) {
    final ObjectNode listElement = createListElement(rowValues, rule, root);
    if (listElement == null) {
      markAsSkipped(skippedListPaths, rule);
    } else {
      listElementCache.put(rule.path(), listElement);
      copyValuesToElement(rowValues, listElement, rule);
    }
  }

  private boolean shouldSkipDueToParent(final String listPath, final Set<String> skippedListPaths) {
    if (isSkippedDueToParent(listPath, skippedListPaths)) {
      context
          .config()
          .reporter()
          .ifPresent(
              r ->
                  r.warn(
                      "Skipping list rule '"
                          + listPath
                          + "' because parent list was skipped due to missing keyPath"));
      return true;
    }
    return false;
  }

  private void markAsSkipped(
      final Set<String> skippedListPaths, final MappingConfig.ListRule rule) {
    skippedListPaths.add(rule.path());
    context
        .config()
        .reporter()
        .ifPresent(
            r ->
                r.warn(
                    "Skipping list rule '"
                        + rule.path()
                        + "' because keyPath(s) "
                        + rule.keyPaths()
                        + " are missing or null"));
  }

  private boolean isSkippedDueToParent(final String listPath, final Set<String> skippedListPaths) {
    return context.pathResolver().isUnderAny(listPath, skippedListPaths);
  }

  private ObjectNode createListElement(
      final Map<String, JsonNode> rowValues,
      final MappingConfig.ListRule rule,
      final ObjectNode root) {
    final String listPath = rule.path();
    final String parentListPath = context.hierarchyCache().getParentListPath(listPath);
    final ObjectNode baseObject = findBaseObject(parentListPath, root);
    final String relativePath = computeRelativePath(listPath, parentListPath);
    return groupingEngine.upsertListElementRelative(baseObject, relativePath, rowValues, rule);
  }

  private ObjectNode findBaseObject(final String parentListPath, final ObjectNode root) {
    final ObjectNode baseObject = resolveBaseObject(parentListPath, root);
    if (baseObject == null) {
      throw new IllegalStateException(
          "Parent list element for '" + parentListPath + "' not found in cache");
    }
    return baseObject;
  }

  private String computeRelativePath(final String listPath, final String parentListPath) {
    return parentListPath == null
        ? listPath
        : context.pathResolver().tailAfter(listPath, parentListPath);
  }

  private ObjectNode resolveBaseObject(final String parentListPath, final ObjectNode root) {
    return parentListPath == null ? root : listElementCache.get(parentListPath);
  }

  private void copyValuesToElement(
      final Map<String, JsonNode> rowValues,
      final ObjectNode element,
      final MappingConfig.ListRule rule) {
    final WriteContext writeContext =
        new WriteContext(rule, context.pathResolver().buildPrefix(rule.path()));
    for (final var entry : rowValues.entrySet()) {
      if (entry.getKey().startsWith(writeContext.pathPrefix())) {
        writeValueIfNotUnderChild(element, entry, writeContext);
      }
    }
  }

  private void writeValueIfNotUnderChild(
      final ObjectNode element,
      final Map.Entry<String, JsonNode> entry,
      final WriteContext writeContext) {
    final String relativePath =
        context.pathResolver().stripPrefix(entry.getKey(), writeContext.pathPrefix());
    if (!context.hierarchyCache().isUnderAnyChildList(relativePath, writeContext.rule().path())) {
      final String absolutePath = entry.getKey();
      final Path path = new Path(relativePath, absolutePath);
      writer.writeWithConflictPolicy(
          element, path, entry.getValue(), writeContext.rule().onConflict());
    }
  }

  private record WriteContext(MappingConfig.ListRule rule, String pathPrefix) {}
}
