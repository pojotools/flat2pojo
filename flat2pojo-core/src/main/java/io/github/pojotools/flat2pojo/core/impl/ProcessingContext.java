package io.github.pojotools.flat2pojo.core.impl;

import io.github.pojotools.flat2pojo.core.config.ListHierarchyCache;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import io.github.pojotools.flat2pojo.core.util.PathResolver;

/**
 * Immutable context object holding all configuration and utilities needed for processing.
 * Eliminates parameter passing throughout the processing pipeline.
 */
record ProcessingContext(
    MappingConfig config, ListHierarchyCache hierarchyCache, PathResolver pathResolver) {}
