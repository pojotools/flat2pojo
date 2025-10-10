package io.github.pojotools.flat2pojo.core.engine;

import java.util.List;
import java.util.Objects;
import lombok.ToString;

@ToString(of = "values")
public final class CompositeKey {
  private final List<Object> values;
  private final int hash;

  public CompositeKey(final List<Object> values) {
    this.values = List.copyOf(values);
    this.hash = Objects.hash(this.values);
  }

  @Override
  public boolean equals(final Object o) {
    return this == o || (o instanceof CompositeKey k && values.equals(k.values));
  }

  @Override
  public int hashCode() {
    return hash;
  }
}
