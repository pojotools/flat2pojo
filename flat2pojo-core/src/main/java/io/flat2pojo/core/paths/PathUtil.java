package io.flat2pojo.core.paths;

import java.util.*;

public final class PathUtil {
  private PathUtil() {}

  public static List<String> split(String path, String separator) {
    if (path == null || path.isEmpty()) {
      return List.of();
    }

    // Optimize for single character separators (most common case)
    if (separator.length() == 1) {
      return splitBySingleChar(path, separator.charAt(0));
    }

    // Fall back to multi-character separator handling
    return splitByMultiChar(path, separator);
  }

  private static List<String> splitBySingleChar(String path, char separator) {
    List<String> parts = new ArrayList<>();
    int start = 0;

    for (int i = 0; i < path.length(); i++) {
      if (path.charAt(i) == separator) {
        parts.add(path.substring(start, i));
        start = i + 1;
      }
    }

    // Add the last part
    parts.add(path.substring(start));
    return parts;
  }

  private static List<String> splitByMultiChar(String path, String separator) {
    List<String> parts = new ArrayList<>();
    int start = 0;
    int index = path.indexOf(separator, start);

    while (index >= 0) {
      parts.add(path.substring(start, index));
      start = index + separator.length();
      index = path.indexOf(separator, start);
    }

    // Add the last part
    parts.add(path.substring(start));
    return parts;
  }

  public static String join(List<String> parts, String separator) {
    return String.join(separator, parts);
  }

  public static boolean isPrefixOf(String prefix, String path, String separator) {
    if (prefix.equals(path)) {
      return true;
    }
    return path.startsWith(prefix + separator);
  }
}
