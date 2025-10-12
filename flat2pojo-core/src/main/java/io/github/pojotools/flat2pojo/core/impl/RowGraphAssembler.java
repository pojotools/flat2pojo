package io.github.pojotools.flat2pojo.core.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Assembles object graphs from flat rows by processing list rules and direct values. Single
 * Responsibility: Builds nested JSON tree structure from flat key-value rows.
 */
final class RowGraphAssembler implements RowProcessor {
  private final ObjectNode root;
  private final AssemblerDependencies dependencies;
  private final ProcessingContext context;
  private final ListRuleProcessor listRuleProcessor;
  private final ListElementWriter listElementWriter;
  private final DirectValueWriter directValueWriter;
  private final Function<Map<String, ?>, Map<String, ?>> preprocessor;
  private final Map<String, ObjectNode> listElementCache = new LinkedHashMap<>();

  RowGraphAssembler(final AssemblerDependencies dependencies, final ProcessingContext context) {
    this.dependencies = dependencies;
    this.root = dependencies.objectMapper().createObjectNode();
    this.context = context;
    this.listElementWriter = new ListElementWriter(context, dependencies.primitiveAccumulator());
    this.directValueWriter = new DirectValueWriter(context, dependencies.primitiveAccumulator());
    this.listRuleProcessor =
        new ListRuleProcessor(dependencies.groupingEngine(), context, listElementWriter, listElementCache);
    this.preprocessor = buildPreprocessor(context.config());
  }

  @Override
  public void processRow(final Map<String, ?> row) {
    final Map<String, ?> preprocessed = preprocessor.apply(row);
    final Map<String, JsonNode> rowValues =
        dependencies.valueTransformer().transformRowValuesToJsonNodes(preprocessed);
    final Set<String> skippedListPaths = processListRules(rowValues);
    processDirectValues(rowValues, skippedListPaths);
  }

  @Override
  public <T> T materialize(final Class<T> type) {
    dependencies.groupingEngine().finalizeArrays(root);
    writeAccumulatedArrays();
    return dependencies.materializer().materialize(root, type);
  }

  private void writeAccumulatedArrays() {
    // Write all accumulated arrays by traversing the entire tree and writing to each node
    // This must happen AFTER finalizeArrays so that list elements are in the tree
    dependencies.primitiveAccumulator().writeAllAccumulatedArrays(root);
  }

  private static Function<Map<String, ?>, Map<String, ?>> buildPreprocessor(
      final MappingConfig config) {
    return config
        .valuePreprocessor()
        .map(p -> (Function<Map<String, ?>, Map<String, ?>>) p::process)
        .orElse(Function.identity());
  }

  private Set<String> processListRules(final Map<String, JsonNode> rowValues) {
    final Set<String> skippedListPaths = new HashSet<>();
    for (final MappingConfig.ListRule rule : context.config().lists()) {
      listRuleProcessor.processRule(rowValues, skippedListPaths, rule, root);
    }
    return skippedListPaths;
  }

  private void processDirectValues(
      final Map<String, JsonNode> rowValues, final Set<String> skippedListPaths) {
    for (final var entry : rowValues.entrySet()) {
      final String path = entry.getKey();
      if (isDirectValuePath(path, skippedListPaths)) {
        directValueWriter.writeDirectly(root, path, entry.getValue());
      }
    }
  }

  private boolean isDirectValuePath(final String path, final Set<String> skippedListPaths) {
    return isEligibleForDirectWrite(path, skippedListPaths);
  }

  private boolean isEligibleForDirectWrite(final String path, final Set<String> skippedListPaths) {
    final boolean underAnyList = context.hierarchyCache().isUnderAnyList(path);
    final boolean skipped = context.pathResolver().isUnderAny(path, skippedListPaths);
    return !underAnyList && !skipped;
  }
}
