package io.flat2pojo.diff;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

/**
 * Identity-aware, path-configurable deep diff for Jackson trees/POJOs.
 *
 * Features:
 * - List normalization via identity keys (grouping) to avoid index/order noise
 * - Ignore fields by exact path, prefix, or simple wildcards
 * - Per-path equivalence rules (tolerances, case-insensitive, truncation, etc.)
 * - Emits stable JSON Pointer paths in results
 *
 * Typical usage:
 *   var cfg = PojoDiff.config()
 *       .list("/definitions", "id/identifier")
 *       .list("/definitions/tracker/tasks", "taskDate")
 *       .ignore("/auditTime")
 *       .ignorePattern("^/definitions/[^/]+/version$")
 *       .equivalent("/definitions/*/tracker/tasks/*/dueDate",
 *           PojoDiff.Equivalences.equalsIgnoringTimeZone());
 *
 *   List<PojoDiff.Entry> diffs = PojoDiff.diff(oldPojo, newPojo, mapper, cfg);
 */
public final class PojoDiff {

    private PojoDiff() {}

    // -------------------------------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------------------------------

    public enum Kind { ADDED, REMOVED, CHANGED }

    public static final class Entry {
        public final String path;      // JSON Pointer
        public final Kind kind;
        public final JsonNode oldValue; // null for ADDED
        public final JsonNode newValue; // null for REMOVED
        public Entry(String path, Kind kind, JsonNode oldValue, JsonNode newValue) {
            this.path = path; this.kind = kind; this.oldValue = oldValue; this.newValue = newValue;
        }
        @Override public String toString() {
            return kind + " @ " + path + " : " +
                    (oldValue==null?"∅":oldValue) + " -> " + (newValue==null?"∅":newValue);
        }
    }

    /** Configure identity-aware list matching, ignore rules, and equivalence rules. */
    public static final class Config {
        private final List<ListRule> listRules = new ArrayList<>();
        private final Set<String> ignoredExact = new HashSet<>();
        private final List<String> ignoredPrefixes = new ArrayList<>();
        private final List<Pattern> ignoredPatterns = new ArrayList<>();
        private final Map<String, BiPredicate<JsonNode, JsonNode>> equivalencesByExact = new HashMap<>();
        private final List<PathPredicateEquivalence> equivalencesByPattern = new ArrayList<>();

        /** Treat the array at {@code arrayPointer} as a map keyed by {@code idFieldOrPointer}. */
        public Config list(String arrayPointer, String idFieldOrPointer) {
            listRules.add(new ListRule(arrayPointer, idFieldOrPointer));
            return this;
        }
        /** Ignore this exact JSON Pointer (e.g., "/auditTime"). */
        public Config ignore(String exactPointer) {
            ignoredExact.add(exactPointer);
            return this;
        }
        /** Ignore any path with this JSON Pointer prefix (e.g., "/metadata"). */
        public Config ignorePrefix(String pointerPrefix) {
            ignoredPrefixes.add(pointerPrefix);
            return this;
        }
        /** Ignore any path matching this regex against the JSON Pointer string. */
        public Config ignorePattern(String regex) {
            ignoredPatterns.add(Pattern.compile(regex));
            return this;
        }
        /** Ignore paths using simple glob with '*' segments (maps to regex). */
        public Config ignoreGlob(String glob) {
            return ignorePattern(globToRegex(glob));
        }
        /** Provide an equivalence rule for an exact path. */
        public Config equivalent(String exactPointer, BiPredicate<JsonNode, JsonNode> eq) {
            equivalencesByExact.put(exactPointer, eq);
            return this;
        }
        /** Provide an equivalence rule for a regex that matches JSON Pointer. */
        public Config equivalentPattern(String regex, BiPredicate<JsonNode, JsonNode> eq) {
            equivalencesByPattern.add(new PathPredicateEquivalence(Pattern.compile(regex), eq));
            return this;
        }
        /** Provide an equivalence rule using a simple glob with '*' segments. */
        public Config equivalentGlob(String glob, BiPredicate<JsonNode, JsonNode> eq) {
            return equivalentPattern(globToRegex(glob), eq);
        }

        // Internal accessors
        List<ListRule> listRules() { return listRules; }
        boolean isIgnored(JsonPointer path) {
            String p = path.toString();
            if (ignoredExact.contains(p)) return true;
            for (String pref : ignoredPrefixes) {
                if (p.equals(pref) || p.startsWith(pref.endsWith("/") ? pref : pref + "/")) return true;
            }
            for (Pattern pattern : ignoredPatterns) {
                if (pattern.matcher(p).matches()) return true;
            }
            return false;
        }
        Optional<BiPredicate<JsonNode, JsonNode>> equivalenceFor(JsonPointer path) {
            String p = path.toString();
            BiPredicate<JsonNode, JsonNode> eq = equivalencesByExact.get(p);
            if (eq != null) return Optional.of(eq);
            for (PathPredicateEquivalence e : equivalencesByPattern) {
                if (e.pattern.matcher(p).matches()) return Optional.of(e.eq);
            }
            return Optional.empty();
        }
    }

    /** Create a new config. */
    public static Config config() { return new Config(); }

    /** Compare two POJOs using the provided mapper and configuration. */
    public static List<Entry> diff(Object leftPojo, Object rightPojo, ObjectMapper mapper, Config cfg) {
        JsonNode left = mapper.valueToTree(leftPojo);
        JsonNode right = mapper.valueToTree(rightPojo);
        return diff(left, right, cfg);
    }

    /** Compare two JsonNodes with configuration. */
    public static List<Entry> diff(JsonNode left, JsonNode right, Config cfg) {
        JsonNode nl = normalize(left, cfg);
        JsonNode nr = normalize(right, cfg);
        List<Entry> out = new ArrayList<>();
        walk(nl, nr, JsonPointer.compile(""), cfg, out);
        return out;
    }

    // -------------------------------------------------------------------------------------------------
    // Normalization (identity-aware lists)
    // -------------------------------------------------------------------------------------------------

    private static final class ListRule {
        final JsonPointer at;     // where the array sits
        final String idSpec;      // field name or nested path like "id/identifier"
        final boolean idIsPointer;
        ListRule(String pointer, String idFieldOrPointer) {
            this.at = JsonPointer.compile(pointer);
            this.idSpec = idFieldOrPointer;
            this.idIsPointer = idFieldOrPointer.indexOf('/') >= 0;
        }
    }

    private static JsonNode normalize(JsonNode root, Config cfg) {
        return normalizeRecurse(root, JsonPointer.compile(""), cfg);
    }

    private static JsonNode normalizeRecurse(JsonNode node, JsonPointer here, Config cfg) {
        if (node == null || node.isNull() || node.isMissingNode()) return MissingNode.getInstance();

        // If there's a list rule at this exact path and the node is an array -> convert to keyed object
        Optional<ListRule> rule = cfg.listRules().stream().filter(r -> here.equals(r.at)).findFirst();
        if (rule.isPresent() && node.isArray()) {
            ObjectNode keyed = keyArray((ArrayNode) node, rule.get(), cfg);
            // Recurse inside the keyed object
            ObjectNode out = JsonNodeFactory.instance.objectNode();
            Iterator<String> fn = keyed.fieldNames();
            while (fn.hasNext()) {
                String k = fn.next();
                JsonNode child = keyed.get(k);
                out.set(k, normalizeRecurse(child, here /* same path; keys are dynamic below */, cfg));
            }
            return wrapKeyed(out); // mark as keyed for walk()
        }

        if (node.isObject()) {
            ObjectNode out = JsonNodeFactory.instance.objectNode();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                JsonPointer childPtr = here.append(segment(e.getKey()));
                out.set(e.getKey(), normalizeRecurse(e.getValue(), childPtr, cfg));
            }
            return out;
        }

        if (node.isArray()) {
            ArrayNode out = JsonNodeFactory.instance.arrayNode();
            for (int i = 0; i < node.size(); i++) {
                JsonPointer childPtr = here.append(segment(i));
                out.add(normalizeRecurse(node.get(i), childPtr, cfg));
            }
            return out;
        }

        // primitives
        return node;
    }

    private static ObjectNode keyArray(ArrayNode arr, ListRule r, Config cfg) {
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        for (JsonNode el : arr) {
            if (!el.isObject()) continue;
            String key = extractId((ObjectNode) el, r);
            if (key == null) key = "<null>";
            out.set(key, el);
        }
        return out;
    }

    private static String extractId(ObjectNode obj, ListRule r) {
        if (r.idIsPointer) {
            JsonPointer p = toPointer(r.idSpec);
            JsonNode n = obj.at(p);
            return (n == null || n.isMissingNode() || n.isNull()) ? null : n.asText();
        } else {
            JsonNode n = obj.get(r.idSpec);
            return (n == null || n.isNull()) ? null : n.asText();
        }
    }

    private static ObjectNode wrapKeyed(ObjectNode keyed) {
        ObjectNode wrapper = JsonNodeFactory.instance.objectNode();
        wrapper.set("__keyed__", keyed);
        return wrapper;
    }

    private static boolean isKeyed(JsonNode n) { return n != null && n.isObject() && n.has("__keyed__"); }
    private static JsonNode unwrapKeyed(JsonNode n) { return isKeyed(n) ? n.get("__keyed__") : n; }

    // -------------------------------------------------------------------------------------------------
    // Diff walk
    // -------------------------------------------------------------------------------------------------

    private static void walk(JsonNode l, JsonNode r, JsonPointer path, Config cfg, List<Entry> out) {
        // Ignore gate
        if (cfg.isIgnored(path)) return;

        // missing/added/removed checks
        boolean lm = (l == null) || l.isMissingNode();
        boolean rm = (r == null) || r.isMissingNode();
        if (lm && !rm) { out.add(new Entry(path.toString(), Kind.ADDED, null, r)); return; }
        if (rm && !lm) { out.add(new Entry(path.toString(), Kind.REMOVED, l, null)); return; }
        if (lm && rm) return;

        // Node type change
        if (l.getNodeType() != r.getNodeType()) {
            out.add(new Entry(path.toString(), Kind.CHANGED, l, r));
            return;
        }

        // Apply per-path equivalence if present (for scalars and same-type nodes)
        Optional<BiPredicate<JsonNode, JsonNode>> eqOpt = cfg.equivalenceFor(path);
        if (eqOpt.isPresent() && isScalarOrTextualTree(l) && isScalarOrTextualTree(r)) {
            if (eqOpt.get().test(l, r)) return;
        }

        switch (l.getNodeType()) {
            case OBJECT -> {
                // If keyed wrapper present, unwrap and compare keys by name
                JsonNode lo = unwrapKeyed(l);
                JsonNode ro = unwrapKeyed(r);

                // If one side keyed and the other not (structural change), mark changed
                if ((lo != l) != (ro != r)) {
                    out.add(new Entry(path.toString(), Kind.CHANGED, l, r));
                    return;
                }

                // Compare fields by union of names
                Set<String> names = new TreeSet<>();
                lo.fieldNames().forEachRemaining(names::add);
                ro.fieldNames().forEachRemaining(names::add);
                for (String name : names) {
                    JsonPointer childPtr = path.append(segment(name));
                    JsonNode lv = lo.get(name);
                    JsonNode rv = ro.get(name);
                    lv = (lv == null) ? MissingNode.getInstance() : lv;
                    rv = (rv == null) ? MissingNode.getInstance() : rv;
                    walk(lv, rv, childPtr, cfg, out);
                }
            }
            case ARRAY -> {
                int max = Math.max(l.size(), r.size());
                for (int i = 0; i < max; i++) {
                    JsonPointer childPtr = path.append(segment(i));
                    JsonNode lv = i < l.size() ? l.get(i) : MissingNode.getInstance();
                    JsonNode rv = i < r.size() ? r.get(i) : MissingNode.getInstance();
                    walk(lv, rv, childPtr, cfg, out);
                }
            }
            default -> {
                if (!nodesEqual(l, r)) {
                    // If a scalar equivalence exists but didn't hit above (because one/both are not scalar), try now.
                    if (eqOpt.isPresent() && eqOpt.get().test(l, r)) return;
                    out.add(new Entry(path.toString(), Kind.CHANGED, l, r));
                }
            }
        }
    }

    private static boolean nodesEqual(JsonNode a, JsonNode b) {
        // Jackson's equals for JsonNode is deep structural for containers and value equality for scalars
        return Objects.equals(a, b);
    }

    private static boolean isScalarOrTextualTree(JsonNode n) {
        return n.isNumber() || n.isTextual() || n.isBoolean() || n.isNull();
    }

    // -------------------------------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------------------------------

    private static JsonPointer segment(String field) {
        return JsonPointer.compile("/" + escape(field));
    }
    private static JsonPointer segment(int index) {
        return JsonPointer.compile("/" + index);
    }
    private static String escape(String raw) {
        return raw.replace("~", "~0").replace("/", "~1");
    }
    private static JsonPointer toPointer(String pathLike) {
        if (pathLike.startsWith("/")) return JsonPointer.compile(pathLike);
        String[] parts = pathLike.split("/");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append('/').append(escape(p));
        return JsonPointer.compile(sb.toString());
    }

    private static String globToRegex(String glob) {
        // Very simple glob: '*' matches one path segment (no '/'), '**' matches greedy across '/'
        StringBuilder sb = new StringBuilder();
        sb.append('^');
        for (int i = 0; i < glob.length(); ) {
            char c = glob.charAt(i);
            if (c == '*') {
                boolean isDouble = (i + 1 < glob.length() && glob.charAt(i + 1) == '*');
                if (isDouble) {
                    sb.append(".*");
                    i += 2;
                } else {
                    sb.append("[^/]*");
                    i++;
                }
            } else {
                if ("\\.[]{}()+-^$|".indexOf(c) >= 0) sb.append('\\');
                sb.append(c);
                i++;
            }
        }
        sb.append('$');
        return sb.toString();
    }

    private record PathPredicateEquivalence(Pattern pattern, BiPredicate<JsonNode, JsonNode> eq) {}

    // -------------------------------------------------------------------------------------------------
    // Built-in equivalences (handy presets)
    // -------------------------------------------------------------------------------------------------
    public static final class Equivalences {
        private Equivalences() {}

        /** Numbers equal within epsilon (works for numeric strings too). */
        public static BiPredicate<JsonNode, JsonNode> numericWithin(double epsilon) {
            return (a, b) -> {
                Optional<BigDecimal> da = toDecimal(a);
                Optional<BigDecimal> db = toDecimal(b);
                if (da.isEmpty() || db.isEmpty()) return false;
                BigDecimal diff = da.get().subtract(db.get()).abs();
                return diff.compareTo(BigDecimal.valueOf(epsilon)) <= 0;
            };
        }

        /** Case-insensitive textual equality (non-text values fallback to Jackson equality). */
        public static BiPredicate<JsonNode, JsonNode> caseInsensitive() {
            return (a, b) -> {
                if (a.isTextual() && b.isTextual()) {
                    return a.asText().equalsIgnoreCase(b.asText());
                }
                return Objects.equals(a, b);
            };
        }

        /** Treat ISO date-times equal when truncated to milliseconds (simple textual heuristic). */
        public static BiPredicate<JsonNode, JsonNode> isoTimeTruncatedToMillis() {
            return (a, b) -> {
                if (a.isTextual() && b.isTextual()) {
                    String ta = truncateIsoMillis(a.asText());
                    String tb = truncateIsoMillis(b.asText());
                    return Objects.equals(ta, tb);
                }
                return Objects.equals(a, b);
            };
        }

        /** Equality on strings ignoring surrounding whitespace. */
        public static BiPredicate<JsonNode, JsonNode> trimmedText() {
            return (a, b) -> {
                if (a.isTextual() && b.isTextual()) {
                    return a.asText().trim().equals(b.asText().trim());
                }
                return Objects.equals(a, b);
            };
        }

        private static Optional<BigDecimal> toDecimal(JsonNode n) {
            try {
                if (n.isNumber()) return Optional.of(n.decimalValue());
                if (n.isTextual()) return Optional.of(new BigDecimal(n.asText().trim()));
                return Optional.empty();
            } catch (Exception e) {
                return Optional.empty();
            }
        }

        private static String truncateIsoMillis(String s) {
            // Very lightweight: find '.' followed by digits before timezone marker and keep 3 digits.
            // Examples: 2025-01-01T10:00:00.123456Z -> 2025-01-01T10:00:00.123Z
            int t = s.indexOf('T');
            if (t < 0) return s;
            int dot = s.indexOf('.', t);
            if (dot < 0) return s;
            // find end of fraction
            int end = dot + 1;
            while (end < s.length() && Character.isDigit(s.charAt(end))) end++;
            String frac = s.substring(dot + 1, end);
            if (frac.length() <= 3) return s; // already <= millis
            String trimmed = s.substring(0, dot + 1) + frac.substring(0, 3) + s.substring(end);
            return trimmed;
        }
    }
}