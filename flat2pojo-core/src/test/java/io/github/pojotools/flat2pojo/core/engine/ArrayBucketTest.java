package io.github.pojotools.flat2pojo.core.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    ObjectNode node = om.createObjectNode();

    ObjectNode result = bucket.upsert(k, node);

    assertThat(result).isSameAs(node);
    assertThat(bucket.ordered(List.of())).containsExactly(node);
  }

  @Test
  void upsert_withExistingKey_returnsExistingNode() {
    CompositeKey k = key("id1");
    ObjectNode first = om.createObjectNode();
    first.put("name", "Alice");

    bucket.upsert(k, first);

    ObjectNode second = om.createObjectNode();
    ObjectNode result = bucket.upsert(k, second);

    assertThat(result).isSameAs(first);
    assertThat(result.get("name").asText()).isEqualTo("Alice");
  }

  @Test
  void ordered_withNoComparators_returnsInsertionOrder() {
    ObjectNode node1 = createNode("id", "3");
    ObjectNode node2 = createNode("id", "1");
    ObjectNode node3 = createNode("id", "2");

    bucket.upsert(key("k3"), node1);
    bucket.upsert(key("k1"), node2);
    bucket.upsert(key("k2"), node3);

    List<ObjectNode> result = bucket.ordered(List.of());

    assertThat(result).containsExactly(node1, node2, node3);
  }

  @Test
  void ordered_withComparator_sortsProperly() {
    ObjectNode node1 = createNode("priority", "3");
    ObjectNode node2 = createNode("priority", "1");
    ObjectNode node3 = createNode("priority", "2");

    bucket.upsert(key("k1"), node1);
    bucket.upsert(key("k2"), node2);
    bucket.upsert(key("k3"), node3);

    Comparator<ObjectNode> comparator = Comparator.comparing(n -> n.get("priority").asInt());
    List<ObjectNode> result = bucket.ordered(List.of(comparator));

    assertThat(result).containsExactly(node2, node3, node1);
  }

  @Test
  void ordered_withCaching_returnsCachedResultOnSecondCall() {
    ObjectNode node = createNode("id", "1");
    bucket.upsert(key("k1"), node);

    Comparator<ObjectNode> comparator = Comparator.comparing(n -> n.get("id").asText());
    List<ObjectNode> result1 = bucket.ordered(List.of(comparator));
    List<ObjectNode> result2 = bucket.ordered(List.of(comparator));

    assertThat(result1).isSameAs(result2);
  }

  @Test
  void ordered_afterInvalidation_recomputesOrder() {
    ObjectNode node1 = createNode("id", "1");
    ObjectNode node2 = createNode("id", "2");

    bucket.upsert(key("k1"), node1);

    Comparator<ObjectNode> comparator = Comparator.comparing(n -> n.get("id").asText());
    List<ObjectNode> result1 = bucket.ordered(List.of(comparator));

    bucket.upsert(key("k2"), node2);
    List<ObjectNode> result2 = bucket.ordered(List.of(comparator));

    assertThat(result1).hasSize(1);
    assertThat(result2).hasSize(2);
    assertThat(result1).isNotSameAs(result2);
  }

  @Test
  void asArray_createsArrayNodeWithOrderedElements() {
    ObjectNode node1 = createNode("priority", "2");
    ObjectNode node2 = createNode("priority", "1");

    bucket.upsert(key("k1"), node1);
    bucket.upsert(key("k2"), node2);

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

    bucket.upsert(key("k1"), node1);
    bucket.upsert(key("k2"), node2);

    ArrayNode result = bucket.asArray(om, List.of());

    assertThat(result).hasSize(2);
    assertThat(result.get(0).get("id").asText()).isEqualTo("first");
    assertThat(result.get(1).get("id").asText()).isEqualTo("second");
  }

  @Test
  void upsert_withExistingKey_doesNotInvalidateCache() {
    CompositeKey k = key("id1");
    ObjectNode first = om.createObjectNode();
    first.put("priority", "1");

    bucket.upsert(k, first);

    Comparator<ObjectNode> comparator = Comparator.comparing(n -> n.get("priority").asInt());
    List<ObjectNode> cached1 = bucket.ordered(List.of(comparator));

    ObjectNode second = om.createObjectNode();
    bucket.upsert(k, second);

    List<ObjectNode> cached2 = bucket.ordered(List.of(comparator));

    assertThat(cached1).isSameAs(cached2);
  }
}
