package io.github.pojotools.flat2pojo.benchmarks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import io.github.pojotools.flat2pojo.core.engine.GroupingEngine;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class GroupingEngineBenchmark {

  private ObjectMapper objectMapper;
  private GroupingEngine groupingEngine;
  private MappingConfig listConfig;
  private MappingConfig.ListRule simpleListRule;
  private MappingConfig.ListRule nestedListRule;
  private ObjectNode rootNode;

  @Setup
  public void setup() {
    objectMapper = new ObjectMapper();

    simpleListRule =
        new MappingConfig.ListRule(
            "items", List.of("id"), List.of(), true, MappingConfig.ConflictPolicy.error);

    nestedListRule =
        new MappingConfig.ListRule(
            "users/orders",
            List.of("userId", "orderId"),
            List.of(
                new MappingConfig.OrderBy(
                    "timestamp", MappingConfig.OrderDirection.desc, MappingConfig.Nulls.last)),
            true,
            MappingConfig.ConflictPolicy.lastWriteWins);

    listConfig = MappingConfig.builder().addLists(simpleListRule).addLists(nestedListRule).build();

    groupingEngine = new GroupingEngine(objectMapper, listConfig);
    rootNode = objectMapper.createObjectNode();
  }

  @Benchmark
  public void upsertSimpleListElement(Blackhole bh) {
    ObjectNode result =
        groupingEngine.upsertListElementRelative(
            rootNode,
            "items",
            Map.of(
                "id",
                objectMapper.valueToTree("123"),
                "name",
                objectMapper.valueToTree("Product A")),
            simpleListRule);
    bh.consume(result);
  }

  @Benchmark
  public void upsertNestedListElement(Blackhole bh) {
    ObjectNode result =
        groupingEngine.upsertListElementRelative(
            rootNode,
            "users/orders",
            Map.of(
                "userId", objectMapper.valueToTree("user-456"),
                "orderId", objectMapper.valueToTree("order-789"),
                "timestamp", objectMapper.valueToTree("2023-01-01T12:00:00Z"),
                "amount", objectMapper.valueToTree("99.99")),
            nestedListRule);
    bh.consume(result);
  }

  @Benchmark
  public void finalizeArraysSimple(Blackhole bh) {
    ObjectNode testNode = objectMapper.createObjectNode();
    testNode.set("items", objectMapper.createArrayNode());
    groupingEngine.finalizeArrays(testNode);
    bh.consume(testNode);
  }

  @Benchmark
  public void finalizeArraysNested(Blackhole bh) {
    ObjectNode testNode = objectMapper.createObjectNode();
    ObjectNode users = objectMapper.createObjectNode();
    users.set("orders", objectMapper.createArrayNode());
    testNode.set("users", users);
    groupingEngine.finalizeArrays(testNode);
    bh.consume(testNode);
  }

  @Benchmark
  public void createGroupingEngineInstance(Blackhole bh) {
    GroupingEngine engine = new GroupingEngine(objectMapper, listConfig);
    bh.consume(engine);
  }
}
