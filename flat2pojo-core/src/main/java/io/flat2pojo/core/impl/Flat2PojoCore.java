package io.flat2pojo.core.impl;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import io.flat2pojo.core.api.Flat2Pojo;
import io.flat2pojo.core.api.Flat2PojoException;
import io.flat2pojo.core.config.MappingConfig;
import io.flat2pojo.core.config.MappingConfig.ConflictPolicy;
import io.flat2pojo.core.config.MappingConfigLoader;
import io.flat2pojo.core.engine.FlatTreeBuilder;
import io.flat2pojo.core.engine.GroupingEngine;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public final class Flat2PojoCore implements Flat2Pojo {
    private final ObjectMapper om;

    public Flat2PojoCore(ObjectMapper om) { this.om = om; }

    @Override
    public <T> T convert(Map<String, ?> flatRow, Class<T> type, MappingConfig config) {
        List<T> all = convertAll(List.of(flatRow), type, config);
        return all.isEmpty() ? null : all.getFirst();
    }

    @Override
    public <T> List<T> convertAll(List<? extends Map<String, ?>> rows,
                                  Class<T> type,
                                  MappingConfig cfg) {
        // Validate only that parent list rules appear before child list rules
        io.flat2pojo.core.config.MappingConfigLoader.validateHierarchy(cfg);

        // Group rows by optional root keys (or a single synthetic root)
        java.util.function.Function<Map<String, ?>, String> rootKeyFn = r -> {
            if (cfg.rootKeys().isEmpty()) return "__single";
            StringBuilder sb = new StringBuilder();
            for (String k : cfg.rootKeys()) {
                sb.append(k).append('=').append(r.get(k)).append('|');
            }
            return sb.toString();
        };

        Map<String, List<Map<String, ?>>> buckets = new java.util.LinkedHashMap<>();
        for (Map<String, ?> r : rows) {
            buckets.computeIfAbsent(rootKeyFn.apply(r), k -> new java.util.ArrayList<>()).add(r);
        }

        List<T> out = new java.util.ArrayList<>();

        for (var entry : buckets.entrySet()) {
            // Fresh root per grouped object
            ObjectNode root = om.createObjectNode();
            GroupingEngine ge = new GroupingEngine(om, cfg);
            FlatTreeBuilder tb = new FlatTreeBuilder(om, cfg);

            for (Map<String, ?> row : entry.getValue()) {
                // Build a per-row tiny tree for easy value capture, then flatten
                ObjectNode rowNode = tb.buildTreeForRow(row);
                Map<String, JsonNode> rowValues = flatten(rowNode, "");

                // Cache of list elements upserted for THIS row (keyed by absolute list path)
                Map<String, ObjectNode> rowElementCache = new java.util.LinkedHashMap<>();

                // Process lists in declared order (parent-first)
                for (var rule : cfg.lists()) {
                    final String path = rule.path();
                    final String sep = cfg.separator();

                    // Find nearest list ancestor (declared before this rule)
                    String nearestAncestor = null;
                    for (var parentRule : cfg.lists()) {
                        String parentPath = parentRule.path();
                        if (parentPath.equals(path)) break;
                        if (path.startsWith(parentPath + sep)) nearestAncestor = parentPath;
                    }

                    // Determine base object and relative list path
                    final ObjectNode base;
                    final String relPath;
                    if (nearestAncestor != null) {
                        base = rowElementCache.get(nearestAncestor);
                        if (base == null) {
                            throw new IllegalStateException(
                              "Parent list element for '" + path + "' not initialized for row.");
                        }
                        relPath = path.substring(nearestAncestor.length() + sep.length()); // e.g. "tracker/tasks"
                    } else {
                        base = root;
                        relPath = path; // top-level list is relative to the root
                    }

                    // Upsert the list element at the relative path under the chosen base
                    ObjectNode element =
                      ge.upsertListElementRelative(base, relPath, rowValues, rule);
                    rowElementCache.put(path, element);

                    // === Copy values that belong to THIS list's subtree, excluding any CHILD list subtrees ===
                    final String rulePath = rule.path();
                    final String absPrefix = rulePath + sep;

                    // Precompute absolute child-list prefixes (with trailing sep)
                    java.util.List<String> childListPrefixes = cfg.lists().stream()
                      .map(MappingConfig.ListRule::path)
                      .filter(p -> !p.equals(rulePath) && p.startsWith(rulePath + sep))
                      .map(p -> p + sep)
                      .toList();

                    for (var rv : rowValues.entrySet()) {
                        String p = rv.getKey();

                        // Only values inside this list's subtree
                        if (p.equals(rulePath) || p.startsWith(absPrefix)) {
                            // Exclude anything that lies within any descendant list subtree
                            boolean underChildList = childListPrefixes.stream().anyMatch(childPrefix ->
                              // equals the child list path itself (without trailing sep)
                              p.equals(childPrefix.substring(0, childPrefix.length() - sep.length()))
                                // or under that child list subtree
                                || p.startsWith(childPrefix)
                            );
                            if (underChildList) continue;

                            String suffix = p.equals(rulePath) ? "" : p.substring(absPrefix.length());
                            writeIntoWithPolicy(element, suffix, rv.getValue(), sep, rule.onConflict());
                        }
                    }
                }

                // Write all non-list paths onto the root node
                for (var rv : rowValues.entrySet()) {
                    String p = rv.getKey();
                    boolean isListPath = cfg.lists().stream()
                      .anyMatch(lr -> p.equals(lr.path()) || p.startsWith(lr.path() + cfg.separator()));
                    if (!isListPath) {
                        writeInto(root, p, rv.getValue(), cfg.separator());
                    }
                }
            }

            // Materialize arrays from buckets and apply ordering
            ge.finalizeArrays(root);

            // Map to requested result type (JsonNode passthrough or POJO via Jackson)
            try {
                final T value;
                if (JsonNode.class.isAssignableFrom(type)) {
                    @SuppressWarnings("unchecked")
                    T cast = (T) root;
                    value = cast;
                } else {
                    value = om.treeToValue(root, type);
                }
                out.add(value);
            } catch (Exception ex) {
                throw new io.flat2pojo.core.api.Flat2PojoException(
                  "Failed to map result to " + type.getName(), ex);
            }
        }

        return out;
    }


    @Override
    public <T> Stream<T> stream(Iterator<? extends Map<String, ?>> rows,
                                Class<T> type, MappingConfig config) {
        List<Map<String, ?>> list = new ArrayList<>();
        rows.forEachRemaining(list::add);
        return convertAll(list, type, config).stream();
    }

    private Map<String, JsonNode> flatten(ObjectNode node, String prefix) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        Iterator<String> fn = node.fieldNames();
        while (fn.hasNext()) {
            String f = fn.next();
            JsonNode v = node.get(f);
            String path = prefix.isEmpty() ? f : prefix + "/" + f;
            if (v.isObject()) out.putAll(flatten((ObjectNode) v, path));
            else out.put(path, v);
        }
        return out;
    }

    private void writeInto(ObjectNode target, String path, JsonNode value, String sep) {
        if (path.isEmpty()) return;
        String[] segs = path.split(java.util.regex.Pattern.quote(sep));
        ObjectNode cur = target;
        for (int i = 0; i < segs.length - 1; i++) {
            JsonNode n = cur.get(segs[i]);

            final ObjectNode nextObj;
            if (n instanceof ObjectNode existing) {
                nextObj = existing;
            } else {
                ObjectNode created = cur.objectNode();
                cur.set(segs[i], created);
                nextObj = created;
            }
            cur = nextObj;
        }
        cur.set(segs[segs.length - 1], value);
    }

    // Deep merge for ObjectNode when policy=merge
    private void deepMerge(ObjectNode target,
                           ObjectNode src) {
        java.util.Iterator<String> it = src.fieldNames();
        while (it.hasNext()) {
            String f = it.next();
            JsonNode sv = src.get(f);
            JsonNode tv = target.get(f);
            if (tv instanceof ObjectNode to
              && sv instanceof ObjectNode so) {
                deepMerge(to, so);
            } else {
                target.set(f, sv);
            }
        }
    }

    /** Write value with the per-rule conflict policy applied at the leaf. */
    private void writeIntoWithPolicy(ObjectNode target,
                                     String path,
                                     JsonNode value,
                                     String sep,
                                     ConflictPolicy policy) {
        if (path.isEmpty()) return;
        String[] segs = path.split(java.util.regex.Pattern.quote(sep));
        ObjectNode cur = target;
        for (int i = 0; i < segs.length - 1; i++) {
            JsonNode n = cur.get(segs[i]);
            final ObjectNode nextObj;
            if (n instanceof ObjectNode existing) {
                nextObj = existing;
            } else {
                ObjectNode created = cur.objectNode();
                cur.set(segs[i], created);
                nextObj = created;
            }
            cur = nextObj;
        }
        String leaf = segs[segs.length - 1];
        JsonNode existing = cur.get(leaf);

        if (existing != null && !existing.isNull()) {
            switch (policy) {
                case error -> {
                    // If both are value nodes and differ -> conflict
                    if (existing.isValueNode() && value.isValueNode() && !existing.equals(value)) {
                        throw new RuntimeException("Conflict at '" + path + "': existing=" + existing + ", incoming=" + value);
                    }
                    // If both objects and differ -> we can also flag; or let merge/overwrite below handle
                    if (existing.isObject() && value.isObject()) {
                        // no error on objects for 'error' policy unless you want a deep compare; we keep lastWriteWins semantics here
                    }
                    // Default for arrays/objects: fall through to overwrite (since element identity is handled by grouping)
                }
                case firstWriteWins -> {
                    // Keep existing; do nothing
                    return;
                }
                case merge -> {
                    if (existing instanceof ObjectNode eo
                      && value instanceof ObjectNode vo) {
                        deepMerge(eo, vo);
                        return;
                    }
                    // For non-objects, fall through to lastWriteWins behavior
                }
                case lastWriteWins -> {
                    // just overwrite below
                }
            }
        }

        cur.set(leaf, value);
    }

}
