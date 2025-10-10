package io.github.pojotools.flat2pojo.core.util;

import io.github.pojotools.flat2pojo.core.config.MappingConfig.ConflictPolicy;
import io.github.pojotools.flat2pojo.spi.Reporter;
import java.util.Optional;

/**
 * Context object bundling conflict-handling parameters.
 *
 * <p>Reduces parameter count from 6 to 4 in ConflictHandler methods by grouping related policy,
 * path, and reporter information.
 */
public record ConflictContext(ConflictPolicy policy, String absolutePath, Reporter reporter) {

  /** Returns the reporter as Optional for null-safe usage. */
  public Optional<Reporter> reporterOptional() {
    return Optional.ofNullable(reporter);
  }
}
