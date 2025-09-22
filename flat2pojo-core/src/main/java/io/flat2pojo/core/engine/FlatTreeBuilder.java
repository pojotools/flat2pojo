package io.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import io.flat2pojo.core.config.MappingConfig;
import io.flat2pojo.core.paths.PathUtil;
import java.util.*;

public final class FlatTreeBuilder {
  private final ObjectMapper om;
  private final MappingConfig cfg;

  public FlatTreeBuilder(ObjectMapper om, MappingConfig cfg) {
    this.om = om; this.cfg = cfg;
  }

  public ObjectNode buildTreeForRow(Map<String, ?> row) {
    ObjectNode root = om.createObjectNode();
    // 1) Expand basic paths to ObjectNodes
    for (var e : row.entrySet()) {
      String key = e.getKey();
      Object raw = e.getValue();
      // optional blank→null
      if (raw instanceof String s && cfg.nullPolicy().blanksAsNulls() && s.isBlank()) {
        raw = null;
      }

      List<String> segs = PathUtil.split(key, cfg.separator());
      ObjectNode cur = root;
      for (int i = 0; i < segs.size() - 1; i++) {
        String s = segs.get(i);
        JsonNode n = cur.get(s);

        final ObjectNode nextObj;
        if (n instanceof ObjectNode existing) {
          nextObj = existing;
        } else {
          ObjectNode created = om.createObjectNode();
          cur.set(s, created);
          nextObj = created;
        }
        cur = nextObj;
      }
      String last = segs.getLast();

      // primitive split rule?
      java.util.Optional<MappingConfig.PrimitiveSplitRule> split =
        cfg.primitives().stream().filter(p -> p.path().equals(key)).findFirst();

      com.fasterxml.jackson.databind.JsonNode valueNode;

      if (split.isPresent() && raw instanceof String str) {
        // Split string into an array; honor trim + blanksAsNulls
        String delimiter = java.util.regex.Pattern.quote(split.get().delimiter());
        boolean trim = split.get().trim();

        com.fasterxml.jackson.databind.node.ArrayNode arr = om.createArrayNode();
        for (String part : str.split(delimiter, -1)) { // -1 keeps empty trailing parts
          String val = trim ? part.trim() : part;
          if (blanksAsNulls() && val.isEmpty()) {
            arr.add(com.fasterxml.jackson.databind.node.NullNode.getInstance());
          } else {
            arr.add(com.fasterxml.jackson.databind.node.TextNode.valueOf(val));
          }
        }
        valueNode = arr;
      } else {
        // Plain leaf: collapse "" -> null if policy enabled
        valueNode = leafNodeFor(raw);
      }

      cur.set(last, valueNode);
    }

    return root;
  }

  // at class level (private helpers)
  private boolean blanksAsNulls() {
    return cfg.nullPolicy() != null && cfg.nullPolicy().blanksAsNulls();
  }

  private com.fasterxml.jackson.databind.JsonNode leafNodeFor(Object raw) {
    if (raw == null) return com.fasterxml.jackson.databind.node.NullNode.getInstance();
    if (raw instanceof String s) {
      String v = s;
      if (blanksAsNulls() && v.trim().isEmpty()) {
        return com.fasterxml.jackson.databind.node.NullNode.getInstance();
      }
      return com.fasterxml.jackson.databind.node.TextNode.valueOf(v);
    }
    // Numbers/booleans/etc.
    return om.valueToTree(raw);
  }

}
