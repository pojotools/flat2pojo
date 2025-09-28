package io.github.kyran121.flat2pojo.benchmarks;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kyran121.flat2pojo.core.api.Flat2Pojo;
import io.github.kyran121.flat2pojo.core.config.MappingConfig;
import io.github.kyran121.flat2pojo.core.impl.Flat2PojoCore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 2, time = 3)
@Measurement(iterations = 3, time = 5)
public class RealisticDatasetBenchmark {

  private Flat2Pojo converter;
  private MappingConfig ecommerceConfig;
  private MappingConfig crmConfig;
  private MappingConfig analyticsConfig;
  private List<Map<String, Object>> ecommerceData;
  private List<Map<String, Object>> crmData;
  private List<Map<String, Object>> analyticsData;

  @Setup
  public void setup() {
    converter = new Flat2PojoCore(new ObjectMapper());

    ecommerceConfig =
        MappingConfig.builder()
            .separator("/")
            .addLists(
                new MappingConfig.ListRule(
                    "orders",
                    List.of("customerId"),
                    List.of(
                        new MappingConfig.OrderBy(
                            "orderDate", MappingConfig.Direction.desc, MappingConfig.Nulls.last)),
                    true,
                    MappingConfig.ConflictPolicy.error))
            .addLists(
                new MappingConfig.ListRule(
                    "orders/items",
                    List.of("customerId", "orderId", "productId"),
                    List.of(),
                    true,
                    MappingConfig.ConflictPolicy.lastWriteWins))
            .build();

    crmConfig =
        MappingConfig.builder()
            .separator("/")
            .addLists(
                new MappingConfig.ListRule(
                    "contacts",
                    List.of("companyId"),
                    List.of(
                        new MappingConfig.OrderBy(
                            "lastName", MappingConfig.Direction.asc, MappingConfig.Nulls.last)),
                    true,
                    MappingConfig.ConflictPolicy.merge))
            .addLists(
                new MappingConfig.ListRule(
                    "contacts/activities",
                    List.of("companyId", "contactId"),
                    List.of(
                        new MappingConfig.OrderBy(
                            "timestamp", MappingConfig.Direction.desc, MappingConfig.Nulls.last)),
                    true,
                    MappingConfig.ConflictPolicy.lastWriteWins))
            .build();

    analyticsConfig =
        MappingConfig.builder()
            .separator("/")
            .addLists(
                new MappingConfig.ListRule(
                    "events",
                    List.of("sessionId"),
                    List.of(
                        new MappingConfig.OrderBy(
                            "timestamp", MappingConfig.Direction.asc, MappingConfig.Nulls.first)),
                    false,
                    MappingConfig.ConflictPolicy.error))
            .build();

    ecommerceData = generateEcommerceDataset();
    crmData = generateCrmDataset();
    analyticsData = generateAnalyticsDataset();
  }

  private List<Map<String, Object>> generateEcommerceDataset() {
    List<Map<String, Object>> data = new ArrayList<>();

    for (int customerId = 1; customerId <= 50; customerId++) {
      for (int orderNum = 1; orderNum <= 3; orderNum++) {
        int orderId = (customerId * 100) + orderNum;

        for (int itemNum = 1; itemNum <= 5; itemNum++) {
          Map<String, Object> row = new HashMap<>();
          row.put("customerId", customerId);
          row.put("customer/name", "Customer " + customerId);
          row.put("customer/email", "customer" + customerId + "@example.com");
          row.put("customer/tier", customerId % 3 == 0 ? "premium" : "standard");

          row.put("orderId", orderId);
          row.put("orderDate", "2023-" + String.format("%02d", (orderNum % 12) + 1) + "-15");
          row.put("orderStatus", orderNum % 4 == 0 ? "delivered" : "shipped");

          row.put("productId", (itemNum * 1000) + (customerId % 20));
          row.put("product/name", "Product " + ((itemNum * 1000) + (customerId % 20)));
          row.put("product/category", "Category " + (itemNum % 5));
          row.put("product/price", (itemNum * 25.0) + (customerId % 100));
          row.put("quantity", itemNum);
          row.put("discount", itemNum % 3 == 0 ? 0.1 : 0.0);

          data.add(row);
        }
      }
    }

    return data;
  }

  private List<Map<String, Object>> generateCrmDataset() {
    List<Map<String, Object>> data = new ArrayList<>();

    for (int companyId = 1; companyId <= 20; companyId++) {
      for (int contactNum = 1; contactNum <= 8; contactNum++) {
        int contactId = (companyId * 100) + contactNum;

        for (int activityNum = 1; activityNum <= 12; activityNum++) {
          Map<String, Object> row = new HashMap<>();
          row.put("companyId", companyId);
          row.put("company/name", "Company " + companyId);
          row.put("company/industry", "Industry " + (companyId % 5));
          row.put("company/size", companyId % 3 == 0 ? "large" : "medium");

          row.put("contactId", contactId);
          row.put("contact/firstName", "FirstName" + contactId);
          row.put("contact/lastName", "LastName" + contactId);
          row.put("contact/title", "Title " + (contactNum % 4));
          row.put("contact/email", "contact" + contactId + "@company" + companyId + ".com");
          row.put("contact/phone", "+1-555-" + String.format("%04d", contactId));

          row.put("activityId", (contactId * 100) + activityNum);
          row.put(
              "activity/type",
              activityNum % 4 == 0 ? "call" : activityNum % 4 == 1 ? "email" : "meeting");
          row.put("activity/subject", "Activity " + activityNum + " for " + contactId);
          row.put(
              "activity/timestamp",
              "2023-"
                  + String.format("%02d", (activityNum % 12) + 1)
                  + "-"
                  + String.format("%02d", (activityNum % 28) + 1));
          row.put("activity/outcome", activityNum % 3 == 0 ? "successful" : "pending");

          data.add(row);
        }
      }
    }

    return data;
  }

  private List<Map<String, Object>> generateAnalyticsDataset() {
    List<Map<String, Object>> data = new ArrayList<>();

    for (int sessionId = 1; sessionId <= 100; sessionId++) {
      for (int eventNum = 1; eventNum <= 25; eventNum++) {
        Map<String, Object> row = new HashMap<>();
        row.put("sessionId", "session-" + sessionId);
        row.put("userId", "user-" + (sessionId % 30));
        row.put("timestamp", System.currentTimeMillis() + (eventNum * 1000));

        row.put(
            "event/type",
            eventNum % 5 == 0
                ? "page_view"
                : eventNum % 5 == 1 ? "click" : eventNum % 5 == 2 ? "scroll" : "form_submit");
        row.put("event/category", "Category" + (eventNum % 3));
        row.put("event/value", eventNum * 10);

        row.put("page/url", "/page/" + (eventNum % 10));
        row.put("page/title", "Page " + (eventNum % 10));
        row.put("page/loadTime", 100 + (eventNum % 500));

        row.put(
            "device/type",
            sessionId % 3 == 0 ? "mobile" : sessionId % 3 == 1 ? "tablet" : "desktop");
        row.put(
            "device/os",
            sessionId % 4 == 0
                ? "iOS"
                : sessionId % 4 == 1 ? "Android" : sessionId % 4 == 2 ? "Windows" : "macOS");
        row.put(
            "device/browser",
            sessionId % 3 == 0 ? "Chrome" : sessionId % 3 == 1 ? "Safari" : "Firefox");

        data.add(row);
      }
    }

    return data;
  }

  @Benchmark
  public void ecommerceDataProcessing(Blackhole bh) {
    List<Map> results = converter.convertAll(ecommerceData, Map.class, ecommerceConfig);
    bh.consume(results);
  }

  @Benchmark
  public void crmDataProcessing(Blackhole bh) {
    List<Map> results = converter.convertAll(crmData, Map.class, crmConfig);
    bh.consume(results);
  }

  @Benchmark
  public void analyticsDataProcessing(Blackhole bh) {
    List<Map> results = converter.convertAll(analyticsData, Map.class, analyticsConfig);
    bh.consume(results);
  }

  @Benchmark
  public void mixedWorkloadProcessing(Blackhole bh) {
    List<Map> ecommerceResults =
        converter.convertAll(ecommerceData.subList(0, 100), Map.class, ecommerceConfig);
    List<Map> crmResults = converter.convertAll(crmData.subList(0, 100), Map.class, crmConfig);
    List<Map> analyticsResults =
        converter.convertAll(analyticsData.subList(0, 100), Map.class, analyticsConfig);

    bh.consume(ecommerceResults);
    bh.consume(crmResults);
    bh.consume(analyticsResults);
  }
}
