package io.flat2pojo.core.api;

public final class Flat2PojoException extends RuntimeException {
  public Flat2PojoException(String msg) {
    super(msg);
  }

  public Flat2PojoException(String msg, Throwable t) {
    super(msg, t);
  }
}
