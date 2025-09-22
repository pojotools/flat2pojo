package io.flat2pojo.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class JacksonAdapter {
    private final ObjectMapper om;

    public JacksonAdapter(ObjectMapper om) {
        this.om = om;
    }

    public <T> T treeToValue(JsonNode node, Class<T> type) {
        try {
            return om.treeToValue(node, type);
        } catch (Exception e) {
            throw new IllegalStateException("Jackson mapping failed", e);
        }
    }

    public static ObjectMapper defaultObjectMapper() {
        return JsonMapper.builder()
          // ✅ Register module for java.time types
          .addModule(new JavaTimeModule())

          // ✅ ISO-8601 instead of timestamps for dates
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

          // ✅ Allow case-insensitive enums
          .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)

          // ✅ Ignore unknown fields instead of failing
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

          .build();
    }
}
