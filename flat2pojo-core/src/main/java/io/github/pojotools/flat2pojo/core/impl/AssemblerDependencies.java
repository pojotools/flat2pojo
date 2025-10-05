package io.github.pojotools.flat2pojo.core.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pojotools.flat2pojo.core.engine.GroupingEngine;
import io.github.pojotools.flat2pojo.core.engine.ValueTransformer;

/**
 * Bundles core dependencies for RowGraphAssembler construction.
 * Reduces constructor parameter count from 6 to 4.
 */
record AssemblerDependencies(
    ObjectMapper objectMapper,
    GroupingEngine groupingEngine,
    ValueTransformer valueTransformer,
    ResultMaterializer materializer
) {
}
