package io.github.pojotools.flat2pojo.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.github.pojotools.flat2pojo.spi.Reporter;
import io.github.pojotools.flat2pojo.spi.ValuePreprocessor;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.immutables.value.Value;

/**
 * Immutable configuration for flat-to-POJO conversion.
 *
 * <p>This class defines how flat key-value maps should be transformed into structured objects,
 * including rules for grouping related data into lists, handling conflicts, and processing
 * primitive values.
 *
 * <p>Instances are thread-safe and should be reused across multiple conversions for optimal
 * performance. Use {@link ImmutableMappingConfig#builder()} to create new configurations.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableMappingConfig.class)
@JsonDeserialize(as = ImmutableMappingConfig.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class MappingConfig {

  // ======= ABSTRACT GETTERS (implemented by Immutables) =======

  @Value.Default
  public String separator() {
    return "/";
  }

  @Value.Default
  public boolean allowSparseRows() {
    return false;
  }

  @Value.Default
  public List<String> rootKeys() {
    return List.of();
  }

  @Value.Default
  public List<ListRule> lists() {
    return List.of();
  }

  @Value.Default
  public List<PrimitiveSplitRule> primitives() {
    return List.of();
  }

  @Value.Default
  public List<PrimitiveListRule> primitiveLists() {
    return List.of();
  }

  @Value.Default
  public NullPolicy nullPolicy() {
    return new NullPolicy(false);
  }

  @Value.Default
  public Optional<Reporter> reporter() {
    return Optional.empty();
  }

  @Value.Default
  public Optional<ValuePreprocessor> valuePreprocessor() {
    return Optional.empty();
  }

  // ======= DERIVED/CACHED FIELDS =======

  @Value.Derived
  public Set<String> listPaths() {
    return precomputeListPaths();
  }

  // ======= CONVENIENCE BUILDER =======

  public static ImmutableMappingConfig.Builder builder() {
    return ImmutableMappingConfig.builder();
  }

  // ======= HELPER METHODS FOR DERIVED FIELDS =======

  private Set<String> precomputeListPaths() {
    final Set<String> paths = new HashSet<>();
    for (final ListRule rule : lists()) {
      paths.add(rule.path());
    }
    return Set.copyOf(paths);
  }

  // ======= VALUE TYPES =======

  public record NullPolicy(boolean blanksAsNulls) {}

  public record ListRule(
      String path,
      List<String> keyPaths,
      List<OrderBy> orderBy,
      boolean dedupe,
      ConflictPolicy onConflict) {}

  public enum ConflictPolicy {
    error,
    lastWriteWins,
    firstWriteWins,
    merge
  }

  public record OrderBy(String path, OrderDirection direction, Nulls nulls) {}

  public enum Nulls {
    first,
    last
  }

  public record PrimitiveSplitRule(String path, String delimiter, boolean trim) {}

  public record PrimitiveListRule(String path, OrderDirection orderDirection, boolean dedup) {}

  public enum OrderDirection {
    insertion,
    asc,
    desc
  }
}
