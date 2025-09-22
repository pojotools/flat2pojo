package io.flat2pojo.core.config;

import io.flat2pojo.core.config.MappingConfig.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

@SuppressWarnings("unchecked")
public final class MappingConfigLoader {
    private MappingConfigLoader(){}

    public static MappingConfig fromYaml(String yaml) {
        Map<String,Object> root = new Yaml().load(yaml);
        MappingConfig.Builder b = MappingConfig.builder();
        if (root == null) return b.build();

        b.separator((String) root.getOrDefault("separator", "/"));
        b.allowSparseRows(Boolean.TRUE.equals(root.get("allowSparseRows")));

        List<String> rk = (List<String>) root.get("rootKeys");
        if (rk != null) b.rootKeys(rk);

        List<Map<String,Object>> prims = (List<Map<String,Object>>) root.get("primitives");
        if (prims != null) {
            for (Map<String,Object> p : prims) {
                Map<String,Object> split = (Map<String,Object>) p.get("split");
                if (split != null) {
                    b.addPrimitiveRule(new PrimitiveSplitRule(
                        (String) p.get("path"),
                        (String) split.getOrDefault("delimiter", ","),
                        Boolean.TRUE.equals(split.get("trim"))
                    ));
                }
            }
        }

        List<Map<String,Object>> lists = (List<Map<String,Object>>) root.get("lists");
        if (lists != null) {
            for (Map<String,Object> m : lists) {
                String path = (String) m.get("path");
                List<String> keyPaths = (List<String>) m.getOrDefault("keyPaths", List.of());

                List<OrderBy> order = new ArrayList<>();
                List<Map<String,Object>> obs = (List<Map<String,Object>>) m.get("orderBy");
                if (obs != null) {
                    for (Map<String,Object> o : obs) {
                        order.add(new OrderBy(
                          (String) o.get("path"),
                          Direction.valueOf(
                            ((String) o.getOrDefault("direction","asc"))
                              .toLowerCase(java.util.Locale.ROOT)
                          ),
                          Nulls.valueOf(
                            ((String) o.getOrDefault("nulls","last"))
                              .toLowerCase(java.util.Locale.ROOT)
                          )
                        ));
                    }
                }

                boolean dedupe = !Boolean.FALSE.equals(m.get("dedupe"));
                ConflictPolicy cp = ConflictPolicy.valueOf(((String)m.getOrDefault("onConflict","error")).trim());

                b.addListRule(new ListRule(path, keyPaths, order, dedupe, cp));
            }
        }

        // --- parse nullPolicy ---
        Object npObj = root.get("nullPolicy");
        if (npObj instanceof Map<?,?> npMapRaw) {
            Object bav = npMapRaw.get("blanksAsNulls");
            boolean blanksAsNulls = false;
            if (bav instanceof Boolean bb) {
                blanksAsNulls = bb;
            } else if (bav != null) {
                blanksAsNulls = Boolean.parseBoolean(String.valueOf(bav));
            }
            b.nullPolicy(new MappingConfig.NullPolicy(blanksAsNulls));
        }

        return b.build();
    }

    public static void validateHierarchy(MappingConfig cfg) {
        final String sep = cfg.separator();

        // index list paths and their declaration order
        final Map<String, Integer> order = new HashMap<>();
        final Set<String> listPaths = new HashSet<>();
        for (int i = 0; i < cfg.lists().size(); i++) {
            String p = cfg.lists().get(i).path();
            order.put(p, i);
            listPaths.add(p);
        }

        for (var lr : cfg.lists()) {
            final String path = lr.path();

            // ---------- (A) enforce parent-before-child for nearest list ancestor ----------
            String nearestListAncestor = nearestListAncestor(path, listPaths, sep);
            if (nearestListAncestor != null) {
                if (order.get(nearestListAncestor) > order.get(path)) {
                    throw new ValidationException(
                      "List '" + path + "' must be declared after its parent list '" + nearestListAncestor + "'");
                }
            } else {
                // no list ancestor; top-level list is fine
            }

            // ---------- (B) keyPaths-implied parent lists must exist & precede ----------
            for (String keyPath : lr.keyPaths()) {
                String impliedParent = longestCommonPrefixPath(path, keyPath, sep);
                // If the keyPath shares a non-empty prefix with this list path
                // and that prefix is *strictly above* the list itself, this implies a parent list
                if (!impliedParent.isEmpty() && !impliedParent.equals(path)) {
                    if (!listPaths.contains(impliedParent)) {
                        throw new ValidationException(
                          "Invalid list rule: '" + path + "' missing ancestor '" + impliedParent
                            + "' (implied by keyPath '" + keyPath + "')");
                    }
                    // and it must be declared earlier
                    if (order.get(impliedParent) > order.get(path)) {
                        throw new ValidationException(
                          "List '" + path + "' must be declared after its parent list '" + impliedParent + "'");
                    }
                }
            }
        }
    }

    /** Returns the nearest declared list ancestor (longest prefix that is a list path), or null. */
    private static String nearestListAncestor(String path, Set<String> listPaths, String sep) {
        int idx = path.lastIndexOf(sep);
        while (idx > 0) {
            String prefix = path.substring(0, idx);
            if (listPaths.contains(prefix)) return prefix;
            idx = prefix.lastIndexOf(sep);
        }
        return null;
    }

    /** Longest common prefix of two slash-delimited paths, returned as a path string (may be empty). */
    private static String longestCommonPrefixPath(String a, String b, String sep) {
        String[] as = a.split(java.util.regex.Pattern.quote(sep));
        String[] bs = b.split(java.util.regex.Pattern.quote(sep));
        int n = Math.min(as.length, bs.length);
        int i = 0;
        while (i < n && as[i].equals(bs[i])) i++;
        if (i == 0) return "";
        return String.join(sep, java.util.Arrays.copyOfRange(as, 0, i));
    }
}
