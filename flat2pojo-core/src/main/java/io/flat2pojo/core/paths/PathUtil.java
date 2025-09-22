package io.flat2pojo.core.paths;

import java.util.*;

public final class PathUtil {
  private PathUtil() {}

  public static List<String> split(String path, String sep) {
    if (path == null || path.isEmpty()) return List.of();
    return List.of(path.split(java.util.regex.Pattern.quote(sep)));
  }

  public static String join(List<String> parts, String sep) {
    return String.join(sep, parts);
  }

  public static boolean isPrefixOf(String prefix, String path, String sep) {
    if (prefix.equals(path)) return true;
    return path.startsWith(prefix + sep);
  }
}
