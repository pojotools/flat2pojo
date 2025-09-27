package io.github.kyran121.flat2pojo.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kyran121.flat2pojo.core.api.Flat2Pojo;
import io.github.kyran121.flat2pojo.core.impl.Flat2PojoCore;

/**
 * Factory for creating {@link Flat2Pojo} instances with Jackson integration.
 *
 * <p>This factory provides the main entry point for obtaining converter instances
 * that work with Jackson {@link ObjectMapper} for POJO serialization and deserialization.
 */
public final class Flat2PojoFactory {
  private Flat2PojoFactory() {}

  /**
   * Creates a new {@link Flat2Pojo} converter using the provided Jackson ObjectMapper.
   *
   * <p>The ObjectMapper should be configured with appropriate modules and settings
   * for your use case. Consider using {@link JacksonAdapter#defaultObjectMapper()}
   * for a pre-configured mapper with sensible defaults.
   *
   * @param om the Jackson ObjectMapper to use for POJO conversion
   * @return a new Flat2Pojo converter instance
   */
  public static Flat2Pojo defaultMapper(final ObjectMapper om) {
    return new Flat2PojoCore(om);
  }

  /**
   * Creates a new {@link Flat2Pojo} converter using the default Jackson configuration.
   *
   * @return a new Flat2Pojo converter with optimized Jackson settings
   */
  public static Flat2Pojo create() {
    return new Flat2PojoCore(JacksonAdapter.defaultObjectMapper());
  }
}
