package io.github.pojotools.flat2pojo.examples;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pojotools.flat2pojo.core.api.Flat2Pojo;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Tests demonstrating LEFT JOIN semantics with optional relationships.
 *
 * <p>Models the scenario: Orders LEFT JOIN Customers LEFT JOIN Addresses LEFT JOIN LineItems.
 *
 * <p>Key behaviors:
 * <ul>
 *   <li>Some orders have no customer (omitted fields prevent spurious objects)</li>
 *   <li>Some orders have a customer but no address (address fields omitted)</li>
 *   <li>Some orders have multiple line items; others have zero</li>
 *   <li>Absent keys in a row mean no object is created for that level</li>
 * </ul>
 *
 * <p>Relationship diagram:
 * <pre>
 * Order 1--? Customer 1--? Address
 *   |
 *   +-- LineItems 0..*
 * </pre>
 */
class LeftJoinCsvToPojoExampleTest {

  private ObjectMapper objectMapper;
  private Flat2Pojo converter;

  @BeforeEach
  void init() {
    objectMapper = TestSupport.createObjectMapper();
    converter = TestSupport.createConverter(objectMapper);
  }

  @Test
  void test01_leftJoin_orderWithNoCustomerNoAddressNoLineItems() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      rootKeys: ["order/id"]
      lists:
        - path: "lineItem"
          keyPaths: ["sku"]
    """);

    // Order with no customer, no address, no line items (all optional fields omitted)
    List<Map<String, ?>> rows =
        List.of(Map.of("order/id", "ORD001", "order/date", "2025-01-15"));

    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
      {
        "order": { "id": "ORD001", "date": "2025-01-15" },
        "lineItem": []
      }
    """,
        out);
  }

  @Test
  void test02_leftJoin_orderWithCustomerNoAddress() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      rootKeys: ["order/id"]
      lists:
        - path: "lineItem"
          keyPaths: ["sku"]
    """);

    // Order with customer but no address (address fields omitted)
    List<Map<String, ?>> rows =
        List.of(
            Map.of(
                "order/id", "ORD002",
                "order/date", "2025-02-20",
                "customer/id", "CUST100",
                "customer/name", "Alice",
                "lineItem/sku", "SKU-A",
                "lineItem/qty", 2),
            Map.of(
                "order/id", "ORD002",
                "order/date", "2025-02-20",
                "customer/id", "CUST100",
                "customer/name", "Alice",
                "lineItem/sku", "SKU-B",
                "lineItem/qty", 5));

    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
      {
        "order": { "id": "ORD002", "date": "2025-02-20" },
        "customer": { "id": "CUST100", "name": "Alice" },
        "lineItem": [
          { "sku": "SKU-A", "qty": 2 },
          { "sku": "SKU-B", "qty": 5 }
        ]
      }
    """,
        out);
  }

  @Test
  void test03_leftJoin_orderWithCustomerAndAddress() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      rootKeys: ["order/id"]
      lists:
        - path: "lineItem"
          keyPaths: ["sku"]
    """);

    // Order with customer, address, and one line item
    List<Map<String, ?>> rows =
        List.of(
            // First row: no lineItem (keyPath missing) -> entry skipped
            Map.of(
                "order/id", "ORD003",
                "order/date", "2025-03-10",
                "customer/id", "CUST200",
                "customer/name", "Bob Smith",
                "address/street", "123 Main St",
                "address/city", "Springfield",
                "address/country", "USA"),
            // Second row: lineItem present
            Map.of(
                "order/id", "ORD003",
                "order/date", "2025-03-10",
                "customer/id", "CUST200",
                "customer/name", "Bob Smith",
                "address/street", "123 Main St",
                "address/city", "Springfield",
                "address/country", "USA",
                "lineItem/sku", "SKU-C",
                "lineItem/qty", 1));

    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
      {
        "order": { "id": "ORD003", "date": "2025-03-10" },
        "customer": { "id": "CUST200", "name": "Bob Smith" },
        "address": { "street": "123 Main St", "city": "Springfield", "country": "USA" },
        "lineItem": [
          { "sku": "SKU-C", "qty": 1 }
        ]
      }
    """,
        out);
  }

  @Test
  void test04_leftJoin_multipleOrdersMixedScenarios() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      rootKeys: ["order/id"]
      lists:
        - path: "lineItem"
          keyPaths: ["sku"]
    """);

    // All three scenarios in one dataset (simulates original CSV)
    List<Map<String, ?>> rows =
        List.of(
            // ORD001: no customer, no address, no lineItems
            Map.of("order/id", "ORD001", "order/date", "2025-01-15"),
            // ORD002: customer, no address, two lineItems
            Map.of(
                "order/id", "ORD002",
                "order/date", "2025-02-20",
                "customer/id", "CUST100",
                "customer/name", "Alice",
                "lineItem/sku", "SKU-A",
                "lineItem/qty", 2),
            Map.of(
                "order/id", "ORD002",
                "order/date", "2025-02-20",
                "customer/id", "CUST100",
                "customer/name", "Alice",
                "lineItem/sku", "SKU-B",
                "lineItem/qty", 5),
            // ORD003: customer, address, no lineItem in first row
            Map.of(
                "order/id", "ORD003",
                "order/date", "2025-03-10",
                "customer/id", "CUST200",
                "customer/name", "Bob Smith",
                "address/street", "123 Main St",
                "address/city", "Springfield",
                "address/country", "USA"),
            // ORD003: second row with lineItem
            Map.of(
                "order/id", "ORD003",
                "order/date", "2025-03-10",
                "customer/id", "CUST200",
                "customer/name", "Bob Smith",
                "address/street", "123 Main St",
                "address/city", "Springfield",
                "address/country", "USA",
                "lineItem/sku", "SKU-C",
                "lineItem/qty", 1));

    var out = converter.convertAll(rows, JsonNode.class, cfg);

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
      [
        {
          "order": { "id": "ORD001", "date": "2025-01-15" },
          "lineItem": []
        },
        {
          "order": { "id": "ORD002", "date": "2025-02-20" },
          "customer": { "id": "CUST100", "name": "Alice" },
          "lineItem": [
            { "sku": "SKU-A", "qty": 2 },
            { "sku": "SKU-B", "qty": 5 }
          ]
        },
        {
          "order": { "id": "ORD003", "date": "2025-03-10" },
          "customer": { "id": "CUST200", "name": "Bob Smith" },
          "address": { "street": "123 Main St", "city": "Springfield", "country": "USA" },
          "lineItem": [
            { "sku": "SKU-C", "qty": 1 }
          ]
        }
      ]
    """,
        out);
  }
}
