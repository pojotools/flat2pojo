package io.github.kyran121.flat2pojo.examples.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableDefinition.class)
@JsonDeserialize(as = ImmutableDefinition.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface Definition {
  Identifier id();

  @Nullable
  String name();

  @Nullable
  Schedule schedule();

  @Nullable
  Audit audit();

  @Nullable
  Tracker tracker();
}
