package io.flat2pojo.spi;

/** Optional hook to report unknown paths, config warnings, etc. */
public interface Reporter {
  void warn(String message);
}
