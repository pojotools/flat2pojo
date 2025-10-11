package io.github.pojotools.flat2pojo.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/**
 * High-performance path manipulation utilities for flat2pojo.
 *
 * <p>This class provides optimized string operations for handling hierarchical paths without the
 * overhead of regular expressions or unnecessary string allocations. All methods are static and
 * thread-safe.
 */
public final class PathOps {
  private PathOps() {}

  public static String tailAfter(final String s, final String prefix, final String sep) {
    if (!s.startsWith(prefix)) {
      return s;
    }
    final int prefixLen = prefix.length();
    if (s.length() == prefixLen) {
      return "";
    }
    if (s.startsWith(sep, prefixLen)) {
      return s.substring(prefixLen + sep.length());
    }
    return s.substring(prefixLen);
  }

  public static boolean isUnder(final String path, final String prefix, final String sep) {
    return path.equals(prefix) || path.startsWith(prefix + sep);
  }

  public static List<String> splitPath(final String path, final String separator) {
    return List.of(path.split(java.util.regex.Pattern.quote(separator), -1));
  }

  /**
   * Traverses a path and ensures all intermediate ObjectNodes exist. Returns the final parent
   * ObjectNode where the last segment should be set. Supports multi-character separators.
   */
  public static ObjectNode traverseAndEnsurePath(
      final ObjectNode root,
      final String path,
      final String separator,
      final ObjectNodeEnsurer ensurer) {
    ObjectNode current = root;
    int start = 0;
    int sepIndex;

    while ((sepIndex = path.indexOf(separator, start)) >= 0) {
      final String segment = path.substring(start, sepIndex);
      current = ensurer.ensureObject(current, segment);
      start = sepIndex + separator.length();
    }

    return current;
  }

  /** Gets the final segment of a path after the last separator. */
  public static String getFinalSegment(final String path, final String separator) {
    final int lastSep = path.lastIndexOf(separator);
    return lastSep >= 0 ? path.substring(lastSep + separator.length()) : path;
  }

  /** Interface for ensuring ObjectNode creation - allows different implementations. */
  @FunctionalInterface
  public interface ObjectNodeEnsurer {
    ObjectNode ensureObject(ObjectNode parent, String fieldName);
  }

  /** Standard implementation of ensureObject - consolidated from 3 duplicates. */
  public static ObjectNode ensureObject(final ObjectNode parent, final String fieldName) {
    final JsonNode existing = parent.get(fieldName);
    if (existing instanceof ObjectNode objectNode) {
      return objectNode;
    }
    // If field doesn't exist or is not an ObjectNode, create/replace with ObjectNode
    final ObjectNode created = parent.objectNode();
    parent.set(fieldName, created);
    return created;
  }

  /**
   * Finds the nearest parent path from a set of candidate paths by iterating backwards. Returns the
   * longest prefix of the given path that exists in the candidates set.
   *
   * @param path the path to find a parent for
   * @param candidates set of potential parent paths
   * @param separator the path separator
   * @return the nearest parent path, or null if none found
   */
  public static String findParentPath(
      final String path, final java.util.Set<String> candidates, final String separator) {
    int lastSepIndex = path.lastIndexOf(separator);
    while (lastSepIndex > 0) {
      final String prefix = path.substring(0, lastSepIndex);
      if (candidates.contains(prefix)) {
        return prefix;
      }
      lastSepIndex = prefix.lastIndexOf(separator);
    }
    return null;
  }

  /**
   * Checks if a path is under any of the candidate paths.
   *
   * @param path the path to check
   * @param candidates collection of potential parent paths
   * @param separator the path separator
   * @return true if path is under any candidate, false otherwise
   */
  public static boolean isUnderAny(
      final String path, final java.util.Collection<String> candidates, final String separator) {
    for (final String candidate : candidates) {
      if (isUnder(path, candidate, separator)) {
        return true;
      }
    }
    return false;
  }
}
