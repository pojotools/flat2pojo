package io.github.pojotools.flat2pojo.core.util;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;

/**
 * Encapsulates all path-related operations with a specific separator. Eliminates primitive
 * obsession by bundling the separator with path operations.
 */
public record PathResolver(String separator) {

  /** Checks if a path is under any of the given parent paths. */
  public boolean isUnderAny(final String path, final Set<String> parentPaths) {
    return PathOps.isUnderAny(path, parentPaths, separator);
  }

  /** Gets the portion of a path after a prefix. Example: tailAfter("a/b/c", "a") returns "b/c" */
  public String tailAfter(final String path, final String prefix) {
    return PathOps.tailAfter(path, prefix, separator);
  }

  /** Gets the final segment of a path. Example: getFinalSegment("a/b/c") returns "c" */
  public String getFinalSegment(final String path) {
    return PathOps.getFinalSegment(path, separator);
  }

  /** Builds a path prefix by appending the separator. Example: buildPrefix("a/b") returns "a/b/" */
  public String buildPrefix(final String path) {
    return path + separator;
  }

  /** Strips the prefix from a path. Example: stripPrefix("a/b/c", "a/b/") returns "c" */
  public String stripPrefix(final String path, final String prefix) {
    return path.substring(prefix.length());
  }

  /** Traverses and ensures the path exists in the target node, creating objects as needed. */
  public ObjectNode traverseAndEnsurePath(final ObjectNode target, final String path) {
    return PathOps.traverseAndEnsurePath(target, path, separator, PathOps::ensureObject);
  }

  /**
   * Checks if a relative suffix is under any child list of the parent. Example: For parent
   * "definitions", checks if suffix "modules/name" starts with "modules/"
   */
  public boolean isSuffixUnderAnyChildList(
      final String suffix, final String parentPath, final Set<String> allListPaths) {
    final String parentPrefix = buildPrefix(parentPath);
    for (final String listPath : allListPaths) {
      if (listPath.startsWith(parentPrefix)) {
        final String childRelativePath = buildPrefix(listPath.substring(parentPrefix.length()));
        if (suffix.startsWith(childRelativePath)) {
          return true;
        }
      }
    }
    return false;
  }
}
