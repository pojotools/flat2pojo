package io.flat2pojo.core.util;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;

/**
 * High-performance path manipulation utilities for flat2pojo.
 *
 * <p>This class provides optimized string operations for handling hierarchical paths
 * without the overhead of regular expressions or unnecessary string allocations.
 * All methods are static and thread-safe.
 */
public final class PathOps {
  private PathOps() {}

  public static int nextSep(final String s, final int from, final char sepCh) {
    return s.indexOf(sepCh, from);
  }

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

  public static List<String> splitPath(final String path, final char sepCh) {
    final List<String> segments = new ArrayList<>();
    int start = 0;
    int sepIndex;
    while ((sepIndex = nextSep(path, start, sepCh)) >= 0) {
      segments.add(path.substring(start, sepIndex));
      start = sepIndex + 1;
    }
    if (start < path.length()) {
      segments.add(path.substring(start));
    }
    return segments;
  }

  public static List<String> splitPath(final String path, final String separator) {
    if (separator.length() == 1) {
      return splitPath(path, separator.charAt(0));
    }
    final List<String> segments = new ArrayList<>();
    int start = 0;
    int sepIndex;
    while ((sepIndex = path.indexOf(separator, start)) >= 0) {
      segments.add(path.substring(start, sepIndex));
      start = sepIndex + separator.length();
    }
    if (start < path.length()) {
      segments.add(path.substring(start));
    }
    return segments;
  }

  /**
   * Traverses a path and ensures all intermediate ObjectNodes exist.
   * Returns the final parent ObjectNode where the last segment should be set.
   */
  public static ObjectNode traverseAndEnsurePath(
      final ObjectNode root,
      final String path,
      final char separatorChar,
      final ObjectNodeEnsurer ensurer) {
    ObjectNode current = root;
    int start = 0;
    int sepIndex;

    while ((sepIndex = nextSep(path, start, separatorChar)) >= 0) {
      final String segment = path.substring(start, sepIndex);
      current = ensurer.ensureObject(current, segment);
      start = sepIndex + 1;
    }

    return current;
  }

  /**
   * Gets the final segment of a path after the last separator.
   */
  public static String getFinalSegment(final String path, final char separatorChar) {
    final int lastSep = path.lastIndexOf(separatorChar);
    return lastSep >= 0 ? path.substring(lastSep + 1) : path;
  }

  /**
   * Interface for ensuring ObjectNode creation - allows different implementations.
   */
  @FunctionalInterface
  public interface ObjectNodeEnsurer {
    ObjectNode ensureObject(ObjectNode parent, String fieldName);
  }
}