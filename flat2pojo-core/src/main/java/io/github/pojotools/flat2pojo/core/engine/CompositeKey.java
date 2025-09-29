package io.github.pojotools.flat2pojo.core.engine;

import java.util.*;

public final class CompositeKey {
  private final List<Object> values;
  private final int hash;

  public CompositeKey(List<Object> values) {
    this.values = List.copyOf(values);
    this.hash = Objects.hash(this.values);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CompositeKey k)) {
      return false;
    }
    return values.equals(k.values);
  }

  @Override
  public int hashCode() {
    return hash;
  }

  @Override
  public String toString() {
    return values.toString();
  }
}
