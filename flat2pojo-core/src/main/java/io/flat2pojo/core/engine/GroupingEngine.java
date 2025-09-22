package io.flat2pojo.core.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import io.flat2pojo.core.config.MappingConfig;
import io.flat2pojo.core.paths.PathUtil;
import java.util.*;

/** Manages list/grouping state while building the JSON tree. */
public final class GroupingEngine {
    private final ObjectMapper om;
    private final MappingConfig cfg;

    // Side-car state for arrays encountered during building
    private final IdentityHashMap<ArrayNode, ArrayBucket> buckets = new IdentityHashMap<>();
    private final IdentityHashMap<ArrayNode, List<Comparator<ObjectNode>>> comparators = new IdentityHashMap<>();

    public GroupingEngine(ObjectMapper om, MappingConfig cfg) {
        this.om = om; this.cfg = cfg;
    }

    /**
     * Upsert an element inside a list located by a RELATIVE path from the given base object.
     * Example: base=definitionElement, relativeListPath="tracker/tasks"
     */
    public ObjectNode upsertListElementRelative(ObjectNode base,
                                                String relativeListPath,
                                                Map<String, JsonNode> rowValues,
                                                MappingConfig.ListRule rule) {
        // Walk to the parent object of the array, using RELATIVE path segments
        ObjectNode cursor = base;
        String[] segs = relativeListPath.split(java.util.regex.Pattern.quote(cfg.separator()));
        for (int i = 0; i < segs.length - 1; i++) {
            cursor = ensureObject(cursor, segs[i]);
        }
        String arrayField = segs[segs.length - 1];
        ArrayNode arr = cursor.withArray(arrayField);

        // Per-array state
        ArrayBucket bucket = buckets.computeIfAbsent(arr, k -> new ArrayBucket());
        comparators.computeIfAbsent(arr, k -> buildComparators(rule));

        // Composite key (keyPaths are ABSOLUTE)
        List<Object> keyVals = new ArrayList<>();
        for (String keyPath : rule.keyPaths()) {
            JsonNode v = rowValues.get(keyPath);
            keyVals.add(v == null ? NullNode.getInstance() : v);
        }
        CompositeKey key = new CompositeKey(keyVals);

        ObjectNode candidate = om.createObjectNode();
        return bucket.upsert(key, candidate, rule.onConflict());
    }

    /** Must be called after all rows are processed — materializes arrays with ordering. */
    public void finalizeArrays(ObjectNode node) {
        Deque<JsonNode> stack = new ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty()) {
            JsonNode cur = stack.pop();
            if (cur instanceof ObjectNode on) {
                Iterator<String> f = on.fieldNames();
                List<String> names = new ArrayList<>();
                f.forEachRemaining(names::add);
                for (String n : names) {
                    JsonNode v = on.get(n);
                    if (v instanceof ObjectNode || v instanceof ArrayNode) stack.push(v);
                }
            } else if (cur instanceof ArrayNode an) {
                ArrayBucket bucket = buckets.get(an);
                if (bucket != null) {
                    List<Comparator<ObjectNode>> comps = comparators.getOrDefault(an, List.of());
                    an.removeAll();
                    for (ObjectNode e : bucket.ordered(comps)) an.add(e);
                }
                for (JsonNode child : an) stack.push(child);
            }
        }
    }

    // Build comparators relative to the list element (strip rule.path prefix if present)
    private List<Comparator<ObjectNode>> buildComparators(MappingConfig.ListRule rule) {
        String sep = cfg.separator();
        final String prefix = rule.path() + sep;

        List<Comparator<ObjectNode>> comps = new ArrayList<>();
        for (var ob : rule.orderBy()) {
            final String raw = ob.path();
            final String rel = raw.startsWith(prefix) ? raw.substring(prefix.length()) : raw;
            final boolean asc = ob.direction() == MappingConfig.Direction.asc;
            final boolean nullsFirst = ob.nulls() == MappingConfig.Nulls.first;

            comps.add((a,b) -> {
                JsonNode va = find(a, rel, sep);
                JsonNode vb = find(b, rel, sep);
                boolean na = (va == null || va.isNull());
                boolean nb = (vb == null || vb.isNull());
                if (na != nb) return nullsFirst ? (na ? -1 : 1) : (na ? 1 : -1);
                if (na && nb) return 0;
                int cmp = compare(va, vb);
                return asc ? cmp : -cmp;
            });
        }
        return comps;
    }

    private static int compare(JsonNode a, JsonNode b) {
        if (a.isNumber() && b.isNumber()) return Double.compare(a.asDouble(), b.asDouble());
        return a.asText().compareTo(b.asText());
    }

    private static JsonNode find(ObjectNode base, String relPath, String sep) {
        if (relPath.isEmpty()) return base;
        String[] segs = relPath.split(java.util.regex.Pattern.quote(sep));
        JsonNode cur = base;
        for (String s : segs) {
            if (!(cur instanceof ObjectNode co)) return NullNode.getInstance();
            cur = co.get(s);
            if (cur == null) return NullNode.getInstance();
        }
        return cur;
    }

    private static ObjectNode ensureObject(ObjectNode parent, String field) {
        JsonNode n = parent.get(field);
        if (n instanceof ObjectNode existing) return existing;
        ObjectNode created = parent.objectNode();
        parent.set(field, created);
        return created;
    }
}
