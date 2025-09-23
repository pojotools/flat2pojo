package io.flat2pojo.benchmarks;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flat2pojo.core.api.Flat2Pojo;
import io.flat2pojo.core.config.MappingConfig;
import io.flat2pojo.core.impl.Flat2PojoCore;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, warmups = 1)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 3)
public class PerformanceRegressionBenchmark {

  private Flat2Pojo converter;
  private MappingConfig standardConfig;
  private List<Map<String, Object>> regressionDataset;
  private Map<String, Object> singleComplexRow;

  @Setup
  public void setup() {
    converter = new Flat2PojoCore(new ObjectMapper());

    standardConfig = MappingConfig.builder()
        .separator("/")
        .allowSparseRows(true)
        .addRootKeys("tenantId")
        .addLists(new MappingConfig.ListRule(
            "users",
            List.of("tenantId"),
            List.of(new MappingConfig.OrderBy("userId", MappingConfig.Direction.asc, MappingConfig.Nulls.last)),
            true,
            MappingConfig.ConflictPolicy.error
        ))
        .addLists(new MappingConfig.ListRule(
            "users/orders",
            List.of("tenantId", "userId"),
            List.of(new MappingConfig.OrderBy("orderDate", MappingConfig.Direction.desc, MappingConfig.Nulls.last)),
            true,
            MappingConfig.ConflictPolicy.lastWriteWins
        ))
        .addLists(new MappingConfig.ListRule(
            "users/orders/items",
            List.of("tenantId", "userId", "orderId"),
            List.of(),
            true,
            MappingConfig.ConflictPolicy.merge
        ))
        .addPrimitives(new MappingConfig.PrimitiveSplitRule("tags", ",", true))
        .addPrimitives(new MappingConfig.PrimitiveSplitRule("categories", ";", true))
        .nullPolicy(new MappingConfig.NullPolicy(true))
        .build();

    regressionDataset = generateRegressionDataset();
    singleComplexRow = generateComplexRow();
  }

  private List<Map<String, Object>> generateRegressionDataset() {
    List<Map<String, Object>> dataset = new ArrayList<>();

    for (int tenantId = 1; tenantId <= 3; tenantId++) {
      for (int userId = 1; userId <= 20; userId++) {
        for (int orderId = 1; orderId <= 5; orderId++) {
          for (int itemId = 1; itemId <= 8; itemId++) {
            Map<String, Object> row = new HashMap<>();

            row.put("tenantId", tenantId);
            row.put("userId", userId);
            row.put("user/profile/firstName", "User" + userId);
            row.put("user/profile/lastName", "LastName" + userId);
            row.put("user/profile/email", "user" + userId + "@tenant" + tenantId + ".com");
            row.put("user/profile/age", 20 + (userId % 50));
            row.put("user/profile/department", "Department " + (userId % 5));
            row.put("user/preferences/theme", userId % 2 == 0 ? "dark" : "light");
            row.put("user/preferences/language", userId % 3 == 0 ? "en" : userId % 3 == 1 ? "es" : "fr");
            row.put("user/permissions/role", userId % 4 == 0 ? "admin" : "user");
            row.put("user/permissions/level", userId % 3 + 1);

            row.put("orderId", orderId);
            row.put("order/date", "2023-" + String.format("%02d", (orderId % 12) + 1) + "-15");
            row.put("order/status", orderId % 3 == 0 ? "completed" : orderId % 3 == 1 ? "pending" : "shipped");
            row.put("order/total", (orderId * 100.0) + (itemId * 25.0));
            row.put("order/currency", "USD");
            row.put("order/shipping/address/street", orderId + " Main St");
            row.put("order/shipping/address/city", "City" + (orderId % 10));
            row.put("order/shipping/address/state", "State" + (orderId % 5));
            row.put("order/shipping/address/zipCode", String.format("%05d", 10000 + (orderId * 100)));
            row.put("order/shipping/method", orderId % 2 == 0 ? "standard" : "express");

            row.put("itemId", itemId);
            row.put("item/productId", (itemId * 1000) + (userId % 100));
            row.put("item/name", "Product " + ((itemId * 1000) + (userId % 100)));
            row.put("item/description", "Description for product " + itemId);
            row.put("item/category", "Category " + (itemId % 5));
            row.put("item/subcategory", "Subcategory " + (itemId % 3));
            row.put("item/price", (itemId * 15.0) + (userId % 50));
            row.put("item/quantity", itemId % 5 + 1);
            row.put("item/discount", itemId % 4 == 0 ? 0.1 : 0.0);
            row.put("item/sku", "SKU-" + itemId + "-" + userId);
            row.put("item/weight", (itemId * 0.5) + 1.0);
            row.put("item/dimensions/length", itemId * 2);
            row.put("item/dimensions/width", itemId * 1.5);
            row.put("item/dimensions/height", itemId);

            row.put("tags", "tag1,tag2,tag" + (itemId % 3));
            row.put("categories", "cat1;cat2;cat" + (userId % 4));

            row.put("metadata/created", "2023-01-01T12:00:00Z");
            row.put("metadata/updated", "2023-06-15T10:30:00Z");
            row.put("metadata/source", "system");
            row.put("metadata/version", "1.0." + (itemId % 10));

            dataset.add(row);
          }
        }
      }
    }

    return dataset;
  }

  private Map<String, Object> generateComplexRow() {
    Map<String, Object> row = new HashMap<>();

    row.put("tenantId", 999);
    row.put("organization/id", 12345);
    row.put("organization/name", "Complex Organization Inc.");
    row.put("organization/address/street", "123 Enterprise Boulevard");
    row.put("organization/address/city", "Business City");
    row.put("organization/address/state", "Corporate State");
    row.put("organization/address/country", "United States");
    row.put("organization/contact/phone", "+1-800-COMPLEX");
    row.put("organization/contact/email", "contact@complex.org");
    row.put("organization/settings/timezone", "America/New_York");
    row.put("organization/settings/locale", "en_US");
    row.put("organization/settings/currency", "USD");

    for (int i = 1; i <= 20; i++) {
      row.put("department" + i + "/id", i);
      row.put("department" + i + "/name", "Department " + i);
      row.put("department" + i + "/manager/id", i * 100);
      row.put("department" + i + "/manager/name", "Manager " + i);
      row.put("department" + i + "/budget", i * 50000.0);
      row.put("department" + i + "/employees/count", i * 5);

      for (int j = 1; j <= 5; j++) {
        String empPrefix = "department" + i + "/employees/employee" + j;
        row.put(empPrefix + "/id", (i * 100) + j);
        row.put(empPrefix + "/name", "Employee " + j + " Dept " + i);
        row.put(empPrefix + "/position", "Position " + j);
        row.put(empPrefix + "/salary", 40000 + (j * 10000));
        row.put(empPrefix + "/skills", "skill1,skill2,skill" + j);
      }
    }

    return row;
  }

  @Benchmark
  public void regressionFullDataset(Blackhole bh) {
    List<Map> results = converter.convertAll(regressionDataset, Map.class, standardConfig);
    bh.consume(results);
  }

  @Benchmark
  public void regressionSingleComplexRow(Blackhole bh) {
    Map result = converter.convert(singleComplexRow, Map.class, standardConfig);
    bh.consume(result);
  }

  @Benchmark
  public void regressionSubsetProcessing(Blackhole bh) {
    List<Map<String, Object>> subset = regressionDataset.subList(0, Math.min(100, regressionDataset.size()));
    List<Map> results = converter.convertAll(subset, Map.class, standardConfig);
    bh.consume(results);
  }

  @Benchmark
  public void regressionStreamProcessing(Blackhole bh) {
    List<Map> results = converter.stream(
        regressionDataset.subList(0, Math.min(50, regressionDataset.size())).iterator(),
        Map.class,
        standardConfig
    ).toList();
    bh.consume(results);
  }

  @Benchmark
  public void regressionConfigValidation(Blackhole bh) {
    MappingConfig complexConfig = MappingConfig.builder()
        .separator("/")
        .allowSparseRows(true)
        .addRootKeys("tenantId")
        .addLists(new MappingConfig.ListRule(
            "users",
            List.of("tenantId"),
            List.of(),
            true,
            MappingConfig.ConflictPolicy.error
        ))
        .addLists(new MappingConfig.ListRule(
            "users/orders",
            List.of("tenantId", "userId"),
            List.of(),
            true,
            MappingConfig.ConflictPolicy.lastWriteWins
        ))
        .addLists(new MappingConfig.ListRule(
            "users/orders/items",
            List.of("tenantId", "userId", "orderId"),
            List.of(),
            true,
            MappingConfig.ConflictPolicy.merge
        ))
        .build();

    List<Map<String, Object>> sampleData = regressionDataset.subList(0, Math.min(10, regressionDataset.size()));
    List<Map> results = converter.convertAll(sampleData, Map.class, complexConfig);
    bh.consume(results);
  }

  public static class RegressionPojo {
    public int tenantId;
    public OrganizationData organization;
    public List<UserData> users;
    public Map<String, Object> metadata;

    public static class OrganizationData {
      public int id;
      public String name;
      public AddressData address;
      public ContactData contact;
      public SettingsData settings;
    }

    public static class AddressData {
      public String street;
      public String city;
      public String state;
      public String country;
    }

    public static class ContactData {
      public String phone;
      public String email;
    }

    public static class SettingsData {
      public String timezone;
      public String locale;
      public String currency;
    }

    public static class UserData {
      public int userId;
      public ProfileData profile;
      public PreferencesData preferences;
      public PermissionsData permissions;
      public List<OrderData> orders;
    }

    public static class ProfileData {
      public String firstName;
      public String lastName;
      public String email;
      public int age;
      public String department;
    }

    public static class PreferencesData {
      public String theme;
      public String language;
    }

    public static class PermissionsData {
      public String role;
      public int level;
    }

    public static class OrderData {
      public int orderId;
      public String date;
      public String status;
      public double total;
      public String currency;
      public ShippingData shipping;
      public List<ItemData> items;
    }

    public static class ShippingData {
      public AddressData address;
      public String method;
    }

    public static class ItemData {
      public int itemId;
      public int productId;
      public String name;
      public String description;
      public String category;
      public String subcategory;
      public double price;
      public int quantity;
      public double discount;
      public String sku;
      public double weight;
      public DimensionsData dimensions;
    }

    public static class DimensionsData {
      public double length;
      public double width;
      public double height;
    }
  }
}