package io.flat2pojo.core.api;

import io.flat2pojo.core.config.MappingConfig;
import java.util.*;
import java.util.stream.Stream;

/**
 * Core interface for converting flat key-value maps to structured POJOs.
 *
 * <p>This interface provides methods to transform flat data structures (such as those from CSV files,
 * database results, or key-value stores) into nested object hierarchies using declarative configuration.
 *
 * <p>The conversion process follows a Jackson-first approach: builds an intermediate JsonNode tree
 * structure, then leverages Jackson's powerful type conversion and mapping capabilities to produce
 * the final POJOs.
 *
 * <p>All implementations are thread-safe and can be used concurrently.
 */
public interface Flat2Pojo {

  /**
   * Converts a single flat row to a POJO.
   *
   * @param flatRow the flat key-value map to convert
   * @param type the target POJO class
   * @param config the mapping configuration
   * @param <T> the target type
   * @return the converted POJO, or null if the input is empty
   */
  <T> T convert(Map<String, ?> flatRow, Class<T> type, MappingConfig config);

  /**
   * Converts multiple flat rows to a list of POJOs with grouping and hierarchical structure.
   *
   * <p>Rows with the same root key (as defined by {@link MappingConfig#rootKeys()}) are grouped
   * together and converted into a single POJO with nested lists and objects.
   *
   * @param flatRows the flat rows to convert
   * @param type the target POJO class
   * @param config the mapping configuration
   * @param <T> the target type
   * @return list of converted POJOs
   */
  <T> List<T> convertAll(
      List<? extends Map<String, ?>> flatRows, Class<T> type, MappingConfig config);

  /**
   * Converts rows from an iterator to a stream of POJOs.
   *
   * <p>This method is useful for processing large datasets that don't fit in memory.
   * Note that streaming conversion is less efficient than batch processing with {@link #convertAll}.
   *
   * @param rows iterator of flat rows
   * @param type the target POJO class
   * @param config the mapping configuration
   * @param <T> the target type
   * @return stream of converted POJOs
   */
  <T> Stream<T> stream(
      Iterator<? extends Map<String, ?>> rows, Class<T> type, MappingConfig config);
}
