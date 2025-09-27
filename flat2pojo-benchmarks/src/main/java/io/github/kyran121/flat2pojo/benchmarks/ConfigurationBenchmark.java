package io.github.kyran121.flat2pojo.benchmarks;

import io.github.kyran121.flat2pojo.core.config.MappingConfig;
import io.github.kyran121.flat2pojo.core.config.MappingConfigLoader;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class ConfigurationBenchmark {

  private String simpleYaml;
  private String complexYaml;
  private String enterpriseYaml;
  private MappingConfig prebuiltConfig;

  @Setup
  public void setup() {
    simpleYaml = """
        separator: "/"
        allowSparseRows: true
        """;

    complexYaml = """
        separator: "/"
        allowSparseRows: true
        rootKeys:
          - "tenantId"
          - "regionId"
        lists:
          - path: "orders"
            keyPaths: ["customerId"]
            orderBy:
              - path: "orderDate"
                direction: "desc"
                nulls: "last"
            dedupe: true
            onConflict: "error"
          - path: "orders/items"
            keyPaths: ["customerId", "orderId", "productId"]
            dedupe: true
            onConflict: "lastWriteWins"
        primitives:
          - path: "tags"
            split:
              delimiter: ","
              trim: true
        nullPolicy:
          blanksAsNulls: true
        """;

    enterpriseYaml = """
        separator: "/"
        allowSparseRows: false
        rootKeys:
          - "organizationId"
          - "departmentId"
          - "projectId"
        lists:
          - path: "employees"
            keyPaths: ["organizationId", "departmentId"]
            orderBy:
              - path: "lastName"
                direction: "asc"
                nulls: "last"
              - path: "firstName"
                direction: "asc"
                nulls: "last"
            dedupe: true
            onConflict: "merge"
          - path: "employees/projects"
            keyPaths: ["organizationId", "departmentId", "employeeId"]
            orderBy:
              - path: "startDate"
                direction: "desc"
                nulls: "first"
            dedupe: true
            onConflict: "lastWriteWins"
          - path: "employees/projects/tasks"
            keyPaths: ["organizationId", "departmentId", "employeeId", "projectId"]
            orderBy:
              - path: "priority"
                direction: "desc"
                nulls: "last"
              - path: "dueDate"
                direction: "asc"
                nulls: "last"
            dedupe: false
            onConflict: "error"
          - path: "employees/skills"
            keyPaths: ["organizationId", "departmentId", "employeeId"]
            orderBy:
              - path: "proficiency"
                direction: "desc"
                nulls: "last"
            dedupe: true
            onConflict: "firstWriteWins"
        primitives:
          - path: "skills/tags"
            split:
              delimiter: ","
              trim: true
          - path: "certifications"
            split:
              delimiter: ";"
              trim: true
          - path: "languages"
            split:
              delimiter: "|"
              trim: false
        nullPolicy:
          blanksAsNulls: true
        """;

    prebuiltConfig = MappingConfig.builder()
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
  }

  @Benchmark
  public void parseSimpleYaml(Blackhole bh) {
    MappingConfig config = MappingConfigLoader.fromYaml(simpleYaml);
    bh.consume(config);
  }

  @Benchmark
  public void parseComplexYaml(Blackhole bh) {
    MappingConfig config = MappingConfigLoader.fromYaml(complexYaml);
    bh.consume(config);
  }

  @Benchmark
  public void parseEnterpriseYaml(Blackhole bh) {
    MappingConfig config = MappingConfigLoader.fromYaml(enterpriseYaml);
    bh.consume(config);
  }

  @Benchmark
  public void buildConfigProgrammatically(Blackhole bh) {
    MappingConfig config = MappingConfig.builder()
        .separator("/")
        .allowSparseRows(true)
        .addRootKeys("tenantId")
        .addRootKeys("regionId")
        .addLists(new MappingConfig.ListRule(
            "orders",
            List.of("customerId"),
            List.of(new MappingConfig.OrderBy("orderDate", MappingConfig.Direction.desc, MappingConfig.Nulls.last)),
            true,
            MappingConfig.ConflictPolicy.error
        ))
        .addLists(new MappingConfig.ListRule(
            "orders/items",
            List.of("customerId", "orderId", "productId"),
            List.of(),
            true,
            MappingConfig.ConflictPolicy.lastWriteWins
        ))
        .addPrimitives(new MappingConfig.PrimitiveSplitRule("tags", ",", true))
        .nullPolicy(new MappingConfig.NullPolicy(true))
        .build();
    bh.consume(config);
  }

  @Benchmark
  public void validateHierarchy(Blackhole bh) {
    MappingConfigLoader.validateHierarchy(prebuiltConfig);
    bh.consume("validated");
  }

  @Benchmark
  public void accessDerivedFields(Blackhole bh) {
    char separatorChar = prebuiltConfig.separatorChar();
    var listPaths = prebuiltConfig.listPaths();
    var childPrefixes = prebuiltConfig.getChildListPrefixes("items");

    bh.consume(separatorChar);
    bh.consume(listPaths);
    bh.consume(childPrefixes);
  }

  @Benchmark
  public void createImmutableConfig(Blackhole bh) {
    MappingConfig config = MappingConfig.builder()
        .separator(".")
        .allowSparseRows(false)
        .build();
    bh.consume(config);
  }

  @Benchmark
  public void parseEmptyYaml(Blackhole bh) {
    MappingConfig config = MappingConfigLoader.fromYaml("");
    bh.consume(config);
  }

  @Benchmark
  public void parseNullYaml(Blackhole bh) {
    MappingConfig config = MappingConfigLoader.fromYaml(null);
    bh.consume(config);
  }
}