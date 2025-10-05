package io.github.pojotools.flat2pojo.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.github.pojotools.flat2pojo.spi.Reporter;
import io.github.pojotools.flat2pojo.spi.ValuePreprocessor;
import org.immutables.value.Value;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    final List<ListRule> rules = lists();
    if (rules.isEmpty()) {
      return Map.of();
    }

    final Map<String, Set<String>> prefixMap = new HashMap<>();
    final String sep = separator();

    // Sort rules by path length (shorter first) to optimize parent-child detection
    // This allows us to build the map in a single pass: O(n log n) instead of O(n²)
    final List<String> sortedPaths =
        rules.stream().map(ListRule::path).sorted(Comparator.comparingInt(String::length)).toList();

    for (final String rulePath : sortedPaths) {
      prefixMap.put(rulePath, new HashSet<>());
    }

    // For each path, find its DIRECT parent (longest matching prefix)
    for (int i = 0; i < sortedPaths.size(); i++) {
      final String childPath = sortedPaths.get(i);
      final String childPrefix = childPath + sep;

      // Find the longest (most specific) parent
      String directParent = null;
      for (int j = i - 1; j >= 0; j--) { // Iterate backwards to find longest match first
        final String potentialParent = sortedPaths.get(j);
        if (childPath.startsWith(potentialParent + sep)) {
          directParent = potentialParent;
          break; // Found the direct parent, stop searching
        }
      }

      // Add this child to its direct parent's set (if found)
      if (directParent != null) {
        prefixMap.get(directParent).add(childPrefix);
      }
    }

    // Make sets immutable
    final Map<String, Set<String>> immutableMap = new HashMap<>();
    for (final var entry : prefixMap.entrySet()) {
      immutableMap.put(entry.getKey(), Set.copyOf(entry.getValue()));
    }

    return Map.copyOf(immutableMap);
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
