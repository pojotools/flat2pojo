package io.github.kyran121.flat2pojo.examples;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kyran121.flat2pojo.core.api.Flat2Pojo;
import io.github.kyran121.flat2pojo.core.config.MappingConfig;
import io.github.kyran121.flat2pojo.core.config.MappingConfigLoader;
import io.github.kyran121.flat2pojo.jackson.Flat2PojoFactory;
import io.github.kyran121.flat2pojo.jackson.JacksonAdapter;

final class TestSupport {
  static ObjectMapper om() {
    return JacksonAdapter.defaultObjectMapper();
  }

  static Flat2Pojo mapper(ObjectMapper om) {
    return Flat2PojoFactory.defaultMapper(om);
  }

  static MappingConfig cfgFromYaml(String yaml) {
    return MappingConfigLoader.fromYaml(yaml);
  }

  static <T> T first(java.util.List<T> list) {
    return list.getFirst();
  }
}
