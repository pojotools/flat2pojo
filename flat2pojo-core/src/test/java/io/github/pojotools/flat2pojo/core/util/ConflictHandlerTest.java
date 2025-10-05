package io.github.pojotools.flat2pojo.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.flat2pojo.core.config.MappingConfig.ConflictPolicy;
import io.github.pojotools.flat2pojo.spi.Reporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConflictHandlerTest {

  private ObjectMapper om;
  private TestReporter reporter;

  @BeforeEach
  void setUp() {
    om = new ObjectMapper();
    reporter = new TestReporter();
  }

  private static class TestReporter implements Reporter {
    final List<String> warnings = new ArrayList<>();

    @Override
    public void warn(String message) {
      warnings.add(message);
    }
  }

  private ConflictContext context(ConflictPolicy policy, String path, Reporter reporter) {
    return new ConflictContext(policy, path, reporter);
  }

  @Test
  void writeScalarWithPolicy_whenNoExistingValue_setsValue() {
    ObjectNode target = om.createObjectNode();

    ConflictHandler.writeScalarWithPolicy(
        target, "name", om.getNodeFactory().textNode("Alice"),
        context(ConflictPolicy.error, "path/name", null));

    assertThat(target.get("name").asText()).isEqualTo("Alice");
  }

  @Test
  void writeScalarWithPolicy_whenExistingIsNull_setsValue() {
    ObjectNode target = om.createObjectNode();
    target.putNull("name");

    ConflictHandler.writeScalarWithPolicy(
        target, "name", om.getNodeFactory().textNode("Alice"),
        context(ConflictPolicy.error, "path/name", null));

    assertThat(target.get("name").asText()).isEqualTo("Alice");
  }

  @Test
  void writeScalarWithPolicy_withErrorPolicy_whenConflict_throwsException() {
    ObjectNode target = om.createObjectNode();
    target.put("name", "Alice");

    assertThatThrownBy(() -> ConflictHandler.writeScalarWithPolicy(
        target, "name", om.getNodeFactory().textNode("Bob"),
        context(ConflictPolicy.error, "path/name", reporter)))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Conflict at 'path/name'")
        .hasMessageContaining("Alice")
        .hasMessageContaining("Bob");

    assertThat(reporter.warnings).hasSize(1);
    assertThat(reporter.warnings.getFirst()).contains("Conflict at 'path/name'");
  }

  @Test
  void writeScalarWithPolicy_withErrorPolicy_whenNoConflict_setsValue() {
    ObjectNode target = om.createObjectNode();
    target.put("name", "Alice");

    ConflictHandler.writeScalarWithPolicy(
        target, "name", om.getNodeFactory().textNode("Alice"),
        context(ConflictPolicy.error, "path/name", null));

    assertThat(target.get("name").asText()).isEqualTo("Alice");
  }

  @Test
  void writeScalarWithPolicy_withFirstWriteWinsPolicy_keepsExistingValue() {
    ObjectNode target = om.createObjectNode();
    target.put("name", "Alice");

    ConflictHandler.writeScalarWithPolicy(
        target, "name", om.getNodeFactory().textNode("Bob"),
        context(ConflictPolicy.firstWriteWins, "path/name", reporter));

    assertThat(target.get("name").asText()).isEqualTo("Alice");
    assertThat(reporter.warnings).hasSize(1);
    assertThat(reporter.warnings.getFirst()).contains("firstWriteWins");
  }

  @Test
  void writeScalarWithPolicy_withFirstWriteWinsPolicy_whenNoConflict_doesNotWarn() {
    ObjectNode target = om.createObjectNode();
    target.put("name", "Alice");

    ConflictHandler.writeScalarWithPolicy(
        target, "name", om.getNodeFactory().textNode("Alice"),
        context(ConflictPolicy.firstWriteWins, "path/name", reporter));

    assertThat(target.get("name").asText()).isEqualTo("Alice");
    assertThat(reporter.warnings).isEmpty();
  }

  @Test
  void writeScalarWithPolicy_withLastWriteWinsPolicy_replacesExistingValue() {
    ObjectNode target = om.createObjectNode();
    target.put("name", "Alice");

    ConflictHandler.writeScalarWithPolicy(
        target, "name", om.getNodeFactory().textNode("Bob"),
        context(ConflictPolicy.lastWriteWins, "path/name", reporter));

    assertThat(target.get("name").asText()).isEqualTo("Bob");
    assertThat(reporter.warnings).hasSize(1);
    assertThat(reporter.warnings.getFirst()).contains("lastWriteWins");
  }

  @Test
  void writeScalarWithPolicy_withMergePolicy_whenBothAreObjects_performsDeepMerge() {
    ObjectNode target = om.createObjectNode();
    ObjectNode existingObject = om.createObjectNode();
    existingObject.put("field1", "value1");
    target.set("nested", existingObject);

    ObjectNode incomingObject = om.createObjectNode();
    incomingObject.put("field2", "value2");

    ConflictHandler.writeScalarWithPolicy(
        target, "nested", incomingObject,
        context(ConflictPolicy.merge, "path/nested", reporter));

    ObjectNode result = (ObjectNode) target.get("nested");
    assertThat(result.get("field1").asText()).isEqualTo("value1");
    assertThat(result.get("field2").asText()).isEqualTo("value2");
  }

  @Test
  void writeScalarWithPolicy_withMergePolicy_whenNotObjects_fallsBackToLastWriteWins() {
    ObjectNode target = om.createObjectNode();
    target.put("name", "Alice");

    ConflictHandler.writeScalarWithPolicy(
        target, "name", om.getNodeFactory().textNode("Bob"),
        context(ConflictPolicy.merge, "path/name", reporter));

    assertThat(target.get("name").asText()).isEqualTo("Bob");
    assertThat(reporter.warnings).hasSize(1);
    assertThat(reporter.warnings.getFirst()).contains("Cannot merge non-object values");
  }

  @Test
  void writeScalarWithPolicy_withMergePolicy_whenValuesAreEqual_doesNotWarn() {
    ObjectNode target = om.createObjectNode();
    target.put("name", "Alice");

    ConflictHandler.writeScalarWithPolicy(
        target, "name", om.getNodeFactory().textNode("Alice"),
        context(ConflictPolicy.merge, "path/name", reporter));

    assertThat(target.get("name").asText()).isEqualTo("Alice");
    assertThat(reporter.warnings).isEmpty();
  }

  @Test
  void deepMerge_mergesSimpleFields() {
    ObjectNode target = om.createObjectNode();
    target.put("field1", "value1");

    ObjectNode source = om.createObjectNode();
    source.put("field2", "value2");

    ConflictHandler.deepMerge(target, source);

    assertThat(target.get("field1").asText()).isEqualTo("value1");
    assertThat(target.get("field2").asText()).isEqualTo("value2");
  }

  @Test
  void deepMerge_overwritesConflictingScalarFields() {
    ObjectNode target = om.createObjectNode();
    target.put("name", "Alice");

    ObjectNode source = om.createObjectNode();
    source.put("name", "Bob");

    ConflictHandler.deepMerge(target, source);

    assertThat(target.get("name").asText()).isEqualTo("Bob");
  }

  @Test
  void deepMerge_recursivelyMergesNestedObjects() {
    ObjectNode target = om.createObjectNode();
    ObjectNode targetNested = om.createObjectNode();
    targetNested.put("field1", "value1");
    target.set("nested", targetNested);

    ObjectNode source = om.createObjectNode();
    ObjectNode sourceNested = om.createObjectNode();
    sourceNested.put("field2", "value2");
    source.set("nested", sourceNested);

    ConflictHandler.deepMerge(target, source);

    ObjectNode resultNested = (ObjectNode) target.get("nested");
    assertThat(resultNested.get("field1").asText()).isEqualTo("value1");
    assertThat(resultNested.get("field2").asText()).isEqualTo("value2");
  }

  @Test
  void deepMerge_handlesMultipleLevelsOfNesting() {
    ObjectNode target = om.createObjectNode();
    ObjectNode level1 = om.createObjectNode();
    ObjectNode level2 = om.createObjectNode();
    level2.put("deep", "original");
    level1.set("level2", level2);
    target.set("level1", level1);

    ObjectNode source = om.createObjectNode();
    ObjectNode sourceLevel1 = om.createObjectNode();
    ObjectNode sourceLevel2 = om.createObjectNode();
    sourceLevel2.put("another", "new");
    sourceLevel1.set("level2", sourceLevel2);
    source.set("level1", sourceLevel1);

    ConflictHandler.deepMerge(target, source);

    ObjectNode result = (ObjectNode) target.get("level1");
    ObjectNode resultLevel2 = (ObjectNode) result.get("level2");
    assertThat(resultLevel2.get("deep").asText()).isEqualTo("original");
    assertThat(resultLevel2.get("another").asText()).isEqualTo("new");
  }

  @Test
  void deepMerge_replacesObjectWithScalar() {
    ObjectNode target = om.createObjectNode();
    ObjectNode nested = om.createObjectNode();
    nested.put("field", "value");
    target.set("item", nested);

    ObjectNode source = om.createObjectNode();
    source.put("item", "scalar");

    ConflictHandler.deepMerge(target, source);

    assertThat(target.get("item").asText()).isEqualTo("scalar");
  }

  @Test
  void deepMerge_replacesScalarWithObject() {
    ObjectNode target = om.createObjectNode();
    target.put("item", "scalar");

    ObjectNode source = om.createObjectNode();
    ObjectNode nested = om.createObjectNode();
    nested.put("field", "value");
    source.set("item", nested);

    ConflictHandler.deepMerge(target, source);

    ObjectNode result = (ObjectNode) target.get("item");
    assertThat(result.get("field").asText()).isEqualTo("value");
  }

  @Test
  void deepMerge_handlesEmptySource() {
    ObjectNode target = om.createObjectNode();
    target.put("field", "value");

    ObjectNode source = om.createObjectNode();

    ConflictHandler.deepMerge(target, source);

    assertThat(target.get("field").asText()).isEqualTo("value");
  }

  @Test
  void deepMerge_handlesEmptyTarget() {
    ObjectNode target = om.createObjectNode();

    ObjectNode source = om.createObjectNode();
    source.put("field", "value");

    ConflictHandler.deepMerge(target, source);

    assertThat(target.get("field").asText()).isEqualTo("value");
  }
}
