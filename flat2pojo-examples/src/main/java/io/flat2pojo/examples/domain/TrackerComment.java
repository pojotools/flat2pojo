package io.flat2pojo.examples.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.immutables.value.Value;
import java.time.ZonedDateTime;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableTrackerComment.class)
@JsonDeserialize(as = ImmutableTrackerComment.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface TrackerComment {
  @Nullable String comment();
  @Nullable String loggedBy();
  ZonedDateTime loggedAt();
}
