package io.github.pojotools.flat2pojo.core.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pojotools.flat2pojo.core.api.Flat2Pojo;
import io.github.pojotools.flat2pojo.core.config.ListHierarchyCache;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import io.github.pojotools.flat2pojo.core.config.MappingConfigLoader;
import io.github.pojotools.flat2pojo.core.engine.GroupingEngine;
import io.github.pojotools.flat2pojo.core.engine.PrimitiveListManager;
import io.github.pojotools.flat2pojo.core.engine.ValueTransformer;
import io.github.pojotools.flat2pojo.core.util.PathResolver;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class Flat2PojoCore implements Flat2Pojo {
  private final ObjectMapper objectMapper;

  public Flat2PojoCore(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public <T> Optional<T> convertOptional(
      Map<String, ?> flatRow, Class<T> type, MappingConfig config) {
    List<T> all = convertAll(List.of(flatRow), type, config);
    return all.isEmpty() ? Optional.empty() : Optional.of(all.getFirst());
  }

  /**
   * Converts flat key-value maps into structured POJOs using hierarchical list grouping.
   *
   * <p>Algorithm: Validate → Group by rootKeys → Process each group → Materialize to POJO
   *
   * @param rows flat key-value maps (e.g., from CSV, database JOIN results)
   * @param type target POJO class to convert to
   * @param config mapping configuration defining list rules, separators, conflict policies
   * @return list of structured POJOs, one per root group
   */
  @Override
  public <T> List<T> convertAll(
      final List<? extends Map<String, ?>> rows, final Class<T> type, final MappingConfig config) {
    MappingConfigLoader.validateHierarchy(config);
    final ProcessingPipeline pipeline = buildProcessingPipeline(config);

    return config.rootKeys().isEmpty()
        ? convertWithoutGrouping(rows, type, pipeline)
        : convertWithGrouping(rows, type, config, pipeline);
  }

  private ProcessingPipeline buildProcessingPipeline(final MappingConfig config) {
    final AssemblerDependencies dependencies = buildAssemblerDependencies(config);
    final ProcessingContext context = buildProcessingContext(config);
    return new ProcessingPipeline(dependencies, context);
  }

  private ProcessingContext buildProcessingContext(final MappingConfig config) {
    final PathResolver pathResolver = new PathResolver(config.separator());
    final ListHierarchyCache hierarchyCache = new ListHierarchyCache(config, pathResolver);
    return new ProcessingContext(config, hierarchyCache, pathResolver);
  }

  private AssemblerDependencies buildAssemblerDependencies(final MappingConfig config) {
    return AssemblerDependencies.builder()
      .objectMapper(objectMapper)
      .groupingEngine(new GroupingEngine(objectMapper, config))
      .valueTransformer(new ValueTransformer(objectMapper, config))
      .primitiveListManager(new PrimitiveListManager(objectMapper, config))
      .materializer(new ResultMaterializer(objectMapper))
      .build();
  }

  private <T> List<T> convertWithoutGrouping(
      final List<? extends Map<String, ?>> rows,
      final Class<T> type,
      final ProcessingPipeline pipeline) {
    final RowProcessor processor = pipeline.createAssembler();
    rows.forEach(processor::processRow);
    return List.of(processor.materialize(type));
  }

  private <T> List<T> convertWithGrouping(
      final List<? extends Map<String, ?>> rows,
      final Class<T> type,
      final MappingConfig config,
      final ProcessingPipeline pipeline) {
    final Map<Object, List<Map<String, ?>>> rowGroups =
        RootKeyGrouper.groupByRootKeys(rows, config.rootKeys());
    final List<T> results = new ArrayList<>(rowGroups.size());
    for (final List<Map<String, ?>> groupRows : rowGroups.values()) {
      results.add(processGroup(groupRows, type, pipeline));
    }
    return results;
  }

  private <T> T processGroup(
      final List<Map<String, ?>> groupRows,
      final Class<T> type,
      final ProcessingPipeline pipeline) {
    final RowProcessor processor = pipeline.createAssembler();
    groupRows.forEach(processor::processRow);
    return processor.materialize(type);
  }

  @Override
  public <T> Stream<T> stream(
      final Iterator<? extends Map<String, ?>> rows,
      final Class<T> type,
      final MappingConfig config) {
    final List<Map<String, ?>> list = new ArrayList<>();
    rows.forEachRemaining(list::add);
    return convertAll(list, type, config).stream();
  }
}
