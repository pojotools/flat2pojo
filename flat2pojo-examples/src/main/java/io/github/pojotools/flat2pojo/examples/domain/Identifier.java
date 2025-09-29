package io.github.pojotools.flat2pojo.examples.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableIdentifier.class)
@JsonDeserialize(as = ImmutableIdentifier.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface Identifier {
  String identifier();

  @Nullable
  String identifierType(); // keep String (enums optional)

  @Nullable
  String source();
}
