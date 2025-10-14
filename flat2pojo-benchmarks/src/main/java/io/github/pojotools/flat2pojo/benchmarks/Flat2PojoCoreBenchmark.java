package io.github.pojotools.flat2pojo.benchmarks;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pojotools.flat2pojo.core.api.Flat2Pojo;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import io.github.pojotools.flat2pojo.core.impl.Flat2PojoCore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Focused benchmark testing Flat2PojoCore entry points with realistic data volumes. Tests the main
 * API methods: convertAll() and convertOptional() with 2.5k, 5k, and 10k record volumes.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
public class Flat2PojoCoreBenchmark {

  private Flat2Pojo converter;
  private MappingConfig simpleConfig;
  private MappingConfig nestedListConfig;
  private MappingConfig complexConfig;

  private List<Map<String, Object>> simple2500;
  private List<Map<String, Object>> simple5000;
  private List<Map<String, Object>> simple10000;

  private List<Map<String, Object>> nestedList2500;
  private List<Map<String, Object>> nestedList5000;
  private List<Map<String, Object>> nestedList10000;

  private List<Map<String, Object>> complex2500;
  private List<Map<String, Object>> complex5000;
  private List<Map<String, Object>> complex10000;

  @Setup
  public void setup() {
    converter = new Flat2PojoCore(new ObjectMapper());

    // Simple config: flat fields only
    simpleConfig = MappingConfig.builder().separator("/").build();

    // Nested list config: single level list with grouping
    nestedListConfig =
        MappingConfig.builder()
            .separator("/")
            .rootKeys(List.of("orderId"))
            .addLists(
                new MappingConfig.ListRule(
                    "items",
                    List.of("productId"),
                    List.of(
                        new MappingConfig.OrderBy(
                            "price", MappingConfig.OrderDirection.asc, MappingConfig.Nulls.last)),
                    true,
                    MappingConfig.ConflictPolicy.error))
            .build();

    // Complex config: nested lists with multiple levels
    complexConfig =
        MappingConfig.builder()
            .separator("/")
            .rootKeys(List.of("customerId"))
            .addLists(
                new MappingConfig.ListRule(
                    "orders",
                    List.of("orderId"),
                    List.of(
                        new MappingConfig.OrderBy(
                            "orderDate",
                            MappingConfig.OrderDirection.desc,
                            MappingConfig.Nulls.last)),
                    true,
                    MappingConfig.ConflictPolicy.error))
            .addLists(
                new MappingConfig.ListRule(
                    "orders/items",
                    List.of("orderId", "productId"),
                    List.of(
                        new MappingConfig.OrderBy(
                            "quantity",
                            MappingConfig.OrderDirection.desc,
                            MappingConfig.Nulls.last)),
                    true,
                    MappingConfig.ConflictPolicy.lastWriteWins))
            .build();

    // Generate datasets
    simple2500 = generateSimpleDataset(2500);
    simple5000 = generateSimpleDataset(5000);
    simple10000 = generateSimpleDataset(10000);

    nestedList2500 = generateNestedListDataset(2500);
    nestedList5000 = generateNestedListDataset(5000);
    nestedList10000 = generateNestedListDataset(10000);

    complex2500 = generateComplexDataset(2500);
    complex5000 = generateComplexDataset(5000);
    complex10000 = generateComplexDataset(10000);
  }

  // ==================== Simple Flat Structure Benchmarks ====================

  @Benchmark
  public void convertAll_Simple_2500(Blackhole bh) {
    List<Map> results = converter.convertAll(simple2500, Map.class, simpleConfig);
    bh.consume(results);
  }

  @Benchmark
  public void convertAll_Simple_5000(Blackhole bh) {
    List<Map> results = converter.convertAll(simple5000, Map.class, simpleConfig);
    bh.consume(results);
  }

  @Benchmark
  public void convertAll_Simple_10000(Blackhole bh) {
    List<Map> results = converter.convertAll(simple10000, Map.class, simpleConfig);
    bh.consume(results);
  }

  // ==================== Nested List with Grouping Benchmarks ====================

  @Benchmark
  public void convertAll_NestedList_2500(Blackhole bh) {
    List<Map> results = converter.convertAll(nestedList2500, Map.class, nestedListConfig);
    bh.consume(results);
  }

  @Benchmark
  public void convertAll_NestedList_5000(Blackhole bh) {
    List<Map> results = converter.convertAll(nestedList5000, Map.class, nestedListConfig);
    bh.consume(results);
  }

  @Benchmark
  public void convertAll_NestedList_10000(Blackhole bh) {
    List<Map> results = converter.convertAll(nestedList10000, Map.class, nestedListConfig);
    bh.consume(results);
  }

  // ==================== Complex Multi-Level Lists Benchmarks ====================

  @Benchmark
  public void convertAll_Complex_2500(Blackhole bh) {
    List<Map> results = converter.convertAll(complex2500, Map.class, complexConfig);
    bh.consume(results);
  }

  @Benchmark
  public void convertAll_Complex_5000(Blackhole bh) {
    List<Map> results = converter.convertAll(complex5000, Map.class, complexConfig);
    bh.consume(results);
  }

  @Benchmark
  public void convertAll_Complex_10000(Blackhole bh) {
    List<Map> results = converter.convertAll(complex10000, Map.class, complexConfig);
    bh.consume(results);
  }

  // ==================== Data Generation Methods ====================

  /**
   * Generates simple flat records with nested paths but no lists. Example: user/name, user/email,
   * user/profile/age
   */
  private List<Map<String, Object>> generateSimpleDataset(int recordCount) {
    List<Map<String, Object>> data = new ArrayList<>(recordCount);

    for (int i = 1; i <= recordCount; i++) {
      Map<String, Object> row = new HashMap<>();
      row.put("user/id", i);
      row.put("user/name", "User" + i);
      row.put("user/email", "user" + i + "@example.com");
      row.put("user/profile/age", 20 + (i % 50));
      row.put("user/profile/country", "Country" + (i % 10));
      row.put("user/settings/theme", i % 2 == 0 ? "dark" : "light");
      row.put("user/settings/notifications", i % 3 == 0);
      data.add(row);
    }

    return data;
  }

  /**
   * Generates records with a single level list requiring grouping. Simulates order with multiple
   * items (5 items per order).
   */
  private List<Map<String, Object>> generateNestedListDataset(int recordCount) {
    List<Map<String, Object>> data = new ArrayList<>(recordCount);
    int ordersCount = recordCount / 5; // 5 items per order

    for (int orderId = 1; orderId <= ordersCount; orderId++) {
      for (int itemNum = 1; itemNum <= 5; itemNum++) {
        Map<String, Object> row = new HashMap<>();
        row.put("orderId", orderId);
        row.put("orderDate", "2023-" + String.format("%02d", (orderId % 12) + 1) + "-15");
        row.put("status", orderId % 3 == 0 ? "delivered" : "shipped");

        row.put("productId", (itemNum * 100) + (orderId % 50));
        row.put("productName", "Product " + ((itemNum * 100) + (orderId % 50)));
        row.put("category", "Category" + (itemNum % 5));
        row.put("price", (itemNum * 25.0) + (orderId % 100));
        row.put("quantity", itemNum);

        data.add(row);
      }
    }

    return data;
  }

  /**
   * Generates records with multi-level nested lists requiring complex grouping. Simulates customers
   * with orders, each order having multiple items. Structure: customers -> orders (3 per customer)
   * -> items (5 per order)
   */
  private List<Map<String, Object>> generateComplexDataset(int recordCount) {
    List<Map<String, Object>> data = new ArrayList<>(recordCount);
    int customersCount = recordCount / 15; // 3 orders × 5 items = 15 rows per customer

    for (int customerId = 1; customerId <= customersCount; customerId++) {
      for (int orderNum = 1; orderNum <= 3; orderNum++) {
        int orderId = (customerId * 100) + orderNum;

        for (int itemNum = 1; itemNum <= 5; itemNum++) {
          Map<String, Object> row = new HashMap<>();

          // Customer data
          row.put("customerId", customerId);
          row.put("customer/name", "Customer" + customerId);
          row.put("customer/email", "customer" + customerId + "@example.com");
          row.put("customer/tier", customerId % 3 == 0 ? "premium" : "standard");

          // Order data
          row.put("orderId", orderId);
          row.put("orderDate", "2023-" + String.format("%02d", (orderNum % 12) + 1) + "-15");
          row.put("orderStatus", orderNum % 3 == 0 ? "delivered" : "processing");
          row.put("orderTotal", (itemNum * 50.0) + (customerId % 200));

          // Item data
          row.put("productId", (itemNum * 1000) + (customerId % 100));
          row.put("productName", "Product " + ((itemNum * 1000) + (customerId % 100)));
          row.put("category", "Category" + (itemNum % 5));
          row.put("price", (itemNum * 25.0) + (customerId % 100));
          row.put("quantity", itemNum);
          row.put("discount", itemNum % 3 == 0 ? 0.1 : 0.0);

          data.add(row);
        }
      }
    }

    return data;
  }
}
