package io.github.pojotools.flat2pojo.core.impl;

import java.util.Map;

/**
 * Processes individual flat rows and builds object graph structure. Enables plugin-based
 * preprocessing and alternative row-processing strategies.
 *
 * <p>Single Responsibility: Transform a single flat key-value row into a nested JSON structure.
 */
interface RowProcessor {
  /**
   * Processes a single flat row, updating internal state.
   *
   * @param row flat key-value map (e.g., from CSV, database result)
   */
  void processRow(Map<String, ?> row);

  /**
   * Materializes accumulated rows into a target POJO type.
   *
   * @param type target class to convert to
   * @param <T> target type
   * @return materialized POJO instance
   */
  <T> T materialize(Class<T> type);
}
