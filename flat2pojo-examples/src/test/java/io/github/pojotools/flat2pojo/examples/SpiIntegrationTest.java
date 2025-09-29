package io.github.pojotools.flat2pojo.examples;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pojotools.flat2pojo.core.api.Flat2Pojo;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import io.github.pojotools.flat2pojo.spi.Reporter;
import io.github.pojotools.flat2pojo.spi.ValuePreprocessor;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpiIntegrationTest {
  ObjectMapper om;
  Flat2Pojo f2p;

  @BeforeEach
  void init() {
    om = TestSupport.om();
    f2p = TestSupport.mapper(om);
  }

  @Test
  void test_valuePreprocessor_transforms_input_data() {
    // Create a value preprocessor that converts "YES"/"NO" to boolean
    ValuePreprocessor preprocessor =
        row -> {
          Map<String, Object> processedRow = new HashMap<>(row);
          processedRow.forEach(
              (key, value) -> {
                if ("YES".equals(value)) {
                  processedRow.put(key, true);
                } else if ("NO".equals(value)) {
                  processedRow.put(key, false);
                }
              });
          return processedRow;
        };

    MappingConfig cfg =
        MappingConfig.builder()
            .separator("/")
            .allowSparseRows(false)
            .valuePreprocessor(Optional.of(preprocessor))
            .lists(List.of())
            .build();

    List<Map<String, ?>> rows =
        List.of(
            Map.of(
                "workflow/isTerminated", "YES",
                "workflow/isActive", "NO",
                "metadata/name", "Test"));

    var out = TestSupport.first(f2p.convertAll(rows, JsonNode.class, cfg));

    PojoJsonAssert.assertPojoJsonEquals(
        om,
        """
        {
          "workflow": {
            "isTerminated": true,
            "isActive": false
          },
          "metadata": { "name": "Test" }
        }
        """,
        out);
  }

  @Test
  void test_reporter_captures_warnings() {
    // Create a reporter that captures warnings
    List<String> warnings = new ArrayList<>();
    Reporter reporter = warnings::add;

    MappingConfig cfg =
        MappingConfig.builder()
            .separator("/")
            .allowSparseRows(false)
            .reporter(Optional.of(reporter))
            .lists(
                List.of(
                    new MappingConfig.ListRule(
                        "definitions",
                        List.of("definitions/id/identifier"),
                        List.of(),
                        false,
                        MappingConfig.ConflictPolicy.error),
                    new MappingConfig.ListRule(
                        "definitions/tracker/tasks",
                        List.of("definitions/tracker/tasks/taskDate"),
                        List.of(),
                        false,
                        MappingConfig.ConflictPolicy.error)))
            .build();

    // Row with missing keyPath should trigger warning
    List<Map<String, ?>> rows =
        List.of(
            Map.of(
                "definitions/id/identifier", "D-1",
                "definitions/name", "Definition 1",
                "definitions/tracker/tasks/isUser", false
                // Note: taskDate is missing, should generate warning
                ));

    var out = TestSupport.first(f2p.convertAll(rows, JsonNode.class, cfg));

    // Check that warning was captured
    assertThat(warnings).hasSize(1);
    assertThat(warnings.getFirst()).contains("Skipping list rule 'definitions/tracker/tasks'");
    assertThat(warnings.getFirst())
        .contains("keyPath(s) [definitions/tracker/tasks/taskDate] are missing or null");

    // Check that the result has empty tasks array
    PojoJsonAssert.assertPojoJsonEquals(
        om,
        """
        {
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
  void test_reporter_captures_conflict_warnings() {
    // Create a reporter that captures warnings
    List<String> warnings = new ArrayList<>();
    Reporter reporter = warnings::add;

    MappingConfig cfg =
        MappingConfig.builder()
            .separator("/")
            .allowSparseRows(false)
            .reporter(Optional.of(reporter))
            .lists(
                List.of(
                    new MappingConfig.ListRule(
                        "definitions",
                        List.of("definitions/id/identifier"),
                        List.of(),
                        true,
                        MappingConfig.ConflictPolicy.lastWriteWins)))
            .build();

    // Two rows with same key but different values -> conflict
    List<Map<String, ?>> rows =
        List.of(
            Map.of(
                "definitions/id/identifier", "D-1",
                "definitions/name", "First Name"),
            Map.of(
                "definitions/id/identifier", "D-1",
                "definitions/name", "Second Name"));

    var out = TestSupport.first(f2p.convertAll(rows, JsonNode.class, cfg));

    // Check that conflict warning was captured
    assertThat(warnings).isNotEmpty();
    assertThat(warnings)
        .anyMatch(
            warning ->
                warning.contains("lastWriteWins policy") && warning.contains("definitions/name"));

    // Check that last write wins
    PojoJsonAssert.assertPojoJsonEquals(
        om,
        """
        {
          "definitions": [
            {
              "id": { "identifier": "D-1" },
              "name": "Second Name"
            }
          ]
        }
        """,
        out);
  }

  @Test
  void test_combined_spi_usage() {
    // Both preprocessor and reporter together
    List<String> warnings = new ArrayList<>();
    Reporter reporter = warnings::add;

    ValuePreprocessor preprocessor =
        row -> {
          Map<String, Object> processedRow = new HashMap<>(row);
          // Add a prefix to all string values
          processedRow.forEach(
              (key, value) -> {
                if (value instanceof String str && !str.startsWith("processed:")) {
                  processedRow.put(key, "processed:" + str);
                }
              });
          return processedRow;
        };

    MappingConfig cfg =
        MappingConfig.builder()
            .separator("/")
            .allowSparseRows(false)
            .reporter(Optional.of(reporter))
            .valuePreprocessor(Optional.of(preprocessor))
            .lists(
                List.of(
                    new MappingConfig.ListRule(
                        "items",
                        List.of("items/id"),
                        List.of(),
                        false,
                        MappingConfig.ConflictPolicy.error)))
            .build();

    List<Map<String, ?>> rows =
        List.of(
            Map.of(
                "items/id", "123",
                "items/name", "Test Item",
                "items/category", "electronics"));

    var out = TestSupport.first(f2p.convertAll(rows, JsonNode.class, cfg));

    // Values should be preprocessed
    PojoJsonAssert.assertPojoJsonEquals(
        om,
        """
        {
          "items": [
            {
              "id": "processed:123",
              "name": "processed:Test Item",
              "category": "processed:electronics"
            }
          ]
        }
        """,
        out);

    // No warnings expected for this case
    assertThat(warnings).isEmpty();
  }
}
