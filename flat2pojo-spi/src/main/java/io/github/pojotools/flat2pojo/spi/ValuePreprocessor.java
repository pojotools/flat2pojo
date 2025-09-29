package io.github.pojotools.flat2pojo.spi;

import java.util.Map;

/**
 * Optional hook to adjust a flat row BEFORE it becomes a JSON tree. Return the same map (mutated)
 * or a new one.
 */
@FunctionalInterface
public interface ValuePreprocessor {
  Map<String, ?> process(Map<String, ?> flatRow);
}
