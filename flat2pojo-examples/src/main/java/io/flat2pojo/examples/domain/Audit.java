package io.flat2pojo.examples.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.immutables.value.Value;
import java.time.ZonedDateTime;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableAudit.class)
@JsonDeserialize(as = ImmutableAudit.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface Audit {
  @Nullable String modifiedBy();
  @Nullable ZonedDateTime modifiedAt();
}
