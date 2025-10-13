package io.github.pojotools.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Comparator;

/**
 * Comparator for JsonNode values with numeric and text fallback.
 * Single Responsibility: JsonNode comparison logic.
 */
final class JsonNodeComparator implements Comparator<JsonNode> {

  @Override
  public int compare(final JsonNode a, final JsonNode b) {
    if (bothAreNumeric(a, b)) {
      return compareAsNumbers(a, b);
    }
    return compareAsText(a, b);
  }

  private boolean bothAreNumeric(final JsonNode a, final JsonNode b) {
    return a.isNumber() && b.isNumber();
  }

  private int compareAsNumbers(final JsonNode a, final JsonNode b) {
    return Double.compare(a.doubleValue(), b.doubleValue());
  }

  private int compareAsText(final JsonNode a, final JsonNode b) {
    return a.asText().compareTo(b.asText());
  }
}
