package io.github.pojotools.flat2pojo.core.api;

public final class Flat2PojoException extends RuntimeException {
  public Flat2PojoException(final String msg) {
    super(msg);
  }

  public Flat2PojoException(final String msg, final Throwable t) {
    super(msg, t);
  }
}
