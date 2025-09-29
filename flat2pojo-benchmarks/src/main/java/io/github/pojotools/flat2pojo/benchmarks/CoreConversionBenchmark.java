package io.github.pojotools.flat2pojo.benchmarks;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pojotools.flat2pojo.core.api.Flat2Pojo;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import io.github.pojotools.flat2pojo.core.impl.Flat2PojoCore;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class CoreConversionBenchmark {

  private Flat2Pojo converter;
  private MappingConfig config;
  private Map<String, Object> simpleRow;
  private Map<String, Object> nestedRow;
  private List<Map<String, Object>> multipleRows;

  @Setup
  public void setup() {
    converter = new Flat2PojoCore(new ObjectMapper());
    config = MappingConfig.builder().build();

    simpleRow =
        Map.of(
            "name", "John Doe",
            "age", 30,
            "email", "john@example.com");

    nestedRow =
        Map.of(
            "user/id", 123,
            "user/profile/firstName", "Jane",
            "user/profile/lastName", "Smith",
            "user/profile/contact/email", "jane@example.com",
            "user/profile/contact/phone", "+1234567890",
            "user/settings/theme", "dark",
            "user/settings/notifications", true);

    multipleRows =
        List.of(
            Map.of("id", 1, "name", "Alice", "department/name", "Engineering"),
            Map.of("id", 2, "name", "Bob", "department/name", "Marketing"),
            Map.of("id", 3, "name", "Charlie", "department/name", "Sales"));
  }

  @Benchmark
  public void convertSimpleMap(Blackhole bh) {
    Object result = converter.convert(simpleRow, Map.class, config);
    bh.consume(result);
  }

  @Benchmark
  public void convertNestedMap(Blackhole bh) {
    Object result = converter.convert(nestedRow, Map.class, config);
    bh.consume(result);
  }

  @Benchmark
  public void convertMultipleRows(Blackhole bh) {
    List<Map> results = converter.convertAll(multipleRows, Map.class, config);
    bh.consume(results);
  }

  @Benchmark
  public void convertToCustomPojo(Blackhole bh) {
    SimpleUser result = converter.convert(simpleRow, SimpleUser.class, config);
    bh.consume(result);
  }

  public static class SimpleUser {
    public String name;
    public int age;
    public String email;
  }
}
