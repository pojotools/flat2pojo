package io.github.pojotools.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.pojotools.flat2pojo.core.config.MappingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArrayBucketTest {

  private ObjectMapper om;
  private ArrayBucket bucket;

  @BeforeEach
  void setUp() {
    om = new ObjectMapper();
    bucket = new ArrayBucket();
  }

  private CompositeKey key(Object... values) {
    return new CompositeKey(List.of(values));
  }

  private ObjectNode createNode(String field, String value) {
    ObjectNode node = om.createObjectNode();
    node.put(field, value);
    return node;
  }

  @Test
  void upsert_withNewKey_insertsNode() {
    CompositeKey k = key("id1");
    ObjectNode node = createNode("name", "Alice");

    ObjectNode result = bucket.upsert(k, node, MappingConfig.ConflictPolicy.error);

    assertThat(result).isSameAs(node);
    assertThat(bucket.ordered(List.of())).containsExactly(node);
  }

  @Test
  void upsert_withErrorPolicy_whenNoConflict_mergesFields() {
    CompositeKey k = key("id1");
    ObjectNode node1 = createNode("name", "Alice");
    ObjectNode node2 = createNode("age", "30");

    bucket.upsert(k, node1, MappingConfig.ConflictPolicy.error);
    ObjectNode result = bucket.upsert(k, node2, MappingConfig.ConflictPolicy.error);

    assertThat(result).isSameAs(node1);
    assertThat(result.get("name").asText()).isEqualTo("Alice");
    assertThat(result.get("age").asText()).isEqualTo("30");
  }

  @Test
  void upsert_withErrorPolicy_whenConflictExists_throwsException() {
    CompositeKey k = key("id1");
    ObjectNode node1 = createNode("name", "Alice");
    ObjectNode node2 = createNode("name", "Bob");

    bucket.upsert(k, node1, MappingConfig.ConflictPolicy.error);

    assertThatThrownBy(() -> bucket.upsert(k, node2, MappingConfig.ConflictPolicy.error))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Conflict on field 'name'")
        .hasMessageContaining("Alice")
        .hasMessageContaining("Bob");
  }

  @Test
  void upsert_withLastWriteWinsPolicy_overwritesExistingFields() {
    CompositeKey k = key("id1");
    ObjectNode node1 = createNode("name", "Alice");
    ObjectNode node2 = createNode("name", "Bob");

    bucket.upsert(k, node1, MappingConfig.ConflictPolicy.lastWriteWins);
    ObjectNode result = bucket.upsert(k, node2, MappingConfig.ConflictPolicy.lastWriteWins);

    assertThat(result).isSameAs(node1);
    assertThat(result.get("name").asText()).isEqualTo("Bob");
  }

  @Test
  void upsert_withLastWriteWinsPolicy_mergesNewFieldsAndOverwritesExisting() {
    CompositeKey k = key("id1");
    ObjectNode node1 = om.createObjectNode();
    node1.put("name", "Alice");
    node1.put("age", "30");

    ObjectNode node2 = om.createObjectNode();
    node2.put("name", "Bob");
    node2.put("city", "NYC");

    bucket.upsert(k, node1, MappingConfig.ConflictPolicy.lastWriteWins);
    ObjectNode result = bucket.upsert(k, node2, MappingConfig.ConflictPolicy.lastWriteWins);

    assertThat(result.get("name").asText()).isEqualTo("Bob");
    assertThat(result.get("age").asText()).isEqualTo("30");
    assertThat(result.get("city").asText()).isEqualTo("NYC");
  }

  @Test
  void upsert_withFirstWriteWinsPolicy_keepsExistingValues() {
    CompositeKey k = key("id1");
    ObjectNode node1 = createNode("name", "Alice");
    ObjectNode node2 = createNode("name", "Bob");

    bucket.upsert(k, node1, MappingConfig.ConflictPolicy.firstWriteWins);
    ObjectNode result = bucket.upsert(k, node2, MappingConfig.ConflictPolicy.firstWriteWins);

    assertThat(result).isSameAs(node1);
    assertThat(result.get("name").asText()).isEqualTo("Alice");
  }

  @Test
  void upsert_withMergePolicy_mergesOnlyAbsentFields() {
    CompositeKey k = key("id1");
    ObjectNode node1 = om.createObjectNode();
    node1.put("name", "Alice");
    node1.put("age", "30");

    ObjectNode node2 = om.createObjectNode();
    node2.put("name", "Bob");
    node2.put("city", "NYC");

    bucket.upsert(k, node1, MappingConfig.ConflictPolicy.merge);
    ObjectNode result = bucket.upsert(k, node2, MappingConfig.ConflictPolicy.merge);

    assertThat(result.get("name").asText()).isEqualTo("Alice");
    assertThat(result.get("age").asText()).isEqualTo("30");
    assertThat(result.get("city").asText()).isEqualTo("NYC");
  }

  @Test
  void ordered_withNoComparators_returnsInsertionOrder() {
    ObjectNode node1 = createNode("id", "3");
    ObjectNode node2 = createNode("id", "1");
    ObjectNode node3 = createNode("id", "2");

    bucket.upsert(key("k3"), node1, MappingConfig.ConflictPolicy.error);
    bucket.upsert(key("k1"), node2, MappingConfig.ConflictPolicy.error);
    bucket.upsert(key("k2"), node3, MappingConfig.ConflictPolicy.error);

    List<ObjectNode> result = bucket.ordered(List.of());

    assertThat(result).containsExactly(node1, node2, node3);
  }

  @Test
  void ordered_withComparator_sortsProperly() {
    ObjectNode node1 = createNode("priority", "3");
    ObjectNode node2 = createNode("priority", "1");
    ObjectNode node3 = createNode("priority", "2");

    bucket.upsert(key("k1"), node1, MappingConfig.ConflictPolicy.error);
    bucket.upsert(key("k2"), node2, MappingConfig.ConflictPolicy.error);
    bucket.upsert(key("k3"), node3, MappingConfig.ConflictPolicy.error);

    Comparator<ObjectNode> comparator = Comparator.comparing(n -> n.get("priority").asInt());
    List<ObjectNode> result = bucket.ordered(List.of(comparator));

    assertThat(result).containsExactly(node2, node3, node1);
  }

  @Test
  void ordered_withCaching_returnsCachedResultOnSecondCall() {
    ObjectNode node = createNode("id", "1");
    bucket.upsert(key("k1"), node, MappingConfig.ConflictPolicy.error);

    Comparator<ObjectNode> comparator = Comparator.comparing(n -> n.get("id").asText());
    List<ObjectNode> result1 = bucket.ordered(List.of(comparator));
    List<ObjectNode> result2 = bucket.ordered(List.of(comparator));

    assertThat(result1).isSameAs(result2);
  }

  @Test
  void ordered_afterInvalidation_recomputesOrder() {
    ObjectNode node1 = createNode("id", "1");
    ObjectNode node2 = createNode("id", "2");

    bucket.upsert(key("k1"), node1, MappingConfig.ConflictPolicy.error);

    Comparator<ObjectNode> comparator = Comparator.comparing(n -> n.get("id").asText());
    List<ObjectNode> result1 = bucket.ordered(List.of(comparator));

    bucket.upsert(key("k2"), node2, MappingConfig.ConflictPolicy.error);
    List<ObjectNode> result2 = bucket.ordered(List.of(comparator));

    assertThat(result1).hasSize(1);
    assertThat(result2).hasSize(2);
    assertThat(result1).isNotSameAs(result2);
  }

  @Test
  void asArray_createsArrayNodeWithOrderedElements() {
    ObjectNode node1 = createNode("priority", "2");
    ObjectNode node2 = createNode("priority", "1");

    bucket.upsert(key("k1"), node1, MappingConfig.ConflictPolicy.error);
    bucket.upsert(key("k2"), node2, MappingConfig.ConflictPolicy.error);

    Comparator<ObjectNode> comparator = Comparator.comparing(n -> n.get("priority").asInt());
    ArrayNode result = bucket.asArray(om, List.of(comparator));

    assertThat(result).hasSize(2);
    assertThat(result.get(0).get("priority").asInt()).isEqualTo(1);
    assertThat(result.get(1).get("priority").asInt()).isEqualTo(2);
  }

  @Test
  void asArray_withNoComparators_usesInsertionOrder() {
    ObjectNode node1 = createNode("id", "first");
    ObjectNode node2 = createNode("id", "second");

    bucket.upsert(key("k1"), node1, MappingConfig.ConflictPolicy.error);
    bucket.upsert(key("k2"), node2, MappingConfig.ConflictPolicy.error);

    ArrayNode result = bucket.asArray(om, List.of());

    assertThat(result).hasSize(2);
    assertThat(result.get(0).get("id").asText()).isEqualTo("first");
    assertThat(result.get(1).get("id").asText()).isEqualTo("second");
  }
}
