package io.github.pojotools.flat2pojo.examples;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pojotools.flat2pojo.core.api.Flat2Pojo;
import io.github.pojotools.flat2pojo.examples.domain.ImmutableProductRoot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class OrderingSuiteTest {
  private ObjectMapper objectMapper;
  private Flat2Pojo converter;

  @BeforeEach
  void init() {
    objectMapper = TestSupport.createObjectMapper();
    converter = TestSupport.createConverter(objectMapper);
  }

  @Test
  void ordering01_definitions_name_asc_nullsLast() {
    var cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      lists:
        - path: "definitions"
          keyPaths: ["id/identifier"]
          orderBy:
            - path: "name"
              direction: "asc"
              nulls: "last"
    """);
    var rows =
        List.of(
            Map.of("definitions/id/identifier", "D-2", "definitions/name", "Zeta"),
            Map.of("definitions/id/identifier", "D-1", "definitions/name", "Alpha"),
            Map.of("definitions/id/identifier", "D-3"));
    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, ImmutableProductRoot.class, cfg));
    PojoJsonAssert.assertPojoJsonEquals(
      objectMapper,
        """
      {
        "definitions": [
          { "id": { "identifier": "D-1" }, "name": "Alpha" },
          { "id": { "identifier": "D-2" }, "name": "Zeta" },
          { "id": { "identifier": "D-3" } }
        ]
      }
    """,
        out);
  }

  @Test
  void ordering02_definitions_name_desc_nullsFirst() {
    var cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      lists:
        - path: "definitions"
          keyPaths: ["id/identifier"]
          orderBy:
            - path: "name"
              direction: "desc"
              nulls: "first"
    """);
    var rows =
        List.of(
            Map.of("definitions/id/identifier", "D-2", "definitions/name", "Zeta"),
            Map.of("definitions/id/identifier", "D-1", "definitions/name", "Alpha"),
            Map.of("definitions/id/identifier", "D-3"));
    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, ImmutableProductRoot.class, cfg));
    PojoJsonAssert.assertPojoJsonEquals(
      objectMapper,
        """
      {
        "definitions": [
          { "id": { "identifier": "D-3" } },
          { "id": { "identifier": "D-2" }, "name": "Zeta" },
          { "id": { "identifier": "D-1" }, "name": "Alpha" }
        ]
      }
    """,
        out);
  }

  @Test
  void ordering03_multi_key() {
    var cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      lists:
        - path: "definitions"
          keyPaths: ["id/identifier"]
          orderBy:
            - path: "name"
              direction: "asc"
              nulls: "last"
            - path: "id/identifier"
              direction: "desc"
              nulls: "last"
    """);
    var rows =
        List.of(
            Map.of("definitions/id/identifier", "D-1", "definitions/name", "Alpha"),
            Map.of("definitions/id/identifier", "D-3", "definitions/name", "Alpha"),
            Map.of("definitions/id/identifier", "D-2", "definitions/name", "Beta"));
    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, ImmutableProductRoot.class, cfg));
    PojoJsonAssert.assertPojoJsonEquals(
      objectMapper,
        """
      {
        "definitions": [
          { "id": { "identifier": "D-3" }, "name": "Alpha" },
          { "id": { "identifier": "D-1" }, "name": "Alpha" },
          { "id": { "identifier": "D-2" }, "name": "Beta" }
        ]
      }
    """,
        out);
  }

  @Test
  void ordering04_child_tasks_taskDate_asc() {
    var cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      lists:
        - path: "definitions"
          keyPaths: ["id/identifier"]
        - path: "definitions/tracker/tasks"
          keyPaths: ["taskDate"]
          orderBy:
            - path: "taskDate"
              direction: "asc"
              nulls: "last"
    """);
    var rows =
        List.of(
            Map.of(
                "definitions/id/identifier",
                "D-1",
                "definitions/tracker/tasks/taskDate",
                "2025-01-03"),
            Map.of(
                "definitions/id/identifier",
                "D-1",
                "definitions/tracker/tasks/taskDate",
                "2025-01-01"),
            Map.of(
                "definitions/id/identifier",
                "D-1",
                "definitions/tracker/tasks/taskDate",
                "2025-01-02"));
    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, ImmutableProductRoot.class, cfg));
    PojoJsonAssert.assertPojoJsonEquals(
      objectMapper,
        """
      {
        "definitions": [
          {
            "id":{ "identifier":"D-1" },
            "tracker": {
              "comments": [],
              "tasks": [
                {
                  "taskDate":"2025-01-01",
                  "comments":[]
                },
                {
                  "taskDate":"2025-01-02",
                  "comments":[]
                },
                {
                  "taskDate":"2025-01-03",
                  "comments":[]
                }
              ]
            }
          }
        ]
      }
    """,
        out);
  }

  @Test
  void ordering05_grandchild_comments_loggedAt_desc() {
    var cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      lists:
        - path: "definitions"
          keyPaths: ["id/identifier"]
        - path: "definitions/tracker/tasks"
          keyPaths: ["taskDate"]
        - path: "definitions/tracker/tasks/comments"
          keyPaths: ["loggedAt"]
          orderBy:
            - path: "loggedAt"
              direction: "desc"
              nulls: "last"
    """);
    var rows =
        List.of(
            Map.of(
                "definitions/id/identifier",
                "D-1",
                "definitions/tracker/tasks/taskDate",
                "2025-01-01",
                "definitions/tracker/tasks/comments/loggedAt",
                "2025-01-01T00:10:00Z"),
            Map.of(
                "definitions/id/identifier",
                "D-1",
                "definitions/tracker/tasks/taskDate",
                "2025-01-01",
                "definitions/tracker/tasks/comments/loggedAt",
                "2025-01-01T00:20:00Z"),
            Map.of(
                "definitions/id/identifier",
                "D-1",
                "definitions/tracker/tasks/taskDate",
                "2025-01-01",
                "definitions/tracker/tasks/comments/loggedAt",
                "2025-01-01T00:05:00Z"));
    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, ImmutableProductRoot.class, cfg));
    PojoJsonAssert.assertPojoJsonEquals(
      objectMapper,
        """
      {
        "definitions": [
          {
            "id": { "identifier": "D-1" },
            "tracker": {
              "comments": [],
              "tasks": [
                {
                  "taskDate":"2025-01-01",
                  "comments":[
                    {"loggedAt":"2025-01-01T00:20:00Z"},
                    {"loggedAt":"2025-01-01T00:10:00Z"},
                    {"loggedAt":"2025-01-01T00:05:00Z"}
                  ]
                }
              ]
            }
          }
        ]
      }
    """,
        out);
  }

  @Test
  void ordering06_missing_sort_keys_nullsLast() {
    var cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      lists:
        - path: "definitions"
          keyPaths: ["id/identifier"]
          orderBy:
            - path: "name"
              direction: "asc"
              nulls: "last"
    """);
    var rows =
        List.of(
            Map.of("definitions/id/identifier", "D-1"),
            Map.of("definitions/id/identifier", "D-2", "definitions/name", "Bravo"),
            Map.of("definitions/id/identifier", "D-3", "definitions/name", "Alpha"));
    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, ImmutableProductRoot.class, cfg));
    PojoJsonAssert.assertPojoJsonEquals(
      objectMapper,
        """
      {
        "definitions": [
          { "id": { "identifier": "D-3" }, "name": "Alpha" },
          { "id": { "identifier": "D-2" }, "name": "Bravo" },
          { "id": { "identifier": "D-1" } }
        ]
      }
    """,
        out);
  }

  @Test
  void ordering07_missing_sort_keys_nullsFirst() {
    var cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      lists:
        - path: "definitions"
          keyPaths: ["id/identifier"]
          orderBy:
            - path: "name"
              direction: "asc"
              nulls: "first"
    """);
    var rows =
        List.of(
            Map.of("definitions/id/identifier", "D-1"),
            Map.of("definitions/id/identifier", "D-2", "definitions/name", "Bravo"),
            Map.of("definitions/id/identifier", "D-3", "definitions/name", "Alpha"));
    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, ImmutableProductRoot.class, cfg));
    PojoJsonAssert.assertPojoJsonEquals(
      objectMapper,
        """
      {
        "definitions": [
          { "id": { "identifier": "D-1" } },
          { "id": { "identifier": "D-3" }, "name": "Alpha" },
          { "id": { "identifier": "D-2" }, "name": "Bravo" }
        ]
      }
    """,
        out);
  }

  @Test
  void ordering08_out_of_order_arrivals_sorted() {
    var cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      lists:
        - path: "definitions"
          keyPaths: ["id/identifier"]
          orderBy:
            - path: "name"
              direction: "asc"
              nulls: "last"
    """);
    var rows =
        List.of(
            Map.of("definitions/id/identifier", "D-3", "definitions/name", "Gamma"),
            Map.of("definitions/id/identifier", "D-1", "definitions/name", "Alpha"),
            Map.of("definitions/id/identifier", "D-2", "definitions/name", "Beta"));
    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, ImmutableProductRoot.class, cfg));
    PojoJsonAssert.assertPojoJsonEquals(
      objectMapper,
        """
      {
        "definitions": [
          { "id": { "identifier": "D-1" }, "name": "Alpha" },
          { "id": { "identifier": "D-2" }, "name": "Beta" },
          { "id": { "identifier": "D-3" }, "name": "Gamma" }
        ]
      }
    """,
        out);
  }

  @Test
  void ordering09_parent_and_child_have_ordering() {
    var cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      lists:
        - path: "definitions"
          keyPaths: ["id/identifier"]
          orderBy:
            - path: "name"
              direction: "asc"
              nulls: "last"
        - path: "definitions/tracker/tasks"
          keyPaths: ["taskDate"]
          orderBy:
            - path: "taskDate"
              direction: "desc"
              nulls: "last"
    """);
    var rows =
        List.of(
            Map.of(
                "definitions/id/identifier",
                "D-2",
                "definitions/name",
                "Zeta",
                "definitions/tracker/tasks/taskDate",
                "2025-01-02"),
            Map.of(
                "definitions/id/identifier",
                "D-1",
                "definitions/name",
                "Alpha",
                "definitions/tracker/tasks/taskDate",
                "2025-01-03"),
            Map.of(
                "definitions/id/identifier",
                "D-1",
                "definitions/name",
                "Alpha",
                "definitions/tracker/tasks/taskDate",
                "2025-01-01"));
    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, ImmutableProductRoot.class, cfg));
    PojoJsonAssert.assertPojoJsonEquals(
      objectMapper,
        """
      {
        "definitions": [
          {
            "id": { "identifier": "D-1" },
            "name": "Alpha",
            "tracker": {
              "comments": [],
              "tasks": [
                {
                  "taskDate": "2025-01-03",
                  "comments": []
                },
                {
                  "taskDate": "2025-01-01",
                  "comments": []
                }
              ]
            }
          },
          {
            "id": { "identifier": "D-2" },
            "name": "Zeta",
            "tracker": {
              "comments": [],
              "tasks": [
                {
                  "taskDate": "2025-01-02",
                  "comments": []
                }
              ]
            }
          }
        ]
      }
    """,
        out);
  }
}
