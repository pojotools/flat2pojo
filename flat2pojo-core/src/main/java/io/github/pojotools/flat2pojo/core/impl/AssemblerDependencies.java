package io.github.pojotools.flat2pojo.core.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pojotools.flat2pojo.core.engine.GroupingEngine;
import io.github.pojotools.flat2pojo.core.engine.PrimitiveListManager;
import io.github.pojotools.flat2pojo.core.engine.ValueTransformer;
import lombok.Builder;

/**
 * Bundles core dependencies for RowGraphAssembler construction. Reduces constructor parameter count.
 */
@Builder
record AssemblerDependencies(
    ObjectMapper objectMapper,
    GroupingEngine groupingEngine,
    ValueTransformer valueTransformer,
    PrimitiveListManager primitiveListManager,
    ResultMaterializer materializer) {}
