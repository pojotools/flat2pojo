package io.flat2pojo.benchmarks;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flat2pojo.core.api.Flat2Pojo;
import io.flat2pojo.core.config.MappingConfig;
import io.flat2pojo.core.impl.Flat2PojoCore;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.GCProfiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1, jvmArgs = {"-Xms2g", "-Xmx2g"})
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
public class MemoryAllocationBenchmark {

  private Flat2Pojo converter;
  private MappingConfig simpleConfig;
  private MappingConfig listConfig;
  private List<Map<String, Object>> smallDataset;
  private List<Map<String, Object>> mediumDataset;
  private List<Map<String, Object>> largeDataset;

  @Setup
  public void setup() {
    converter = new Flat2PojoCore(new ObjectMapper());

    simpleConfig = MappingConfig.builder().build();

    listConfig = MappingConfig.builder()
        .addLists(new MappingConfig.ListRule(
            "orders",
            List.of("customerId"),
            List.of(),
            true,
            MappingConfig.ConflictPolicy.error
        ))
        .build();

    smallDataset = generateDataset(10);
    mediumDataset = generateDataset(100);
    largeDataset = generateDataset(1000);
  }

  private List<Map<String, Object>> generateDataset(int size) {
    List<Map<String, Object>> dataset = new ArrayList<>(size);

    for (int i = 0; i < size; i++) {
      Map<String, Object> row = new HashMap<>();
      row.put("id", i);
      row.put("user/name", "User" + i);
      row.put("user/email", "user" + i + "@example.com");
      row.put("user/profile/age", 20 + (i % 50));
      row.put("user/profile/department", "Dept" + (i % 5));
      row.put("user/settings/theme", i % 2 == 0 ? "dark" : "light");
      row.put("user/settings/notifications/email", i % 3 == 0);
      row.put("user/settings/notifications/sms", i % 4 == 0);
      row.put("metadata/created", "2023-01-" + String.format("%02d", (i % 28) + 1));
      row.put("metadata/source", "system");
      dataset.add(row);
    }

    return dataset;
  }

  @Benchmark
  public void convertSmallDatasetSimple(Blackhole bh) {
    List<Map> results = converter.convertAll(smallDataset, Map.class, simpleConfig);
    bh.consume(results);
  }

  @Benchmark
  public void convertMediumDatasetSimple(Blackhole bh) {
    List<Map> results = converter.convertAll(mediumDataset, Map.class, simpleConfig);
    bh.consume(results);
  }

  @Benchmark
  public void convertLargeDatasetSimple(Blackhole bh) {
    List<Map> results = converter.convertAll(largeDataset, Map.class, simpleConfig);
    bh.consume(results);
  }

  @Benchmark
  public void convertSmallDatasetWithLists(Blackhole bh) {
    List<Map> results = converter.convertAll(smallDataset, Map.class, listConfig);
    bh.consume(results);
  }

  @Benchmark
  public void convertMediumDatasetWithLists(Blackhole bh) {
    List<Map> results = converter.convertAll(mediumDataset, Map.class, listConfig);
    bh.consume(results);
  }

  @Benchmark
  public void singleRowConversion(Blackhole bh) {
    Map<String, Object> singleRow = smallDataset.get(0);
    Map result = converter.convert(singleRow, Map.class, simpleConfig);
    bh.consume(result);
  }

  @Benchmark
  public void configurationCaching(Blackhole bh) {
    MappingConfig config = MappingConfig.builder()
        .separator("/")
        .allowSparseRows(true)
        .addLists(new MappingConfig.ListRule(
            "items",
            List.of("id"),
            List.of(),
            true,
            MappingConfig.ConflictPolicy.error
        ))
        .build();
    bh.consume(config);
  }

  public static class ComplexPojo {
    public int id;
    public UserData user;
    public MetaData metadata;

    public static class UserData {
      public String name;
      public String email;
      public ProfileData profile;
      public SettingsData settings;

      public static class ProfileData {
        public int age;
        public String department;
      }

      public static class SettingsData {
        public String theme;
        public NotificationData notifications;

        public static class NotificationData {
          public boolean email;
          public boolean sms;
        }
      }
    }

    public static class MetaData {
      public String created;
      public String source;
    }
  }
}