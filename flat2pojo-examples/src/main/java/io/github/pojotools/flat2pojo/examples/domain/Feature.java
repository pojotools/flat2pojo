package io.github.pojotools.flat2pojo.examples.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableFeature.class)
@JsonDeserialize(as = ImmutableFeature.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface Feature {
  @Nullable
  String name();

  @Nullable
  Boolean enabled();

  @Nullable
  String version();
}
