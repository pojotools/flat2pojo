package io.flat2pojo.examples;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.flat2pojo.core.api.Flat2Pojo;
import io.flat2pojo.core.config.MappingConfig;
import io.flat2pojo.core.config.MappingConfigLoader;
import io.flat2pojo.core.config.ValidationException;
import io.flat2pojo.examples.domain.ImmutableProductRoot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpecSuiteTest {
  ObjectMapper om;
  Flat2Pojo f2p;

  @BeforeEach
  void init() {
    om = TestSupport.om();
    f2p = TestSupport.mapper(om);
  }

  @Test
  void test01_singleRow_noLists() {
    MappingConfig cfg =
        TestSupport.cfgFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      lists: []
    """);

    List<Map<String, ?>> rows =
        List.of(Map.of("workflow/isTerminated", false, "metadata/name", "Alpha"));

    var out = TestSupport.first(f2p.convertAll(rows, ImmutableProductRoot.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        om,
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
        TestSupport.cfgFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      lists: []
    """);

    List<Map<String, ?>> rows = List.of(Map.of("metadata/name", "Alpha"));
    var out = TestSupport.first(f2p.convertAll(rows, ImmutableProductRoot.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        om,
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
        TestSupport.cfgFromYaml(
            """
      separator: "/"
      allowSparseRows: true
      lists: []
    """);

    List<Map<String, ?>> rows = List.of(Map.of("metadata/name", "Alpha"));
    var out = TestSupport.first(f2p.convertAll(rows, ImmutableProductRoot.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        om,
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
        TestSupport.cfgFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      rootKeys: ["referencedProductId/identifier"]
      lists:
        - path: "definitions"
          keyPaths: ["definitions/id/identifier"]
    """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of("referencedProductId/identifier", "P-1", "definitions/id/identifier", "D-1"),
            Map.of("referencedProductId/identifier", "P-2", "definitions/id/identifier", "D-2"));

    var out = f2p.convertAll(rows, ImmutableProductRoot.class, cfg);

    PojoJsonAssert.assertPojoJsonEquals(
        om,
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
        TestSupport.cfgFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      rootKeys: ["referencedProductId/identifier"]
      lists:
        - path: "definitions"
          keyPaths: ["definitions/id/identifier"]
        - path: "definitions/tracker/tasks"
          keyPaths: ["definitions/tracker/tasks/taskDate"]
        - path: "definitions/tracker/tasks/comments"
          keyPaths: ["definitions/tracker/tasks/comments/loggedAt"]
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

    var out = TestSupport.first(f2p.convertAll(rows, ImmutableProductRoot.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        om,
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
        TestSupport.cfgFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      rootKeys: ["referencedProductId/identifier"]
      lists:
        - path: "definitions"
          keyPaths: ["definitions/id/identifier"]
        - path: "definitions/tracker/comments"
          keyPaths: ["definitions/tracker/comments/loggedAt"]
        - path: "definitions/tracker/tasks"
          keyPaths: ["definitions/tracker/tasks/taskDate"]
        - path: "definitions/tracker/tasks/comments"
          keyPaths: ["definitions/tracker/tasks/comments/loggedAt"]
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

    var out = TestSupport.first(f2p.convertAll(rows, ImmutableProductRoot.class, cfg));

    // Full expected tree (two definitions, each with 3 tracker comments + 3 tasks each with 2
    // comments)
    PojoJsonAssert.assertPojoJsonEquals(
        om,
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
        TestSupport.cfgFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      lists:
        - path: "definitions"
          keyPaths: ["definitions/id/identifier"]
      primitives:
        - path: "definitions/schedule/weekdays"
          split: { delimiter: ",", trim: true }
    """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of(
                "definitions/id/identifier", "D-1",
                "definitions/schedule/weekdays", "MON, TUE, WED"));

    var out = TestSupport.first(f2p.convertAll(rows, ImmutableProductRoot.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        om,
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
        TestSupport.cfgFromYaml(
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

    var out = TestSupport.first(f2p.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        om,
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
        TestSupport.cfgFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      lists:
        - path: "definitions"
          keyPaths: ["definitions/id/identifier"]
          orderBy:
            - path: "definitions/name"
              direction: "asc"
              nulls: "last"
    """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of("definitions/id/identifier", "D-2", "definitions/name", "Zeta"),
            Map.of("definitions/id/identifier", "D-1", "definitions/name", "Alpha"),
            Map.of("definitions/id/identifier", "D-3"));

    var out = TestSupport.first(f2p.convertAll(rows, ImmutableProductRoot.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        om,
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
        TestSupport.cfgFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      lists:
        - path: "definitions"
          keyPaths: ["definitions/id/identifier"]
          dedupe: true
          onConflict: "merge"
    """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of("definitions/id/identifier", "D-1", "definitions/name", "Alpha"),
            Map.of("definitions/id/identifier", "D-1", "definitions/audit/modifiedBy", "me"));

    var out = TestSupport.first(f2p.convertAll(rows, ImmutableProductRoot.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        om,
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
        TestSupport.cfgFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      lists:
        - path: "definitions"
          keyPaths: ["definitions/id/identifier"]
          dedupe: true
          onConflict: "lastWriteWins"
    """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of("definitions/id/identifier", "D-1", "definitions/name", "Alpha"),
            Map.of("definitions/id/identifier", "D-1", "definitions/name", "Beta"));

    var out = TestSupport.first(f2p.convertAll(rows, ImmutableProductRoot.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        om,
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
        TestSupport.cfgFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      lists:
        - path: "definitions"
          keyPaths: ["definitions/id/identifier"]
          dedupe: true
          onConflict: "error"
    """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of("definitions/id/identifier", "D-1", "definitions/name", "Alpha"),
            Map.of("definitions/id/identifier", "D-1", "definitions/name", "Beta"));

    assertThatThrownBy(() -> f2p.convertAll(rows, JsonNode.class, cfg))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void test13_unknown_paths_ignored() {
    MappingConfig cfg =
        TestSupport.cfgFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      lists: []
    """);

    List<Map<String, ?>> rows = List.of(Map.of("metadata/name", "Alpha", "unknown/field", "noise"));

    var out = TestSupport.first(f2p.convertAll(rows, ImmutableProductRoot.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        om,
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
        TestSupport.cfgFromYaml(
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
        TestSupport.cfgFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      nullPolicy: { blanksAsNulls: true }
      lists: []
    """);

    List<Map<String, ?>> rows = List.of(Map.of("metadata/description", ""));

    JsonNode out = TestSupport.first(f2p.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        om, """
      { "metadata": { "description": null } }
    """, out);
  }

  @Test
  void test16_streaming_many_roots_full_shape() {
    MappingConfig cfg =
        TestSupport.cfgFromYaml(
            """
      separator: "/"
      allowSparseRows: false
      rootKeys: ["referencedProductId/identifier"]
      lists:
        - path: "definitions"
          keyPaths: ["definitions/id/identifier"]
    """);

    List<Map<String, ?>> rows =
        List.of(
            Map.of("referencedProductId/identifier", "P-1", "definitions/id/identifier", "D-1"),
            Map.of("referencedProductId/identifier", "P-1", "definitions/id/identifier", "D-2"),
            Map.of("referencedProductId/identifier", "P-2", "definitions/id/identifier", "D-1"),
            Map.of("referencedProductId/identifier", "P-2", "definitions/id/identifier", "D-2"),
            Map.of("referencedProductId/identifier", "P-3", "definitions/id/identifier", "D-1"),
            Map.of("referencedProductId/identifier", "P-3", "definitions/id/identifier", "D-2"));

    var out = f2p.stream(rows.iterator(), ImmutableProductRoot.class, cfg).toList();

    PojoJsonAssert.assertPojoJsonEquals(
        om,
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
      TestSupport.cfgFromYaml(
        """
            separator: "/"
            allowSparseRows: false
            rootKeys: ["referencedProductId/identifier"]
            lists:
              - path: "definitions"
                keyPaths: ["definitions/id/identifier"]
              - path: "definitions/tracker/tasks"
                keyPaths: ["definitions/tracker/tasks/taskDate"]
              - path: "definitions/tracker/tasks/comments"
                keyPaths: ["definitions/tracker/tasks/comments/loggedAt"]
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

    var out = TestSupport.first(f2p.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
      om,
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
      TestSupport.cfgFromYaml(
        """
            separator: "/"
            allowSparseRows: false
            rootKeys: ["referencedProductId/identifier"]
            lists:
              - path: "definitions"
                keyPaths: ["definitions/id/identifier"]
              - path: "definitions/tracker/tasks"
                keyPaths: ["definitions/tracker/tasks/taskDate"]
              - path: "definitions/tracker/tasks/comments"
                keyPaths: ["definitions/tracker/tasks/comments/loggedAt"]
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
          "definitions/tracker/tasks/comments/text", "First comment"
        ),
        // D-1: Second task with different date
        Map.of(
          "referencedProductId/identifier", "P-1",
          "definitions/id/identifier", "D-1",
          "definitions/tracker/tasks/taskDate", "2025-01-02",
          "definitions/tracker/tasks/isUser", false,
          "definitions/tracker/tasks/gracePeriod", 30
        ),
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
        )
      );

    var out = TestSupport.first(f2p.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
      om,
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
      TestSupport.cfgFromYaml(
        """
            separator: "/"
            allowSparseRows: false
            lists:
              - path: "definitions"
                keyPaths: ["definitions/id/identifier"]
              - path: "definitions/modules"
                keyPaths: ["definitions/modules/name"]
              - path: "definitions/modules/components"
                keyPaths: ["definitions/modules/components/id"]
              - path: "definitions/modules/components/features"
                keyPaths: ["definitions/modules/components/features/name"]
          """);

    List<Map<String, ?>> rows =
      List.of(
        // Complete hierarchy: definition -> module -> component -> feature
        Map.of(
          "definitions/id/identifier", "D-1",
          "definitions/modules/name", "ModuleA",
          "definitions/modules/components/id", "C-1",
          "definitions/modules/components/features/name", "Feature1",
          "definitions/modules/components/features/enabled", true
        ),
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
        )
      );

    var out = TestSupport.first(f2p.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
      om,
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
}
