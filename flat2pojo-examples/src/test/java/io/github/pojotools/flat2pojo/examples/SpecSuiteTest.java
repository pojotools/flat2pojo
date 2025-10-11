package io.github.pojotools.flat2pojo.examples;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pojotools.flat2pojo.core.api.Flat2Pojo;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import io.github.pojotools.flat2pojo.core.config.MappingConfigLoader;
import io.github.pojotools.flat2pojo.core.config.ValidationException;
import io.github.pojotools.flat2pojo.examples.domain.ImmutableProductRoot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpecSuiteTest {
  private ObjectMapper objectMapper;
  private Flat2Pojo converter;

  @BeforeEach
  void init() {
    objectMapper = TestSupport.createObjectMapper();
    converter = TestSupport.createConverter(objectMapper);
  }

  @Test
  void test01_singleRow_noLists() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      lists: []
    """);

    List<Map<String, ?>> rows =
        List.of(Map.of("workflow/isTerminated", false, "metadata/name", "Alpha"));

    var out =
        TestSupport.firstElementOrThrow(
            converter.convertAll(rows, ImmutableProductRoot.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
      {
        "workflow": { "isTerminated": false },
        "metadata": { "name": "Alpha" },
        "definitions": []
      }
    """,
        out);
  }

  @Test
  void test02_sparse_default_absent_fields() {
    // No schema → absent (not null)
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      lists: []
    """);

    List<Map<String, ?>> rows = List.of(Map.of("metadata/name", "Alpha"));
    var out =
        TestSupport.firstElementOrThrow(
            converter.convertAll(rows, ImmutableProductRoot.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
      {
        "metadata": { "name": "Alpha" },
        "definitions": []
      }
    """,
        out);
  }

  @Test
  void test03_sparseRows_allowed() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      allowSparseRows: true
      lists: []
    """);

    List<Map<String, ?>> rows = List.of(Map.of("metadata/name", "Alpha"));
    var out =
        TestSupport.firstElementOrThrow(
            converter.convertAll(rows, ImmutableProductRoot.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
      {
        "metadata": { "name": "Alpha" },
        "definitions": []
      }
    """,
        out);
  }

  @Test
  void test04_rootGrouping_multipleRoots() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      rootKeys: ["referencedProductId/identifier"]
      lists:
        - path: "definitions"
          keyPaths: ["id/identifier"]
    """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of("referencedProductId/identifier", "P-1", "definitions/id/identifier", "D-1"),
            Map.of("referencedProductId/identifier", "P-2", "definitions/id/identifier", "D-2"));

    var out = converter.convertAll(rows, ImmutableProductRoot.class, cfg);

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
      [
        {
          "referencedProductId": { "identifier": "P-1" },
          "definitions": [ { "id": { "identifier": "D-1" } } ]
        },
        {
          "referencedProductId": { "identifier": "P-2" },
          "definitions": [ { "id": { "identifier": "D-2" } } ]
        }
      ]
    """,
        out);
  }

  @Test
  void test05_hierarchical_grouping_tasks_comments() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      rootKeys: ["referencedProductId/identifier"]
      lists:
        - path: "definitions"
          keyPaths: ["id/identifier"]
        - path: "definitions/tracker/tasks"
          keyPaths: ["taskDate"]
        - path: "definitions/tracker/tasks/comments"
          keyPaths: ["loggedAt"]
    """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of(
                "referencedProductId/identifier",
                "P-1",
                "definitions/id/identifier",
                "D-1",
                "definitions/tracker/tasks/taskDate",
                "2025-01-01",
                "definitions/tracker/tasks/comments/loggedAt",
                "2025-01-01T00:00:00Z"),
            Map.of(
                "referencedProductId/identifier",
                "P-1",
                "definitions/id/identifier",
                "D-1",
                "definitions/tracker/tasks/taskDate",
                "2025-01-01",
                "definitions/tracker/tasks/comments/loggedAt",
                "2025-01-01T01:00:00Z"),
            Map.of(
                "referencedProductId/identifier",
                "P-1",
                "definitions/id/identifier",
                "D-1",
                "definitions/tracker/tasks/taskDate",
                "2025-01-02",
                "definitions/tracker/tasks/comments/loggedAt",
                "2025-01-02T00:00:00Z"),
            Map.of(
                "referencedProductId/identifier",
                "P-1",
                "definitions/id/identifier",
                "D-1",
                "definitions/tracker/tasks/taskDate",
                "2025-01-02",
                "definitions/tracker/tasks/comments/loggedAt",
                "2025-01-02T01:00:00Z"));

    var out =
        TestSupport.firstElementOrThrow(
            converter.convertAll(rows, ImmutableProductRoot.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
      {
        "referencedProductId": { "identifier": "P-1" },
        "definitions": [
          {
            "id": { "identifier": "D-1" },
            "tracker": {
              "comments": [],
              "tasks": [
                {
                  "taskDate": "2025-01-01",
                  "comments": [
                    { "loggedAt": "2025-01-01T00:00:00Z" },
                    { "loggedAt": "2025-01-01T01:00:00Z" }
                  ]
                },
                {
                  "taskDate": "2025-01-02",
                  "comments": [
                    { "loggedAt": "2025-01-02T00:00:00Z" },
                    { "loggedAt": "2025-01-02T01:00:00Z" }
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
  void test06_cartesian_with_sibling_comments_full_assert() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      rootKeys: ["referencedProductId/identifier"]
      lists:
        - path: "definitions"
          keyPaths: ["id/identifier"]
        - path: "definitions/tracker/comments"
          keyPaths: ["loggedAt"]
        - path: "definitions/tracker/tasks"
          keyPaths: ["taskDate"]
        - path: "definitions/tracker/tasks/comments"
          keyPaths: ["loggedAt"]
    """);

    // Build the full 36 cartesian rows for 2 definitions.
    ArrayList<Map<String, ?>> rows = new ArrayList<>();
    String[] defs = {"D-1", "D-2"};
    String[] trackerTimes = {
      "2025-01-01T00:00:00Z", "2025-01-01T01:00:00Z", "2025-01-01T02:00:00Z"
    };
    String[] taskDates = {"2025-01-01", "2025-01-02", "2025-01-03"};
    String[] commentTimes = {"T00:10:00Z", "T00:20:00Z"};

    for (String d : defs) {
      for (String tt : trackerTimes) {
        for (String td : taskDates) {
          for (String t : commentTimes) {
            rows.add(
                Map.of(
                    "referencedProductId/identifier", "P-1",
                    "definitions/id/identifier", d,
                    "definitions/tracker/comments/loggedAt", tt,
                    "definitions/tracker/tasks/taskDate", td,
                    "definitions/tracker/tasks/comments/loggedAt",
                        td + t // e.g. 2025-01-01T00:10:00Z
                    ));
          }
        }
      }
    }

    var out =
        TestSupport.firstElementOrThrow(
            converter.convertAll(rows, ImmutableProductRoot.class, cfg));

    // Full expected tree (two definitions, each with 3 tracker comments + 3 tasks each with 2
    // comments)
    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
      {
        "referencedProductId": { "identifier": "P-1" },
        "definitions": [
          {
            "id": { "identifier": "D-1" },
            "tracker": {
              "comments": [
                {"loggedAt":"2025-01-01T00:00:00Z"},
                {"loggedAt":"2025-01-01T01:00:00Z"},
                {"loggedAt":"2025-01-01T02:00:00Z"}
              ],
              "tasks": [
                {
                  "taskDate":"2025-01-01",
                  "comments":[
                    {"loggedAt":"2025-01-01T00:10:00Z"},
                    {"loggedAt":"2025-01-01T00:20:00Z"}
                  ]
                },
                {
                  "taskDate":"2025-01-02",
                  "comments":[
                    {"loggedAt":"2025-01-02T00:10:00Z"},
                    {"loggedAt":"2025-01-02T00:20:00Z"}
                  ]
                },
                {
                  "taskDate":"2025-01-03",
                  "comments":[
                    {"loggedAt":"2025-01-03T00:10:00Z"},
                    {"loggedAt":"2025-01-03T00:20:00Z"}
                  ]
                }
              ]
            }
          },
          {
            "id": { "identifier": "D-2" },
            "tracker": {
              "comments": [
                {"loggedAt":"2025-01-01T00:00:00Z"},
                {"loggedAt":"2025-01-01T01:00:00Z"},
                {"loggedAt":"2025-01-01T02:00:00Z"}
              ],
              "tasks": [
                {
                  "taskDate":"2025-01-01",
                  "comments":[
                    {"loggedAt":"2025-01-01T00:10:00Z"},
                    {"loggedAt":"2025-01-01T00:20:00Z"}
                  ]
                },
                {
                  "taskDate":"2025-01-02",
                  "comments":[
                    {"loggedAt":"2025-01-02T00:10:00Z"},
                    {"loggedAt":"2025-01-02T00:20:00Z"}
                  ]
                },
                {
                  "taskDate":"2025-01-03",
                  "comments":[
                    {"loggedAt":"2025-01-03T00:10:00Z"},
                    {"loggedAt":"2025-01-03T00:20:00Z"}
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
  void test07_primitive_split_weekdays() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      lists:
        - path: "definitions"
          keyPaths: ["id/identifier"]
      primitives:
        - path: "definitions/schedule/weekdays"
          split: { delimiter: ",", trim: true }
    """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of(
                "definitions/id/identifier", "D-1",
                "definitions/schedule/weekdays", "MON, TUE, WED"));

    var out =
        TestSupport.firstElementOrThrow(
            converter.convertAll(rows, ImmutableProductRoot.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
      {
        "definitions": [
          {
            "id": { "identifier": "D-1" },
            "schedule": { "weekdays": ["MON","TUE","WED"] }
          }
        ]
      }
    """,
        out);
  }

  @Test
  void test08_jackson_first_coercions_passthrough_values() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      lists: []
    """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of(
                "flags/isUser", "yes",
                "status/type", "covenantproduct",
                "dates/modifiedAt", "2025-01-01T01:23:45.000Z"));

    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
      {
        "flags": { "isUser": "yes" },
        "status": { "type": "covenantproduct" },
        "dates": { "modifiedAt": "2025-01-01T01:23:45.000Z" }
      }
    """,
        out);
  }

  @Test
  void test09_orderBy_basic() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      lists:
        - path: "definitions"
          keyPaths: ["id/identifier"]
          orderBy:
            - path: "name"
              direction: "asc"
              nulls: "last"
    """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of("definitions/id/identifier", "D-2", "definitions/name", "Zeta"),
            Map.of("definitions/id/identifier", "D-1", "definitions/name", "Alpha"),
            Map.of("definitions/id/identifier", "D-3"));

    var out =
        TestSupport.firstElementOrThrow(
            converter.convertAll(rows, ImmutableProductRoot.class, cfg));

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
  void test10_dedupe_merge() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      lists:
        - path: "definitions"
          keyPaths: ["id/identifier"]
          dedupe: true
          onConflict: "merge"
    """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of("definitions/id/identifier", "D-1", "definitions/name", "Alpha"),
            Map.of("definitions/id/identifier", "D-1", "definitions/audit/modifiedBy", "me"));

    var out =
        TestSupport.firstElementOrThrow(
            converter.convertAll(rows, ImmutableProductRoot.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
      {
        "definitions": [
          {
            "id": { "identifier": "D-1" },
            "name": "Alpha",
            "audit": { "modifiedBy": "me" }
          }
        ]
      }
    """,
        out);
  }

  @Test
  void test11_conflict_lastWriteWins() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      lists:
        - path: "definitions"
          keyPaths: ["id/identifier"]
          dedupe: true
          onConflict: "lastWriteWins"
    """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of("definitions/id/identifier", "D-1", "definitions/name", "Alpha"),
            Map.of("definitions/id/identifier", "D-1", "definitions/name", "Beta"));

    var out =
        TestSupport.firstElementOrThrow(
            converter.convertAll(rows, ImmutableProductRoot.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
      {
        "definitions": [
          { "id": { "identifier": "D-1" }, "name": "Beta" }
        ]
      }
    """,
        out);
  }

  @Test
  void test12_conflict_error_throws() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      lists:
        - path: "definitions"
          keyPaths: ["id/identifier"]
          dedupe: true
          onConflict: "error"
    """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of("definitions/id/identifier", "D-1", "definitions/name", "Alpha"),
            Map.of("definitions/id/identifier", "D-1", "definitions/name", "Beta"));

    assertThatThrownBy(() -> converter.convertAll(rows, JsonNode.class, cfg))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void test13_unknown_paths_ignored() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      lists: []
    """);

    List<Map<String, ?>> rows = List.of(Map.of("metadata/name", "Alpha", "unknown/field", "noise"));

    var out =
        TestSupport.firstElementOrThrow(
            converter.convertAll(rows, ImmutableProductRoot.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
      {
        "metadata": { "name": "Alpha" },
        "definitions": []
      }
    """,
        out);
  }

  @Test
  void test14_invalid_hierarchy_rejected() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      lists:
        - path: "definitions/tracker/tasks/comments"
          keyPaths:
            - "definitions/id/identifier"
            - "definitions/tracker/tasks/taskDate"
            - "definitions/tracker/tasks/comments/loggedAt"
    """);

    assertThatThrownBy(() -> MappingConfigLoader.validateHierarchy(cfg))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void test15_blank_to_null_collapsing() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      nullPolicy: { blanksAsNulls: true }
      lists: []
    """);

    List<Map<String, ?>> rows = List.of(Map.of("metadata/description", ""));

    JsonNode out = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper, """
      { "metadata": { "description": null } }
    """, out);
  }

  @Test
  void test16_streaming_many_roots_full_shape() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      rootKeys: ["referencedProductId/identifier"]
      lists:
        - path: "definitions"
          keyPaths: ["id/identifier"]
    """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of("referencedProductId/identifier", "P-1", "definitions/id/identifier", "D-1"),
            Map.of("referencedProductId/identifier", "P-1", "definitions/id/identifier", "D-2"),
            Map.of("referencedProductId/identifier", "P-2", "definitions/id/identifier", "D-1"),
            Map.of("referencedProductId/identifier", "P-2", "definitions/id/identifier", "D-2"),
            Map.of("referencedProductId/identifier", "P-3", "definitions/id/identifier", "D-1"),
            Map.of("referencedProductId/identifier", "P-3", "definitions/id/identifier", "D-2"));

    var out = converter.stream(rows.iterator(), ImmutableProductRoot.class, cfg).toList();

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
      [
        {
          "referencedProductId": { "identifier": "P-1" },
          "definitions": [
            { "id": { "identifier": "D-1" } },
            { "id": { "identifier": "D-2" } }
          ]
        },
        {
          "referencedProductId": { "identifier": "P-2" },
          "definitions": [
            { "id": { "identifier": "D-1" } },
            { "id": { "identifier": "D-2" } }
          ]
        },
        {
          "referencedProductId": { "identifier": "P-3" },
          "definitions": [
            { "id": { "identifier": "D-1" } },
            { "id": { "identifier": "D-2" } }
          ]
        }
      ]
    """,
        out);
  }

  @Test
  void test17_null_keyPaths_should_skip_list_entries() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            rootKeys: ["referencedProductId/identifier"]
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
              - path: "definitions/tracker/tasks"
                keyPaths: ["taskDate"]
              - path: "definitions/tracker/tasks/comments"
                keyPaths: ["loggedAt"]
          """);

    // Row where tracker/task has default field values (isUser=false) but null keyPath → skip
    List<Map<String, ?>> rows =
        List.of(
            Map.of(
                "referencedProductId/identifier",
                "P-1",
                "definitions/id/identifier",
                "D-1",
                "definitions/name",
                "Definition 1",
                "definitions/tracker/tasks/isUser",
                false,
                "definitions/tracker/tasks/gracePeriod",
                30));
    // Note: definitions/tracker/tasks/taskDate is missing (null) → skip the task entry

    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
            {
              "referencedProductId": { "identifier": "P-1" },
              "definitions": [
                {
                  "id": { "identifier": "D-1" },
                  "name": "Definition 1",
                  "tracker": { "tasks": [] }
                }
              ]
            }
          """,
        out);
  }

  @Test
  void test18_mixed_keyPaths_some_present_some_absent() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            rootKeys: ["referencedProductId/identifier"]
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
              - path: "definitions/tracker/tasks"
                keyPaths: ["taskDate"]
              - path: "definitions/tracker/tasks/comments"
                keyPaths: ["loggedAt"]
          """);

    // Multiple definitions: some with taskDate (creates tasks), some without (no tasks)
    List<Map<String, ?>> rows =
        List.of(
            // D-1: Has taskDate -> task entry should be created
            Map.of(
                "referencedProductId/identifier", "P-1",
                "definitions/id/identifier", "D-1",
                "definitions/name", "Definition 1",
                "definitions/tracker/tasks/taskDate", "2025-01-01",
                "definitions/tracker/tasks/isUser", true,
                "definitions/tracker/tasks/gracePeriod", 15,
                "definitions/tracker/tasks/comments/loggedAt", "2025-01-01T10:00:00Z",
                "definitions/tracker/tasks/comments/text", "First comment"),
            // D-1: Second task with different date
            Map.of(
                "referencedProductId/identifier", "P-1",
                "definitions/id/identifier", "D-1",
                "definitions/tracker/tasks/taskDate", "2025-01-02",
                "definitions/tracker/tasks/isUser", false,
                "definitions/tracker/tasks/gracePeriod", 30),
            // D-2: Missing taskDate -> no task entry should be created, values ignored
            Map.of(
                "referencedProductId/identifier", "P-1",
                "definitions/id/identifier", "D-2",
                "definitions/name", "Definition 2",
                "definitions/tracker/tasks/isUser", false,
                "definitions/tracker/tasks/gracePeriod", 45
                // Note: taskDate is missing, so this task should be skipped entirely
                ),
            // D-3: Has taskDate but missing comment loggedAt -> task created, comment skipped
            Map.of(
                "referencedProductId/identifier", "P-1",
                "definitions/id/identifier", "D-3",
                "definitions/name", "Definition 3",
                "definitions/tracker/tasks/taskDate", "2025-01-03",
                "definitions/tracker/tasks/isUser", true,
                "definitions/tracker/tasks/comments/text", "Comment without timestamp"
                // Note: comments/loggedAt is missing, so comment should be skipped
                ));

    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
            {
              "referencedProductId": { "identifier": "P-1" },
              "definitions": [
                {
                  "id": { "identifier": "D-1" },
                  "name": "Definition 1",
                  "tracker": {
                    "tasks": [
                      {
                        "taskDate": "2025-01-01",
                        "isUser": true,
                        "gracePeriod": 15,
                        "comments": [
                          {
                            "loggedAt": "2025-01-01T10:00:00Z",
                            "text": "First comment"
                          }
                        ]
                      },
                      {
                        "taskDate": "2025-01-02",
                        "isUser": false,
                        "gracePeriod": 30,
                        "comments": []
                      }
                    ]
                  }
                },
                {
                  "id": { "identifier": "D-2" },
                  "name": "Definition 2",
                  "tracker": { "tasks": [] }
                },
                {
                  "id": { "identifier": "D-3" },
                  "name": "Definition 3",
                  "tracker": {
                    "tasks": [
                      {
                        "taskDate": "2025-01-03",
                        "isUser": true,
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

  @Test
  void test19_deeply_nested_keyPath_skipping() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
              - path: "definitions/modules"
                keyPaths: ["name"]
              - path: "definitions/modules/components"
                keyPaths: ["id"]
              - path: "definitions/modules/components/features"
                keyPaths: ["name"]
          """);

    List<Map<String, ?>> rows =
        List.of(
            // Complete hierarchy: definition -> module -> component -> feature
            Map.of(
                "definitions/id/identifier", "D-1",
                "definitions/modules/name", "ModuleA",
                "definitions/modules/components/id", "C-1",
                "definitions/modules/components/features/name", "Feature1",
                "definitions/modules/components/features/enabled", true),
            // Missing component id -> component and all features under it should be skipped
            Map.of(
                "definitions/id/identifier", "D-2",
                "definitions/modules/name", "ModuleB",
                "definitions/modules/components/type", "service",
                "definitions/modules/components/features/name", "Feature2",
                "definitions/modules/components/features/enabled", false
                // Note: components/id is missing -> component entry skipped, feature also skipped
                ),
            // Missing module name -> module and everything under it should be skipped
            Map.of(
                "definitions/id/identifier", "D-3",
                "definitions/modules/components/id", "C-3",
                "definitions/modules/components/features/name", "Feature3"
                // Note: modules/name is missing -> entire modules subtree skipped
                ));

    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
            {
              "definitions": [
                {
                  "id": { "identifier": "D-1" },
                  "modules": [
                    {
                      "name": "ModuleA",
                      "components": [
                        {
                          "id": "C-1",
                          "features": [
                            {
                              "name": "Feature1",
                              "enabled": true
                            }
                          ]
                        }
                      ]
                    }
                  ]
                },
                {
                  "id": { "identifier": "D-2" },
                  "modules": [
                    {
                      "name": "ModuleB",
                      "components": []
                    }
                  ]
                },
                {
                  "id": { "identifier": "D-3" },
                  "modules": []
                }
              ]
            }
          """,
        out);
  }

  @Test
  void test20_conflict_policy_firstWriteWins() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
                dedupe: true
                onConflict: "firstWriteWins"
          """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of("definitions/id/identifier", "D-1", "definitions/name", "FirstName"),
            Map.of("definitions/id/identifier", "D-1", "definitions/name", "SecondName"));

    var out =
        TestSupport.firstElementOrThrow(
            converter.convertAll(rows, ImmutableProductRoot.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        {
          "definitions": [
            { "id": { "identifier": "D-1" }, "name": "FirstName" }
          ]
        }
        """,
        out);
  }

  @Test
  void test21_primitive_types_and_blanks_as_nulls() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            nullPolicy: { blanksAsNulls: true }
            lists: []
          """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of(
                "numbers/longField", 9223372036854775807L,
                "numbers/doubleField", 3.14159,
                "text/blankField", "   ",
                "text/emptyField", "",
                "text/validField", "value"));

    JsonNode out = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        {
          "numbers": {
            "longField": 9223372036854775807,
            "doubleField": 3.14159
          },
          "text": {
            "blankField": null,
            "emptyField": null,
            "validField": "value"
          }
        }
        """,
        out);
  }

  @Test
  void test22_primitive_split_with_blanks() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            nullPolicy: { blanksAsNulls: true }
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
            primitives:
              - path: "definitions/tags"
                split: { delimiter: ",", trim: true }
          """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of(
                "definitions/id/identifier", "D-1",
                "definitions/tags", "tag1, , tag3,"));

    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        {
          "definitions": [
            {
              "id": { "identifier": "D-1" },
              "tags": ["tag1", null, "tag3", null]
            }
          ]
        }
        """,
        out);
  }

  @Test
  void test23_complex_separator_and_deep_paths() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "::"
            allowSparseRows: false
            lists: []
          """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of(
                "root::deeply::nested::field", "value",
                "simple", "noSeparator",
                "a::b::c::d::e::f", "veryDeep"));

    JsonNode out = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        {
          "root": {
            "deeply": {
              "nested": {
                "field": "value"
              }
            }
          },
          "simple": "noSeparator",
          "a": {
            "b": {
              "c": {
                "d": {
                  "e": {
                    "f": "veryDeep"
                  }
                }
              }
            }
          }
        }
        """,
        out);
  }

  @Test
  void test24_merge_conflict_with_incompatible_types() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
                dedupe: true
                onConflict: "merge"
          """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of("definitions/id/identifier", "D-1", "definitions/conflictField", "stringValue"),
            Map.of("definitions/id/identifier", "D-1", "definitions/conflictField", 123));

    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    // When types are incompatible, merge should fall back to lastWriteWins
    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        {
          "definitions": [
            {
              "id": { "identifier": "D-1" },
              "conflictField": 123
            }
          ]
        }
        """,
        out);
  }

  @Test
  void test25_deep_merge_nested_objects() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
                dedupe: true
                onConflict: "merge"
          """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of(
                "definitions/id/identifier", "D-1",
                "definitions/metadata/field1", "value1"),
            Map.of(
                "definitions/id/identifier", "D-1",
                "definitions/metadata/field2", "value2"));

    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        {
          "definitions": [
            {
              "id": { "identifier": "D-1" },
              "metadata": {
                "field1": "value1",
                "field2": "value2"
              }
            }
          ]
        }
        """,
        out);
  }

  @Test
  void test26_ordering_with_cache_hit() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
                orderBy:
                  - path: "priority"
                    direction: "desc"
          """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of("definitions/id/identifier", "D-1", "definitions/priority", 1),
            Map.of("definitions/id/identifier", "D-2", "definitions/priority", 3),
            Map.of("definitions/id/identifier", "D-3", "definitions/priority", 2));

    // Convert multiple times to trigger cache hit scenarios
    var out1 = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));
    var out2 = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    String expectedJson =
        """
        {
          "definitions": [
            { "id": { "identifier": "D-2" }, "priority": 3 },
            { "id": { "identifier": "D-3" }, "priority": 2 },
            { "id": { "identifier": "D-1" }, "priority": 1 }
          ]
        }
        """;

    // Both conversions should produce identical results (cache works correctly)
    PojoJsonAssert.assertPojoJsonEquals(objectMapper, expectedJson, out1);
    PojoJsonAssert.assertPojoJsonEquals(objectMapper, expectedJson, out2);
  }

  @Test
  void test27_order_by_nulls_handling() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
                orderBy:
                  - path: "priority"
                    direction: "asc"
                    nulls: "first"
          """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of("definitions/id/identifier", "D-1", "definitions/priority", 2),
            Map.of("definitions/id/identifier", "D-2"), // null priority
            Map.of("definitions/id/identifier", "D-3", "definitions/priority", 1));

    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        {
          "definitions": [
            { "id": { "identifier": "D-2" } },
            { "id": { "identifier": "D-3" }, "priority": 1 },
            { "id": { "identifier": "D-1" }, "priority": 2 }
          ]
        }
        """,
        out);
  }

  @Test
  void test28_multiple_field_conflicts_and_iterations() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
                dedupe: true
                onConflict: "merge"
          """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of(
                "definitions/id/identifier", "D-1",
                "definitions/field1", "value1",
                "definitions/field2", "value2",
                "definitions/field3", "value3",
                "definitions/nested/subfield1", "sub1",
                "definitions/nested/subfield2", "sub2"),
            Map.of(
                "definitions/id/identifier", "D-1",
                "definitions/field4", "value4",
                "definitions/field5", "value5",
                "definitions/field6", "value6",
                "definitions/nested/subfield3", "sub3",
                "definitions/nested/subfield4", "sub4"));

    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        {
          "definitions": [
            {
              "id": { "identifier": "D-1" },
              "field1": "value1",
              "field2": "value2",
              "field3": "value3",
              "field4": "value4",
              "field5": "value5",
              "field6": "value6",
              "nested": {
                "subfield1": "sub1",
                "subfield2": "sub2",
                "subfield3": "sub3",
                "subfield4": "sub4"
              }
            }
          ]
        }
        """,
        out);
  }

  @Test
  void test29_null_and_empty_handling_edge_cases() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: true
            nullPolicy: { blanksAsNulls: true }
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
          """);

    // Use Map.of() carefully - it doesn't accept null values
    List<Map<String, ?>> rows =
        List.of(
            Map.of("definitions/id/identifier", "D-1"),
            Map.of("definitions/id/identifier", "D-2", "definitions/emptyField", ""),
            Map.of("definitions/id/identifier", "D-4", "definitions/blankField", "   "));

    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        {
          "definitions": [
            { "id": { "identifier": "D-1" } },
            { "id": { "identifier": "D-2" }, "emptyField": null },
            { "id": { "identifier": "D-4" }, "blankField": null }
          ]
        }
        """,
        out);
  }

  @Test
  void test30_complex_path_traversal_edge_cases() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "::"
            allowSparseRows: false
            lists:
              - path: "items"
                keyPaths: ["id"]
            primitives:
              - path: "items::tags"
                split: { delimiter: "|", trim: true }
          """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of(
                "items::id", "item1",
                "items::name", "First Item",
                "items::tags", "tag1|tag2|tag3",
                "level1::field1", "value1"),
            Map.of(
                "items::id", "item2",
                "items::name", "Second Item",
                "items::tags", "tagA|  tagB  |tagC",
                "level1::field2", "value2"));

    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        {
          "items": [
            {
              "id": "item1",
              "name": "First Item",
              "tags": ["tag1", "tag2", "tag3"]
            },
            {
              "id": "item2",
              "name": "Second Item",
              "tags": ["tagA", "tagB", "tagC"]
            }
          ],
          "level1": {
            "field1": "value1",
            "field2": "value2"
          }
        }
        """,
        out);
  }

  @Test
  void test31_multiple_comparator_edge_cases() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
                orderBy:
                  - path: "category"
                    direction: "asc"
                    nulls: "last"
                  - path: "priority"
                    direction: "desc"
                    nulls: "first"
                  - path: "name"
                    direction: "asc"
          """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of(
                "definitions/id/identifier",
                "D-1",
                "definitions/category",
                "B",
                "definitions/priority",
                1,
                "definitions/name",
                "Beta"),
            Map.of(
                "definitions/id/identifier",
                "D-2",
                "definitions/category",
                "A",
                "definitions/name",
                "Alpha"),
            Map.of(
                "definitions/id/identifier",
                "D-3",
                "definitions/category",
                "A",
                "definitions/priority",
                3,
                "definitions/name",
                "Gamma"),
            Map.of(
                "definitions/id/identifier",
                "D-4",
                "definitions/category",
                "A",
                "definitions/priority",
                3,
                "definitions/name",
                "Delta"),
            Map.of("definitions/id/identifier", "D-5", "definitions/name", "Epsilon"));

    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        {
          "definitions": [
            { "id": { "identifier": "D-2" }, "category": "A", "name": "Alpha" },
            { "id": { "identifier": "D-4" }, "category": "A", "priority": 3, "name": "Delta" },
            { "id": { "identifier": "D-3" }, "category": "A", "priority": 3, "name": "Gamma" },
            { "id": { "identifier": "D-1" }, "category": "B", "priority": 1, "name": "Beta" },
            { "id": { "identifier": "D-5" }, "name": "Epsilon" }
          ]
        }
        """,
        out);
  }

  @Test
  void test32_empty_and_single_element_lists() {
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
                orderBy:
                  - path: "priority"
                    direction: "desc"
          """);

    // Test with single element and empty results to hit edge cases
    List<Map<String, ?>> rows =
        List.of(
            Map.of("definitions/id/identifier", "D-1", "definitions/priority", 5),
            Map.of("other/field", "ignored")); // This won't create a definition

    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        {
          "definitions": [
            { "id": { "identifier": "D-1" }, "priority": 5 }
          ],
          "other": {
            "field": "ignored"
          }
        }
        """,
        out);
  }

  @Test
  void test33_comprehensive_real_world_conversion() {
    // Comprehensive test showcasing all major features:
    // - Multiple root objects (grouping by product ID)
    // - Deep hierarchical nesting (definitions -> tracker -> tasks -> comments)
    // - Multiple list configurations with different key paths
    // - Ordering by multiple fields with different directions
    // - Conflict resolution with merge and deduplication
    // - Primitive arrays with split operations (tags)
    // - Null handling with blanks-as-nulls
    // - Complex cartesian product data (realistic database JOIN results)
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            nullPolicy: { blanksAsNulls: true }
            rootKeys: ["referencedProductId/identifier"]
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
                dedupe: true
                onConflict: "merge"
                orderBy:
                  - path: "priority"
                    direction: "desc"
                  - path: "name"
                    direction: "asc"
              - path: "definitions/tracker/comments"
                keyPaths: ["loggedAt"]
                orderBy:
                  - path: "loggedAt"
                    direction: "asc"
              - path: "definitions/tracker/tasks"
                keyPaths: ["taskDate"]
                orderBy:
                  - path: "taskDate"
                    direction: "asc"
              - path: "definitions/tracker/tasks/comments"
                keyPaths: ["loggedAt"]
                orderBy:
                  - path: "loggedAt"
                    direction: "asc"
              - path: "definitions/modules"
                keyPaths: ["name"]
                orderBy:
                  - path: "name"
                    direction: "asc"
              - path: "definitions/modules/components"
                keyPaths: ["id"]
                orderBy:
                  - path: "id"
                    direction: "asc"
              - path: "definitions/modules/components/features"
                keyPaths: ["name"]
                orderBy:
                  - path: "name"
                    direction: "asc"
            primitives:
              - path: "definitions/tags"
                split: { delimiter: ",", trim: true }
            """);

    // Build comprehensive test data representing real-world scenario:
    // Multiple products with definitions, each having complex nested structures
    ArrayList<Map<String, ?>> rows = new ArrayList<>();

    // Product P-1, Definition D-1: Base definition with tags, metadata merge, tracker comments
    rows.add(
        Map.of(
            "referencedProductId/identifier", "P-1",
            "definitions/id/identifier", "D-1",
            "definitions/name", "Core Services",
            "definitions/priority", 5,
            "definitions/tags", "backend, critical, v2.0",
            "definitions/metadata/name", "Core Services Module",
            "definitions/metadata/description", "Main backend services",
            "definitions/tracker/comments/loggedAt", "2025-01-01T09:00:00Z",
            "definitions/tracker/comments/comment", "Initial setup complete",
            "definitions/tracker/comments/loggedBy", "alice"));

    // P-1, D-1: Metadata merge (conflict resolution) + additional tracker comment
    rows.add(
        Map.of(
            "referencedProductId/identifier", "P-1",
            "definitions/id/identifier", "D-1",
            "definitions/metadata/description", "", // Blank should become null
            "definitions/audit/modifiedBy", "alice",
            "definitions/audit/modifiedAt", "2025-01-01T10:00:00Z",
            "definitions/tracker/comments/loggedAt", "2025-01-01T11:00:00Z",
            "definitions/tracker/comments/comment", "Configuration updated",
            "definitions/tracker/comments/loggedBy", "bob"));

    // P-1, D-1: Tasks with nested comments (cartesian product)
    rows.add(
        Map.of(
            "referencedProductId/identifier", "P-1",
            "definitions/id/identifier", "D-1",
            "definitions/tracker/tasks/taskDate", "2025-01-15",
            "definitions/tracker/tasks/dueDate", "2025-01-20",
            "definitions/tracker/tasks/isUser", true,
            "definitions/tracker/tasks/gracePeriod", 5,
            "definitions/tracker/tasks/comments/loggedAt", "2025-01-15T08:00:00Z",
            "definitions/tracker/tasks/comments/comment", "Task started",
            "definitions/tracker/tasks/comments/loggedBy", "charlie"));

    rows.add(
        Map.of(
            "referencedProductId/identifier", "P-1",
            "definitions/id/identifier", "D-1",
            "definitions/tracker/tasks/taskDate", "2025-01-15",
            "definitions/tracker/tasks/comments/loggedAt", "2025-01-15T14:00:00Z",
            "definitions/tracker/tasks/comments/comment", "Halfway done",
            "definitions/tracker/tasks/comments/loggedBy", "alice"));

    // P-1, D-1: Second task
    rows.add(
        Map.of(
            "referencedProductId/identifier", "P-1",
            "definitions/id/identifier", "D-1",
            "definitions/tracker/tasks/taskDate", "2025-01-10",
            "definitions/tracker/tasks/isUser", false,
            "definitions/tracker/tasks/gracePeriod", 10,
            "definitions/tracker/tasks/comments/loggedAt", "2025-01-10T10:00:00Z",
            "definitions/tracker/tasks/comments/comment", "Automated task executed",
            "definitions/tracker/tasks/comments/loggedBy", "system"));

    // P-1, D-1: Modules and nested components/features
    rows.add(
        Map.of(
            "referencedProductId/identifier", "P-1",
            "definitions/id/identifier", "D-1",
            "definitions/modules/name", "Authentication",
            "definitions/modules/version", "1.2.0",
            "definitions/modules/components/id", "auth-api",
            "definitions/modules/components/type", "REST",
            "definitions/modules/components/features/name", "OAuth2",
            "definitions/modules/components/features/enabled", true,
            "definitions/modules/components/features/version", "2.1"));

    rows.add(
        Map.of(
            "referencedProductId/identifier", "P-1",
            "definitions/id/identifier", "D-1",
            "definitions/modules/name", "Authentication",
            "definitions/modules/components/id", "auth-api",
            "definitions/modules/components/features/name", "JWT",
            "definitions/modules/components/features/enabled", true,
            "definitions/modules/components/features/version", "1.5"));

    rows.add(
        Map.of(
            "referencedProductId/identifier", "P-1",
            "definitions/id/identifier", "D-1",
            "definitions/modules/name", "Database",
            "definitions/modules/version", "2.0.0",
            "definitions/modules/components/id", "db-pool",
            "definitions/modules/components/type", "Connection Pool",
            "definitions/modules/components/features/name", "Connection Pooling",
            "definitions/modules/components/features/enabled", true));

    // P-1, D-2: Second definition (lower priority, should come after D-1)
    rows.add(
        Map.of(
            "referencedProductId/identifier", "P-1",
            "definitions/id/identifier", "D-2",
            "definitions/name", "Analytics Engine",
            "definitions/priority", 3,
            "definitions/tags", "analytics, reporting",
            "definitions/tracker/comments/loggedAt", "2025-01-02T10:00:00Z",
            "definitions/tracker/comments/comment", "Analytics module deployed",
            "definitions/tracker/comments/loggedBy", "david"));

    // P-2: Second product with different structure
    rows.add(
        Map.of(
            "referencedProductId/identifier", "P-2",
            "definitions/id/identifier", "D-3",
            "definitions/name", "Frontend Dashboard",
            "definitions/priority", 7,
            "definitions/tags", "frontend, ui, dashboard",
            "definitions/metadata/name", "Frontend Dashboard",
            "definitions/tracker/tasks/taskDate", "2025-02-01",
            "definitions/tracker/tasks/isUser", true,
            "definitions/tracker/tasks/comments/loggedAt", "2025-02-01T09:00:00Z",
            "definitions/tracker/tasks/comments/comment", "UI redesign started"));

    // P-3: Third product (minimal data to test sparse handling)
    rows.add(
        Map.of(
            "referencedProductId/identifier", "P-3",
            "definitions/id/identifier", "D-4",
            "definitions/name", "Legacy System",
            "definitions/priority", 1));

    var out = converter.convertAll(rows, ImmutableProductRoot.class, cfg);

    // Verify the comprehensive transformation
    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        [
          {
            "referencedProductId": { "identifier": "P-1" },
            "definitions": [
              {
                "id": { "identifier": "D-1" },
                "name": "Core Services",
                "priority": 5,
                "tags": ["backend", "critical", "v2.0"],
                "metadata": {
                  "name": "Core Services Module"
                },
                "audit": {
                  "modifiedBy": "alice",
                  "modifiedAt": "2025-01-01T10:00:00Z"
                },
                "tracker": {
                  "comments": [
                    {
                      "comment": "Initial setup complete",
                      "loggedBy": "alice",
                      "loggedAt": "2025-01-01T09:00:00Z"
                    },
                    {
                      "comment": "Configuration updated",
                      "loggedBy": "bob",
                      "loggedAt": "2025-01-01T11:00:00Z"
                    }
                  ],
                  "tasks": [
                    {
                      "taskDate": "2025-01-10",
                      "gracePeriod": 10,
                      "isUser": false,
                      "comments": [
                        {
                          "comment": "Automated task executed",
                          "loggedBy": "system",
                          "loggedAt": "2025-01-10T10:00:00Z"
                        }
                      ]
                    },
                    {
                      "taskDate": "2025-01-15",
                      "dueDate": "2025-01-20",
                      "gracePeriod": 5,
                      "isUser": true,
                      "comments": [
                        {
                          "comment": "Task started",
                          "loggedBy": "charlie",
                          "loggedAt": "2025-01-15T08:00:00Z"
                        },
                        {
                          "comment": "Halfway done",
                          "loggedBy": "alice",
                          "loggedAt": "2025-01-15T14:00:00Z"
                        }
                      ]
                    }
                  ]
                },
                "modules": [
                  {
                    "name": "Authentication",
                    "version": "1.2.0",
                    "components": [
                      {
                        "id": "auth-api",
                        "type": "REST",
                        "features": [
                          {
                            "name": "JWT",
                            "enabled": true,
                            "version": "1.5"
                          },
                          {
                            "name": "OAuth2",
                            "enabled": true,
                            "version": "2.1"
                          }
                        ]
                      }
                    ]
                  },
                  {
                    "name": "Database",
                    "version": "2.0.0",
                    "components": [
                      {
                        "id": "db-pool",
                        "type": "Connection Pool",
                        "features": [
                          {
                            "name": "Connection Pooling",
                            "enabled": true
                          }
                        ]
                      }
                    ]
                  }
                ]
              },
              {
                "id": { "identifier": "D-2" },
                "name": "Analytics Engine",
                "priority": 3,
                "tags": ["analytics", "reporting"],
                "tracker": {
                  "comments": [
                    {
                      "comment": "Analytics module deployed",
                      "loggedBy": "david",
                      "loggedAt": "2025-01-02T10:00:00Z"
                    }
                  ],
                  "tasks": []
                },
                "modules": []
              }
            ]
          },
          {
            "referencedProductId": { "identifier": "P-2" },
            "definitions": [
              {
                "id": { "identifier": "D-3" },
                "name": "Frontend Dashboard",
                "priority": 7,
                "tags": ["frontend", "ui", "dashboard"],
                "metadata": { "name": "Frontend Dashboard" },
                "tracker": {
                  "comments": [],
                  "tasks": [
                    {
                      "taskDate": "2025-02-01",
                      "isUser": true,
                      "comments": [
                        {
                          "comment": "UI redesign started",
                          "loggedAt": "2025-02-01T09:00:00Z"
                        }
                      ]
                    }
                  ]
                },
                "modules": []
              }
            ]
          },
          {
            "referencedProductId": { "identifier": "P-3" },
            "definitions": [
              {
                "id": { "identifier": "D-4" },
                "name": "Legacy System",
                "priority": 1,
                "tracker": {
                  "comments": [],
                  "tasks": []
                },
                "modules": []
              }
            ]
          }
        ]
        """,
        out);
  }

  @Test
  void test34_missing_rootKeys_should_skip_entry() {
    // Test case: root keys are missing -> entry is skipped (no exception thrown)
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            rootKeys: ["referencedProductId/identifier"]
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
          """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of(
                "referencedProductId/identifier",
                "P-1",
                "definitions/id/identifier",
                "D-1",
                "definitions/name",
                "Definition 1"),
            Map.of(
                "definitions/id/identifier", "D-2", "definitions/name", "Definition 2"
                // Note: referencedProductId/identifier is missing → entry should be skipped
                ));

    var out = converter.convertAll(rows, ImmutableProductRoot.class, cfg);

    // Only P-1 should be present, the row with missing root key should be skipped
    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        [
          {
            "referencedProductId": { "identifier": "P-1" },
            "definitions": [
              {
                "id": { "identifier": "D-1" },
                "name": "Definition 1"
              }
            ]
          }
        ]
        """,
        out);
  }

  @Test
  void test35_rootKeys_present_all_entries_processed() {
    // Test case: root keys are present -> all entries are processed normally
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            rootKeys: ["referencedProductId/identifier"]
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
          """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of(
                "referencedProductId/identifier",
                "P-1",
                "definitions/id/identifier",
                "D-1",
                "definitions/name",
                "Definition 1"),
            Map.of(
                "referencedProductId/identifier",
                "P-2",
                "definitions/id/identifier",
                "D-2",
                "definitions/name",
                "Definition 2"));

    var out = converter.convertAll(rows, ImmutableProductRoot.class, cfg);

    // Both entries should be processed
    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        [
          {
            "referencedProductId": { "identifier": "P-1" },
            "definitions": [
              {
                "id": { "identifier": "D-1" },
                "name": "Definition 1"
              }
            ]
          },
          {
            "referencedProductId": { "identifier": "P-2" },
            "definitions": [
              {
                "id": { "identifier": "D-2" },
                "name": "Definition 2"
              }
            ]
          }
        ]
        """,
        out);
  }

  @Test
  void test36_mixed_rootKeys_some_present_some_missing() {
    // Test case: mixed scenario - some entries have root keys, some don't → only valid entries are
    // processed
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            rootKeys: ["referencedProductId/identifier"]
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
          """);

    List<Map<String, ?>> rows =
        List.of(
            // P-1: valid entry with root key
            Map.of(
                "referencedProductId/identifier", "P-1",
                "definitions/id/identifier", "D-1",
                "definitions/name", "Definition 1"),
            // Missing root key → should be skipped
            Map.of("definitions/id/identifier", "D-2", "definitions/name", "Definition 2"),
            // P-2: valid entry with root key
            Map.of(
                "referencedProductId/identifier", "P-2",
                "definitions/id/identifier", "D-3",
                "definitions/name", "Definition 3"),
            // Another missing root key → should be skipped
            Map.of("definitions/id/identifier", "D-4", "definitions/name", "Definition 4"));

    var out = converter.convertAll(rows, ImmutableProductRoot.class, cfg);

    // Only P-1 and P-2 should be present
    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        [
          {
            "referencedProductId": { "identifier": "P-1" },
            "definitions": [
              {
                "id": { "identifier": "D-1" },
                "name": "Definition 1"
              }
            ]
          },
          {
            "referencedProductId": { "identifier": "P-2" },
            "definitions": [
              {
                "id": { "identifier": "D-3" },
                "name": "Definition 3"
              }
            ]
          }
        ]
        """,
        out);
  }

  @Test
  void test37_composite_rootKeys_all_parts_required() {
    // Test case: composite root keys - all parts must be present
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            rootKeys: ["referencedProductId/identifier", "metadata/name"]
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
          """);

    List<Map<String, ?>> rows =
        List.of(
            // Both root keys present → valid
            Map.of(
                "referencedProductId/identifier", "P-1",
                "metadata/name", "Product Alpha",
                "definitions/id/identifier", "D-1",
                "definitions/name", "Definition 1"),
            // Missing second root key → should be skipped
            Map.of(
                "referencedProductId/identifier", "P-2",
                "definitions/id/identifier", "D-2",
                "definitions/name", "Definition 2"),
            // Missing first root key → should be skipped
            Map.of(
                "metadata/name", "Product Gamma",
                "definitions/id/identifier", "D-3",
                "definitions/name", "Definition 3"),
            // Both root keys present → valid
            Map.of(
                "referencedProductId/identifier", "P-4",
                "metadata/name", "Product Delta",
                "definitions/id/identifier", "D-4",
                "definitions/name", "Definition 4"));

    var out = converter.convertAll(rows, ImmutableProductRoot.class, cfg);

    // Only the first and fourth entries should be processed
    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        [
          {
            "referencedProductId": { "identifier": "P-1" },
            "metadata": { "name": "Product Alpha" },
            "definitions": [
              {
                "id": { "identifier": "D-1" },
                "name": "Definition 1"
              }
            ]
          },
          {
            "referencedProductId": { "identifier": "P-4" },
            "metadata": { "name": "Product Delta" },
            "definitions": [
              {
                "id": { "identifier": "D-4" },
                "name": "Definition 4"
              }
            ]
          }
        ]
        """,
        out);
  }

  @Test
  void test38_default_separator_is_slash() {
    // Test that separator defaults to "/" when omitted from config
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            allowSparseRows: false
            lists: []
          """);

    List<Map<String, ?>> rows = List.of(Map.of("metadata/name", "Alpha", "metadata/version", "1.0"));

    var out = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        {
          "metadata": {
            "name": "Alpha",
            "version": "1.0"
          }
        }
        """,
        out);
  }

  @Test
  void test39_default_blanksAsNulls_is_false() {
    // Test that blanksAsNulls defaults to false when nullPolicy is omitted
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            lists: []
          """);

    List<Map<String, ?>> rows = List.of(Map.of("text/emptyField", "", "text/blankField", "   "));

    JsonNode out = TestSupport.firstElementOrThrow(converter.convertAll(rows, JsonNode.class, cfg));

    // Without nullPolicy (defaults to blanksAsNulls: false), blanks remain as empty strings
    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        {
          "text": {
            "emptyField": "",
            "blankField": "   "
          }
        }
        """,
        out);
  }

  @Test
  void test40_default_dedupe_is_true() {
    // Test that dedupe defaults to true when omitted from list rule
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
                onConflict: "merge"
          """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of("definitions/id/identifier", "D-1", "definitions/name", "Alpha"),
            Map.of("definitions/id/identifier", "D-1", "definitions/priority", 5));

    var out =
        TestSupport.firstElementOrThrow(
            converter.convertAll(rows, ImmutableProductRoot.class, cfg));

    // With default dedupe: true, duplicate entries are merged
    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        {
          "definitions": [
            {
              "id": { "identifier": "D-1" },
              "name": "Alpha",
              "priority": 5
            }
          ]
        }
        """,
        out);
  }

  @Test
  void test41_default_onConflict_is_error() {
    // Test that onConflict defaults to "error" when omitted from list rule
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
                dedupe: true
          """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of("definitions/id/identifier", "D-1", "definitions/name", "Alpha"),
            Map.of("definitions/id/identifier", "D-1", "definitions/name", "Beta"));

    // With default onConflict: error, conflicting scalar values should throw
    assertThatThrownBy(() -> converter.convertAll(rows, JsonNode.class, cfg))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void test42_default_orderBy_direction_is_asc() {
    // Test that direction defaults to "asc" when omitted from orderBy
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
                orderBy:
                  - path: "priority"
                    nulls: "last"
          """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of("definitions/id/identifier", "D-1", "definitions/priority", 3),
            Map.of("definitions/id/identifier", "D-2", "definitions/priority", 1),
            Map.of("definitions/id/identifier", "D-3", "definitions/priority", 2));

    var out =
        TestSupport.firstElementOrThrow(
            converter.convertAll(rows, ImmutableProductRoot.class, cfg));

    // With default direction: asc, items should be sorted in ascending order
    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        {
          "definitions": [
            { "id": { "identifier": "D-2" }, "priority": 1 },
            { "id": { "identifier": "D-3" }, "priority": 2 },
            { "id": { "identifier": "D-1" }, "priority": 3 }
          ]
        }
        """,
        out);
  }

  @Test
  void test43_default_orderBy_nulls_is_last() {
    // Test that nulls defaults to "last" when omitted from orderBy
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
                orderBy:
                  - path: "priority"
                    direction: "asc"
          """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of("definitions/id/identifier", "D-1", "definitions/priority", 2),
            Map.of("definitions/id/identifier", "D-2"), // null priority
            Map.of("definitions/id/identifier", "D-3", "definitions/priority", 1));

    var out =
        TestSupport.firstElementOrThrow(
            converter.convertAll(rows, ImmutableProductRoot.class, cfg));

    // With default nulls: last, null values should appear at the end
    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        {
          "definitions": [
            { "id": { "identifier": "D-3" }, "priority": 1 },
            { "id": { "identifier": "D-1" }, "priority": 2 },
            { "id": { "identifier": "D-2" } }
          ]
        }
        """,
        out);
  }

  @Test
  void test44_default_split_delimiter_is_comma() {
    // Test that delimiter defaults to "," when omitted from split config
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
            primitives:
              - path: "definitions/tags"
                split:
                  trim: true
          """);

    List<Map<String, ?>> rows =
        List.of(Map.of("definitions/id/identifier", "D-1", "definitions/tags", "java,spring,boot"));

    var out =
        TestSupport.firstElementOrThrow(
            converter.convertAll(rows, ImmutableProductRoot.class, cfg));

    // With default delimiter: ",", the string should be split on commas
    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        {
          "definitions": [
            {
              "id": { "identifier": "D-1" },
              "tags": ["java", "spring", "boot"]
            }
          ]
        }
        """,
        out);
  }

  @Test
  void test45_default_split_trim_is_false() {
    // Test that trim defaults to false when omitted from split config
    MappingConfig cfg =
        TestSupport.loadMappingConfigFromYaml(
            """
            separator: "/"
            allowSparseRows: false
            lists:
              - path: "definitions"
                keyPaths: ["id/identifier"]
            primitives:
              - path: "definitions/tags"
                split:
                  delimiter: ","
          """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of("definitions/id/identifier", "D-1", "definitions/tags", "java, spring, boot"));

    var out =
        TestSupport.firstElementOrThrow(
            converter.convertAll(rows, ImmutableProductRoot.class, cfg));

    // With default trim: false, spaces should be preserved
    PojoJsonAssert.assertPojoJsonEquals(
        objectMapper,
        """
        {
          "definitions": [
            {
              "id": { "identifier": "D-1" },
              "tags": ["java", " spring", " boot"]
            }
          ]
        }
        """,
        out);
  }
}
