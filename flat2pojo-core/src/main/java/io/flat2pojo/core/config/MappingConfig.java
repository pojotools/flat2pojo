package io.flat2pojo.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;
import java.util.*;

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
  public NullPolicy nullPolicy() {
    return new NullPolicy(false);
  }

  // ======= DERIVED/CACHED FIELDS =======

  @Value.Derived
  public char separatorChar() {
    return separator().length() == 1 ? separator().charAt(0) : '/';
  }

  @Value.Derived
  public Set<String> listPaths() {
    return precomputeListPaths();
  }

  @Value.Derived
  public Map<String, Set<String>> childListPrefixesMap() {
    return precomputeChildListPrefixes();
  }

  public Set<String> getChildListPrefixes(final String listPath) {
    return childListPrefixesMap().getOrDefault(listPath, Set.of());
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

  private Map<String, Set<String>> precomputeChildListPrefixes() {
    final Map<String, Set<String>> prefixMap = new HashMap<>();
    final String sep = separator();

    for (final ListRule rule : lists()) {
      final String rulePath = rule.path();
      final Set<String> childPrefixes = new HashSet<>();

      for (final ListRule otherRule : lists()) {
        final String otherPath = otherRule.path();
        if (!otherPath.equals(rulePath) && otherPath.startsWith(rulePath + sep)) {
          childPrefixes.add(otherPath + sep);
        }
      }

      prefixMap.put(rulePath, Set.copyOf(childPrefixes));
    }

    return Map.copyOf(prefixMap);
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

  public record OrderBy(String path, Direction direction, Nulls nulls) {}

  public enum Direction {
    asc,
    desc
  }

  public enum Nulls {
    first,
    last
  }

  public record PrimitiveSplitRule(String path, String delimiter, boolean trim) {}
}
