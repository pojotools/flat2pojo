package io.github.pojotools.flat2pojo.core.impl;

/**
 * Encapsulates pipeline configuration for creating RowProcessor instances. Eliminates repetitive
 * parameter passing in Flat2PojoCore.
 */
record ProcessingPipeline(AssemblerDependencies dependencies, ProcessingContext context) {
  RowProcessor createAssembler() {
    return new RowGraphAssembler(dependencies, context);
  }
}
