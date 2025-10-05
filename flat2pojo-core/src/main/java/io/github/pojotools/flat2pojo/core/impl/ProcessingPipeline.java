package io.github.pojotools.flat2pojo.core.impl;

/**
 * Encapsulates pipeline configuration for creating RowGraphAssembler instances.
 * Eliminates repetitive parameter passing in Flat2PojoCore.
 */
record ProcessingPipeline(
    AssemblerDependencies dependencies,
    ProcessingContext context
) {
  RowGraphAssembler createAssembler() {
    return new RowGraphAssembler(dependencies, context);
  }
}
