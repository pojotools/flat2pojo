package io.flat2pojo.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flat2pojo.core.api.Flat2Pojo;
import io.flat2pojo.core.impl.Flat2PojoCore;

public final class Flat2PojoFactory {
  private Flat2PojoFactory(){}

  public static Flat2Pojo defaultMapper(ObjectMapper om) {
    return new Flat2PojoCore(om);
  }
}
