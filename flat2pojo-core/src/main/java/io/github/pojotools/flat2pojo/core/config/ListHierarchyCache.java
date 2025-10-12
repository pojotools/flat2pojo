package io.github.pojotools.flat2pojo.core.config;

import io.github.pojotools.flat2pojo.core.util.PathResolver;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Caches list hierarchy relationships for efficient lookup during conversion. Computes parent-child
 * relationships once and provides fast access methods.
 */
public final class ListHierarchyCache {
  private final Map<String, String> parentListPaths;
  private final Set<String> declaredListPaths;
  private final PathResolver pathResolver;

  public ListHierarchyCache(final MappingConfig config, final PathResolver pathResolver) {
    this.pathResolver = pathResolver;
    this.declaredListPaths = config.listPaths();
    this.parentListPaths = buildParentListPaths(config);
  }

  private Map<String, String> buildParentListPaths(final MappingConfig config) {
    final Map<String, String> result = new HashMap<>();
    final List<String> sortedPaths =
        config.lists().stream()
            .map(MappingConfig.ListRule::path)
            .sorted(Comparator.comparingInt(String::length))
            .toList();

    for (int i = 0; i < sortedPaths.size(); i++) {
      final String childPath = sortedPaths.get(i);

      for (int j = i - 1; j >= 0; j--) {
        final String potentialParent = sortedPaths.get(j);
        if (childPath.startsWith(pathResolver.buildPrefix(potentialParent))) {
          result.put(childPath, potentialParent);
          break;
        }
      }
    }
    return Map.copyOf(result);
  }

  public String getParentListPath(final String listPath) {
    return parentListPaths.get(listPath);
  }

  public boolean isUnderAnyList(final String path) {
    return pathResolver.isUnderAny(path, declaredListPaths);
  }

  public boolean isUnderAnyChildList(final String suffix, final String parentPath) {
    return pathResolver.isSuffixUnderAnyChildList(suffix, parentPath, declaredListPaths);
  }
}
