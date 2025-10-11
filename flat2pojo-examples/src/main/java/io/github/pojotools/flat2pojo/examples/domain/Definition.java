package io.github.pojotools.flat2pojo.examples.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
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
  Integer priority();

  @Nullable
  List<String> tags();

  @Nullable
  Metadata metadata();

  @Nullable
  Schedule schedule();

  @Nullable
  Audit audit();

  @Nullable
  Tracker tracker();

  @Nullable
  List<Module> modules();
}
