package io.github.pojotools.flat2pojo.core.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pojotools.flat2pojo.core.engine.ArrayManager;
import io.github.pojotools.flat2pojo.core.engine.PrimitiveArrayManager;
import io.github.pojotools.flat2pojo.core.engine.ValueTransformer;
import lombok.Builder;

/**
 * Bundles core dependencies for RowGraphAssembler construction. Reduces constructor parameter
 * count.
 */
@Builder
record AssemblerDependencies(
    ObjectMapper objectMapper,
    ArrayManager arrayManager,
    ValueTransformer valueTransformer,
    PrimitiveArrayManager primitiveArrayManager,
    ResultMaterializer materializer) {}
